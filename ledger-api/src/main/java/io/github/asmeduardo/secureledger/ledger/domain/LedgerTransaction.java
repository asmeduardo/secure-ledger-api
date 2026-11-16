package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.money.CurrencyMismatchException;
import io.github.asmeduardo.secureledger.shared.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A posted double-entry transaction: at least two entries, in one currency, on distinct accounts,
 * whose signed amounts sum to zero. Once posted it is never changed; a mistake is corrected by
 * posting its {@linkplain #reverse reversal}. The database enforces the same rules (see the
 * migrations of the ledger schema), so they hold even for writes that bypass this class.
 */
@Entity
@Table(name = "ledger_transaction", schema = "ledger")
public class LedgerTransaction {

    public static final int MAX_REFERENCE_LENGTH = 140;
    public static final int MAX_INITIATOR_LENGTH = 100;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "initiated_by", nullable = false, updatable = false, length = MAX_INITIATOR_LENGTH)
    private String initiatedBy;

    @Column(updatable = false, length = MAX_REFERENCE_LENGTH)
    private String reference;

    @Column(name = "reverses_id", updatable = false)
    private UUID reversesId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<Entry> entries = new ArrayList<>();

    protected LedgerTransaction() {}

    private LedgerTransaction(
            UUID id,
            TransactionType type,
            Currency currency,
            String initiatedBy,
            String reference,
            UUID reversesId,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.currency = currency.getCurrencyCode();
        this.initiatedBy = initiatedBy;
        this.reference = reference;
        this.reversesId = reversesId;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    /**
     * Validates the legs, applies them to the given accounts and returns the transaction with one
     * entry per leg. Either every account changes or none does: all balances are checked before any
     * is applied.
     *
     * @param accounts every account named by a leg; customer accounts must be locked by the caller
     * @throws InvalidTransactionException if the legs do not form a balanced transaction
     * @throws InsufficientFundsException if a customer balance would become negative
     */
    public static LedgerTransaction post(
            UUID id,
            TransactionType type,
            List<Leg> legs,
            Map<UUID, LedgerAccount> accounts,
            String initiatedBy,
            String reference,
            Instant now) {
        if (type == TransactionType.REVERSAL) {
            throw new InvalidTransactionException("A reversal is created from the transaction it reverses");
        }
        return post(id, type, legs, accounts, initiatedBy, reference, null, now);
    }

    /**
     * Posts the compensating transaction: the same accounts with opposite amounts. Customer balances
     * are checked again, so a reversal that would overdraw an account is refused.
     */
    public LedgerTransaction reverse(
            UUID reversalId, Map<UUID, LedgerAccount> accounts, String initiatedBy, String reference, Instant now) {
        if (type == TransactionType.REVERSAL) {
            throw new ReversalNotAllowedException(id, "a reversal cannot itself be reversed");
        }
        var opposite = legs().stream()
                .map(leg -> new Leg(leg.accountId(), leg.amount().negate()))
                .toList();
        return post(reversalId, TransactionType.REVERSAL, opposite, accounts, initiatedBy, reference, id, now);
    }

    private static LedgerTransaction post(
            UUID id,
            TransactionType type,
            List<Leg> legs,
            Map<UUID, LedgerAccount> accounts,
            String initiatedBy,
            String reference,
            UUID reversesId,
            Instant now) {
        var currency = requireBalanced(legs);
        requireInitiator(initiatedBy);
        if (reference != null && reference.length() > MAX_REFERENCE_LENGTH) {
            throw new InvalidTransactionException(
                    "The reference has more than " + MAX_REFERENCE_LENGTH + " characters");
        }
        var targets = new ArrayList<LedgerAccount>(legs.size());
        for (var leg : legs) {
            var account = accounts.get(leg.accountId());
            if (account == null) {
                throw new UnknownLedgerAccountException(leg.accountId());
            }
            if (!account.getCurrency().equals(currency)) {
                throw new CurrencyMismatchException(account.getCurrency(), currency);
            }
            if (account.getType() == AccountType.CUSTOMER) {
                account.balanceAfter(leg.amount());
            }
            targets.add(account);
        }
        var transaction = new LedgerTransaction(id, type, currency, initiatedBy, reference, reversesId, now);
        for (int i = 0; i < legs.size(); i++) {
            var leg = legs.get(i);
            var balanceAfter = targets.get(i).apply(leg.amount()).orElse(null);
            transaction.entries.add(new Entry(transaction, leg.accountId(), leg.amount(), balanceAfter, now));
        }
        return transaction;
    }

    private static Currency requireBalanced(List<Leg> legs) {
        if (legs == null || legs.size() < 2) {
            throw new InvalidTransactionException("A transaction needs at least two entries");
        }
        var currency = legs.getFirst().amount().currency();
        var sum = Money.zero(currency);
        var accounts = new HashSet<UUID>();
        for (var leg : legs) {
            if (leg.amount().isZero()) {
                throw new InvalidTransactionException("An entry cannot have a zero amount");
            }
            if (!accounts.add(leg.accountId())) {
                throw new InvalidTransactionException("Account " + leg.accountId() + " appears more than once");
            }
            sum = sum.plus(leg.amount());
        }
        if (!sum.isZero()) {
            throw new InvalidTransactionException("Entries must sum to zero but sum to " + sum);
        }
        return currency;
    }

    private static void requireInitiator(String initiatedBy) {
        if (initiatedBy == null || initiatedBy.isBlank() || initiatedBy.length() > MAX_INITIATOR_LENGTH) {
            throw new InvalidTransactionException(
                    "The initiator must have between 1 and " + MAX_INITIATOR_LENGTH + " characters");
        }
    }

    /** The signed amount of each entry, in posting order. */
    public List<Leg> legs() {
        return entries.stream()
                .map(entry -> new Leg(entry.getAccountId(), entry.amount()))
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public Currency getCurrency() {
        return Currency.getInstance(currency);
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public Optional<String> getReference() {
        return Optional.ofNullable(reference);
    }

    public Optional<UUID> getReversesId() {
        return Optional.ofNullable(reversesId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }
}
