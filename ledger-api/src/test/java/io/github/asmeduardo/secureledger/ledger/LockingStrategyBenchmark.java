package io.github.asmeduardo.secureledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerRows;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Measures the two candidate concurrency controls for debits, on the real schema with its triggers:
 * pessimistic (lock the account row, then check and update) and optimistic (read the row version,
 * then update only if it did not change, retrying otherwise). The row version is PostgreSQL's
 * {@code xmin}, so the schema needs no extra column for the experiment.
 *
 * <p>Not part of the test suite (the name matches neither {@code *Test} nor {@code *IT}). Run it with
 * {@code ./mvnw -pl ledger-api verify -Dit.test=LockingStrategyBenchmark -Dtest=none
 * -Dsurefire.failIfNoSpecifiedTests=false}. The results are recorded in ADR 0004.
 */
@IntegrationTest
class LockingStrategyBenchmark {

    private static final int THREADS = 8;
    private static final int ROUNDS = 3;
    private static final int MAX_ATTEMPTS = 20;
    private static final BigDecimal ONE = new BigDecimal("1.00");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    enum Strategy {
        PESSIMISTIC,
        OPTIMISTIC
    }

    record Outcome(Strategy strategy, String scenario, long millis, int posted, int refused, int retries, int gaveUp) {}

    @Test
    void compareStrategies() throws Exception {
        var results = new ArrayList<Outcome>();
        for (var strategy : Strategy.values()) {
            run(strategy, "hot", 1, "200.00", 50);
            run(strategy, "spread", 64, "1000.00", 100);
        }
        for (var strategy : Strategy.values()) {
            results.add(median(strategy, "hot", 1, "200.00", 50));
            results.add(median(strategy, "spread", 64, "1000.00", 100));
        }
        System.out.println("strategy     scenario  median_ms  posted  refused  retries  gave_up");
        results.forEach(r -> System.out.printf(
                "%-12s %-9s %9d %7d %8d %8d %8d%n",
                r.strategy(), r.scenario(), r.millis(), r.posted(), r.refused(), r.retries(), r.gaveUp()));
    }

    private Outcome median(Strategy strategy, String scenario, int accounts, String funds, int perThread)
            throws Exception {
        var rounds = new Outcome[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            rounds[i] = run(strategy, scenario, accounts, funds, perThread);
        }
        Arrays.sort(rounds, (a, b) -> Long.compare(a.millis(), b.millis()));
        return rounds[ROUNDS / 2];
    }

    /** Every thread withdraws 1.00 at a time from a random account of the scenario. */
    private Outcome run(Strategy strategy, String scenario, int accountCount, String funds, int perThread)
            throws Exception {
        var rows = new LedgerRows(jdbc);
        var accounts = new ArrayList<UUID>();
        for (int i = 0; i < accountCount; i++) {
            transactions.executeWithoutResult(status -> {
                var account = rows.customerAccount("BRL", funds);
                rows.deposit(account, "BRL", funds, funds);
                accounts.add(account);
            });
        }
        var settlement = rows.settlementAccount("BRL");
        var posted = new AtomicInteger();
        var refused = new AtomicInteger();
        var retries = new AtomicInteger();
        var gaveUp = new AtomicInteger();
        var start = new CountDownLatch(1);
        long began;
        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            for (int t = 0; t < THREADS; t++) {
                long seed = t;
                executor.submit(() -> {
                    var random = new SplittableRandom(seed);
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        var account = accounts.get(random.nextInt(accounts.size()));
                        switch (withdraw(strategy, account, settlement, retries)) {
                            case POSTED -> posted.incrementAndGet();
                            case REFUSED -> refused.incrementAndGet();
                            case GAVE_UP -> gaveUp.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            began = System.nanoTime();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.MINUTES)).isTrue();
        }
        long millis = Duration.ofNanos(System.nanoTime() - began).toMillis();
        for (var account : accounts) {
            var stored = jdbc.queryForObject(
                    "select balance from ledger.ledger_account where id = ?", BigDecimal.class, account);
            var sum = jdbc.queryForObject(
                    "select sum(amount) from ledger.entry where account_id = ?", BigDecimal.class, account);
            assertThat(stored).isEqualByComparingTo(sum).isNotNegative();
        }
        return new Outcome(strategy, scenario, millis, posted.get(), refused.get(), retries.get(), gaveUp.get());
    }

    enum Result {
        POSTED,
        REFUSED,
        GAVE_UP
    }

    private Result withdraw(Strategy strategy, UUID account, UUID settlement, AtomicInteger retries) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var result = transactions.execute(status -> {
                var row = strategy == Strategy.PESSIMISTIC
                        ? jdbc.queryForList(
                                "select balance, xmin::text as version from ledger.ledger_account where id = ? for no key update",
                                account)
                        : jdbc.queryForList(
                                "select balance, xmin::text as version from ledger.ledger_account where id = ?",
                                account);
                var balance = (BigDecimal) row.getFirst().get("balance");
                if (balance.compareTo(ONE) < 0) {
                    return Result.REFUSED;
                }
                var after = balance.subtract(ONE);
                int updated = strategy == Strategy.PESSIMISTIC
                        ? jdbc.update("update ledger.ledger_account set balance = ? where id = ?", after, account)
                        : jdbc.update(
                                "update ledger.ledger_account set balance = ? where id = ? and xmin::text = ?",
                                after,
                                account,
                                row.getFirst().get("version"));
                if (updated == 0) {
                    status.setRollbackOnly();
                    return null;
                }
                post(account, settlement, after);
                return Result.POSTED;
            });
            if (result != null) {
                return result;
            }
            retries.incrementAndGet();
        }
        return Result.GAVE_UP;
    }

    private void post(UUID account, UUID settlement, BigDecimal balanceAfter) {
        var transaction = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into ledger.ledger_transaction (id, type, currency, initiated_by, created_at)
                values (?, 'WITHDRAWAL', 'BRL', 'benchmark', ?)
                """, transaction, now);
        List<Object[]> entries = List.of(
                new Object[] {transaction, account, ONE.negate(), balanceAfter, now},
                new Object[] {transaction, settlement, ONE, null, now});
        jdbc.batchUpdate("""
                insert into ledger.entry (transaction_id, account_id, currency, amount, balance_after, created_at)
                values (?, ?, 'BRL', ?, ?, ?)
                """, entries);
    }
}
