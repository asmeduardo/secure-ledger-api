package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.money.CurrencyMismatchException;
import io.github.asmeduardo.secureledger.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Where entries are posted. A customer account keeps its balance on the row (a materialized sum of
 * its entries, reconciled against them) so that the non-negative rule can be checked under a row
 * lock without summing the whole history. A settlement account keeps no balance: it takes part in
 * every deposit and withdrawal of its currency and would otherwise serialize all of them.
 */
@Entity
@Table(name = "ledger_account", schema = "ledger")
public class LedgerAccount implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AccountType type;

    @Column(precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean persisted;

    protected LedgerAccount() {}

    private LedgerAccount(UUID id, Currency currency, AccountType type, BigDecimal balance, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.currency = Money.currency(currency.getCurrencyCode()).getCurrencyCode();
        this.type = type;
        this.balance = balance;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public static LedgerAccount openCustomerAccount(UUID id, Currency currency, Instant now) {
        return new LedgerAccount(
                id, currency, AccountType.CUSTOMER, Money.zero(currency).amount(), now);
    }

    public static LedgerAccount settlementAccount(UUID id, Currency currency, Instant now) {
        return new LedgerAccount(id, currency, AccountType.SETTLEMENT, null, now);
    }

    /**
     * The balance this customer account would have after the given signed amount (credit positive,
     * debit negative), without changing it.
     *
     * @throws InsufficientFundsException if the balance would become negative
     */
    public Money balanceAfter(Money delta) {
        requireCurrencyOf(delta);
        if (type != AccountType.CUSTOMER) {
            throw new IllegalStateException("A settlement account keeps no balance");
        }
        var after = currentBalance().plus(delta);
        if (after.isNegative()) {
            throw new InsufficientFundsException(id);
        }
        return after;
    }

    /**
     * Applies a signed amount and returns the new balance of a customer account; a settlement account
     * accepts any amount in its currency and returns nothing.
     */
    public Optional<Money> apply(Money delta) {
        requireCurrencyOf(delta);
        if (type == AccountType.SETTLEMENT) {
            return Optional.empty();
        }
        var after = balanceAfter(delta);
        balance = after.amount();
        return Optional.of(after);
    }

    /** The materialized balance of a customer account; empty for a settlement account. */
    public Optional<Money> balance() {
        return type == AccountType.CUSTOMER ? Optional.of(currentBalance()) : Optional.empty();
    }

    private Money currentBalance() {
        return new Money(balance, getCurrency());
    }

    private void requireCurrencyOf(Money amount) {
        if (!amount.currency().equals(getCurrency())) {
            throw new CurrencyMismatchException(getCurrency(), amount.currency());
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    /** The id is assigned by the application, so Spring Data cannot infer novelty from a null id. */
    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    public Currency getCurrency() {
        return Currency.getInstance(currency);
    }

    public AccountType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
