package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * One line of a posted transaction. Entries are append-only: the database rejects any update or
 * delete, so the history of an account can be trusted as written.
 */
@Entity
@Table(name = "entry", schema = "ledger")
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Entry() {}

    Entry(LedgerTransaction transaction, UUID accountId, Money amount, Money balanceAfter, Instant now) {
        this.transaction = transaction;
        this.accountId = accountId;
        this.currency = amount.currency().getCurrencyCode();
        this.amount = amount.amount();
        this.balanceAfter = balanceAfter == null ? null : balanceAfter.amount();
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public UUID getAccountId() {
        return accountId;
    }

    /** Signed amount: a credit is positive, a debit negative. */
    public Money amount() {
        return new Money(amount, Currency.getInstance(currency));
    }

    /** Balance of a customer account right after this entry; settlement accounts keep none. */
    public Optional<Money> balanceAfter() {
        return Optional.ofNullable(balanceAfter).map(value -> new Money(value, Currency.getInstance(currency)));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
