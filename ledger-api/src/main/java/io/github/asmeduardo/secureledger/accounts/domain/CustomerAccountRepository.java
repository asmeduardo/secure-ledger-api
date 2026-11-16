package io.github.asmeduardo.secureledger.accounts.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, UUID> {

    /** Looks an account up on behalf of a client: an account of another client is not found. */
    Optional<CustomerAccount> findByIdAndOwnerId(UUID id, String ownerId);

    Page<CustomerAccount> findByOwnerId(String ownerId, Pageable pageable);
}
