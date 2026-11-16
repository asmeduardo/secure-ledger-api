package io.github.asmeduardo.secureledger.ledger.application;

import io.github.asmeduardo.secureledger.ledger.api.AlreadyReversedException;
import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.ledger.api.PostedEntry;
import io.github.asmeduardo.secureledger.ledger.api.PostedTransaction;
import io.github.asmeduardo.secureledger.ledger.api.TransactionKind;
import io.github.asmeduardo.secureledger.ledger.api.TransactionNotFoundException;
import io.github.asmeduardo.secureledger.ledger.domain.LedgerAccount;
import io.github.asmeduardo.secureledger.ledger.domain.LedgerAccountRepository;
import io.github.asmeduardo.secureledger.ledger.domain.LedgerTransaction;
import io.github.asmeduardo.secureledger.ledger.domain.LedgerTransactionRepository;
import io.github.asmeduardo.secureledger.ledger.domain.Leg;
import io.github.asmeduardo.secureledger.ledger.domain.TransactionType;
import io.github.asmeduardo.secureledger.shared.money.InvalidAmountException;
import io.github.asmeduardo.secureledger.shared.money.Money;
import java.time.Clock;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LedgerService implements Ledger {

    private final LedgerAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final Clock clock;

    LedgerService(LedgerAccountRepository accounts, LedgerTransactionRepository transactions, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void openCustomerAccount(UUID accountId, Currency currency) {
        accounts.save(LedgerAccount.openCustomerAccount(accountId, currency, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Money> balances(Collection<UUID> accountIds) {
        var balances = new HashMap<UUID, Money>();
        accounts.findAllById(accountIds)
                .forEach(account -> account.balance().ifPresent(balance -> balances.put(account.getId(), balance)));
        return balances;
    }

    @Override
    @Transactional
    public PostedTransaction deposit(UUID accountId, Money amount, String initiatedBy, String reference) {
        var settlement = settlementAccountFor(amount);
        return post(
                TransactionType.DEPOSIT,
                List.of(Leg.debit(settlement, amount), Leg.credit(accountId, amount)),
                initiatedBy,
                reference);
    }

    @Override
    @Transactional
    public PostedTransaction withdraw(UUID accountId, Money amount, String initiatedBy, String reference) {
        var settlement = settlementAccountFor(amount);
        return post(
                TransactionType.WITHDRAWAL,
                List.of(Leg.debit(accountId, amount), Leg.credit(settlement, amount)),
                initiatedBy,
                reference);
    }

    @Override
    @Transactional
    public PostedTransaction transfer(
            UUID sourceId, UUID destinationId, Money amount, String initiatedBy, String reference) {
        requirePositive(amount);
        return post(
                TransactionType.TRANSFER,
                List.of(Leg.debit(sourceId, amount), Leg.credit(destinationId, amount)),
                initiatedBy,
                reference);
    }

    @Override
    @Transactional
    public PostedTransaction reverse(UUID transactionId, String initiatedBy, String reference) {
        var original = transactions
                .findWithEntriesById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        var involved = load(original.legs().stream().map(Leg::accountId).toList());
        if (transactions.existsByReversesId(transactionId)) {
            throw new AlreadyReversedException(transactionId);
        }
        var reversal = original.reverse(UUID.randomUUID(), involved, initiatedBy, reference, clock.instant());
        return view(transactions.save(reversal));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PostedTransaction> find(UUID transactionId) {
        return transactions.findWithEntriesById(transactionId).map(LedgerService::view);
    }

    private PostedTransaction post(TransactionType type, List<Leg> legs, String initiatedBy, String reference) {
        var involved = load(legs.stream().map(Leg::accountId).toList());
        var transaction = LedgerTransaction.post(
                UUID.randomUUID(), type, legs, involved, initiatedBy, reference, clock.instant());
        return view(transactions.save(transaction));
    }

    /**
     * Customer accounts are locked, in a fixed order, before their balances are read; the balance
     * check and the update then happen under the lock. Settlement accounts are only read: they keep
     * no balance, so deposits and withdrawals of different accounts never wait for each other.
     */
    private Map<UUID, LedgerAccount> load(List<UUID> ids) {
        var involved = new HashMap<UUID, LedgerAccount>();
        accounts.lockCustomerAccounts(ids).forEach(account -> involved.put(account.getId(), account));
        var others = ids.stream().filter(id -> !involved.containsKey(id)).toList();
        accounts.findAllById(others).forEach(account -> involved.put(account.getId(), account));
        return involved;
    }

    private UUID settlementAccountFor(Money amount) {
        requirePositive(amount);
        return accounts.findSettlementAccount(amount.currency().getCurrencyCode())
                .orElseThrow(() -> new IllegalStateException("No settlement account for " + amount.currency()))
                .getId();
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidAmountException("The amount must be greater than zero");
        }
    }

    private static PostedTransaction view(LedgerTransaction transaction) {
        var entries = transaction.getEntries().stream()
                .map(entry -> new PostedEntry(entry.getAccountId(), entry.amount(), entry.balanceAfter()))
                .toList();
        var moved = entries.stream()
                .map(PostedEntry::amount)
                .filter(Money::isPositive)
                .reduce(Money.zero(transaction.getCurrency()), Money::plus);
        return new PostedTransaction(
                transaction.getId(),
                TransactionKind.valueOf(transaction.getType().name()),
                moved,
                transaction.getInitiatedBy(),
                transaction.getReference(),
                transaction.getReversesId(),
                transaction.getCreatedAt(),
                entries);
    }
}
