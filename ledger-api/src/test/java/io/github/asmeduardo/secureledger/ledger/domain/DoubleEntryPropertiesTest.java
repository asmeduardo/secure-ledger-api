package io.github.asmeduardo.secureledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Property-based checks of the double-entry rules. Each property runs against many randomly
 * generated transactions; every case derives from a printed seed, so a failure can be replayed by
 * running that seed alone.
 */
class DoubleEntryPropertiesTest {

    private static final int CASES = 500;
    private static final long BASE_SEED = 20261116L;
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Test
    void everyBalancedTransactionIsPostedAndMovesEachBalanceByItsLeg() {
        for (long seed = BASE_SEED; seed < BASE_SEED + CASES; seed++) {
            var random = new SplittableRandom(seed);
            var accounts = fundedAccounts(random, 2 + random.nextInt(5), "1000000.00");
            var before = balances(accounts);
            var legs = balancedLegs(random, accounts);

            var transaction = post(legs, accounts);

            var sum = transaction.getEntries().stream()
                    .map(Entry::amount)
                    .reduce(Money.zero(Money.currency("BRL")), Money::plus);
            assertThat(sum.isZero()).as("seed %d: entries sum to zero", seed).isTrue();
            assertThat(transaction.legs()).as("seed %d", seed).containsExactlyElementsOf(legs);
            for (var entry : transaction.getEntries()) {
                var account = accounts.get(entry.getAccountId());
                var expected = before.get(account.getId()).plus(entry.amount());
                assertThat(account.balance()).as("seed %d", seed).contains(expected);
                assertThat(entry.balanceAfter()).as("seed %d", seed).contains(expected);
            }
        }
    }

    @Test
    void anyImbalanceIsRejectedAndChangesNoBalance() {
        for (long seed = BASE_SEED; seed < BASE_SEED + CASES; seed++) {
            var random = new SplittableRandom(seed);
            var accounts = fundedAccounts(random, 2 + random.nextInt(5), "1000000.00");
            var before = balances(accounts);
            var legs = new ArrayList<>(balancedLegs(random, accounts));
            int victim = random.nextInt(legs.size());
            var skew = cents(random, 1, 10_000);
            var changed = legs.get(victim).amount().plus(skew);
            if (changed.isZero()) {
                changed = changed.plus(skew);
            }
            legs.set(victim, new Leg(legs.get(victim).accountId(), changed));

            assertThatThrownBy(() -> post(legs, accounts))
                    .as("seed %d", seed)
                    .isInstanceOf(InvalidTransactionException.class);
            assertThat(balances(accounts)).as("seed %d", seed).isEqualTo(before);
        }
    }

    @Test
    void aReversalUndoesTheEffectOfAnyTransaction() {
        for (long seed = BASE_SEED; seed < BASE_SEED + CASES; seed++) {
            var random = new SplittableRandom(seed);
            var accounts = fundedAccounts(random, 2 + random.nextInt(5), "1000000.00");
            var before = balances(accounts);
            var original = post(balancedLegs(random, accounts), accounts);

            var reversal = original.reverse(UUID.randomUUID(), accounts, "property-test", null, NOW);

            assertThat(balances(accounts)).as("seed %d", seed).isEqualTo(before);
            assertThat(reversal.getReversesId()).as("seed %d", seed).contains(original.getId());
            for (int i = 0; i < original.legs().size(); i++) {
                assertThat(reversal.legs().get(i).amount())
                        .as("seed %d", seed)
                        .isEqualTo(original.legs().get(i).amount().negate());
            }
        }
    }

    @Test
    void noSequenceOfPostingsLeavesACustomerBalanceNegative() {
        for (long seed = BASE_SEED; seed < BASE_SEED + CASES; seed++) {
            var random = new SplittableRandom(seed);
            var accounts = fundedAccounts(random, 3, "50.00");
            for (int step = 0; step < 20; step++) {
                var before = balances(accounts);
                var legs = balancedLegs(random, accounts);

                var failure = catchThrowable(() -> post(legs, accounts));

                if (failure != null) {
                    assertThat(failure)
                            .as("seed %d step %d", seed, step)
                            .isInstanceOf(InsufficientFundsException.class);
                    assertThat(balances(accounts))
                            .as("seed %d step %d", seed, step)
                            .isEqualTo(before);
                }
                assertThat(balances(accounts).values())
                        .as("seed %d step %d", seed, step)
                        .noneMatch(Money::isNegative);
            }
        }
    }

    private static LedgerTransaction post(List<Leg> legs, Map<UUID, LedgerAccount> accounts) {
        return LedgerTransaction.post(
                UUID.randomUUID(), TransactionType.TRANSFER, legs, accounts, "property-test", null, NOW);
    }

    /** Customer accounts that already hold the given amount, credited from a settlement account. */
    private static Map<UUID, LedgerAccount> fundedAccounts(SplittableRandom random, int count, String funds) {
        var brl = Money.currency("BRL");
        var settlement = LedgerAccount.settlementAccount(UUID.randomUUID(), brl, NOW);
        var accounts = new HashMap<UUID, LedgerAccount>();
        for (int i = 0; i < count; i++) {
            var account = LedgerAccount.openCustomerAccount(UUID.randomUUID(), brl, NOW);
            var all = Map.of(settlement.getId(), settlement, account.getId(), account);
            LedgerTransaction.post(
                    UUID.randomUUID(),
                    TransactionType.DEPOSIT,
                    List.of(
                            Leg.debit(settlement.getId(), Money.of(funds, "BRL")),
                            Leg.credit(account.getId(), Money.of(funds, "BRL"))),
                    all,
                    "property-test",
                    null,
                    NOW);
            accounts.put(account.getId(), account);
        }
        return accounts;
    }

    /** Random non-zero amounts for every account, the last one chosen so that they sum to zero. */
    private static List<Leg> balancedLegs(SplittableRandom random, Map<UUID, LedgerAccount> accounts) {
        while (true) {
            var ids = new ArrayList<>(accounts.keySet());
            var legs = new ArrayList<Leg>();
            var sum = Money.zero(Money.currency("BRL"));
            for (int i = 0; i < ids.size() - 1; i++) {
                var amount = cents(random, -9_999, 9_999);
                if (amount.isZero()) {
                    amount = cents(random, 1, 1);
                }
                legs.add(new Leg(ids.get(i), amount));
                sum = sum.plus(amount);
            }
            if (!sum.isZero()) {
                legs.add(new Leg(ids.getLast(), sum.negate()));
                return legs;
            }
        }
    }

    private static Money cents(SplittableRandom random, int min, int max) {
        return new Money(BigDecimal.valueOf(random.nextInt(min, max + 1), 2), Money.currency("BRL"));
    }

    private static Map<UUID, Money> balances(Map<UUID, LedgerAccount> accounts) {
        var balances = new HashMap<UUID, Money>();
        accounts.values()
                .forEach(account ->
                        balances.put(account.getId(), account.balance().orElseThrow()));
        return balances;
    }
}
