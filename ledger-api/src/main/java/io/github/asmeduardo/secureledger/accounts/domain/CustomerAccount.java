package io.github.asmeduardo.secureledger.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** An account opened by an API client. Only that client may read it or move its money. */
@Entity
@Table(name = "customer_account", schema = "accounts")
public class CustomerAccount implements Persistable<UUID> {

    public static final int MAX_NAME_LENGTH = 80;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false, length = 100)
    private String ownerId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean persisted;

    protected CustomerAccount() {}

    public CustomerAccount(UUID id, String ownerId, String name, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.name = Objects.requireNonNull(name, "name");
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public boolean isOwnedBy(String clientId) {
        return ownerId.equals(clientId);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
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
}
