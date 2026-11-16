package io.github.asmeduardo.secureledger.accounts.application;

import io.github.asmeduardo.secureledger.accounts.api.AccountNotFoundException;
import io.github.asmeduardo.secureledger.accounts.api.OwnedAccounts;
import io.github.asmeduardo.secureledger.accounts.domain.CustomerAccount;
import io.github.asmeduardo.secureledger.accounts.domain.CustomerAccountRepository;
import io.github.asmeduardo.secureledger.audit.api.AuditedWrites;
import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentRequest;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentResult;
import io.github.asmeduardo.secureledger.shared.money.Money;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements OwnedAccounts {

    private final CustomerAccountRepository accounts;
    private final Ledger ledger;
    private final AuditedWrites writes;
    private final Clock clock;

    public AccountService(CustomerAccountRepository accounts, Ledger ledger, AuditedWrites writes, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.writes = writes;
        this.clock = clock;
    }

    /**
     * Opens an account owned by the client, with a zero balance in the given currency. Idempotent per
     * client and key, and audited. The name is kept out of the audit trail: it is free text.
     */
    public IdempotentResult<AccountView> open(ClientId owner, String key, String name, String currencyCode) {
        var request = IdempotentRequest.of(owner, key, "OPEN_ACCOUNT", name, currencyCode);
        var details = Map.of("currency", currencyCode);
        return writes.execute(request, AccountView.class, details, AccountView::id, () -> {
            var currency = Money.currency(currencyCode);
            var account = accounts.save(new CustomerAccount(UUID.randomUUID(), owner.value(), name, clock.instant()));
            ledger.openCustomerAccount(account.getId(), currency);
            return view(account, Money.zero(currency));
        });
    }

    @Transactional(readOnly = true)
    public AccountView get(ClientId owner, UUID id) {
        var account = owned(owner, id);
        return view(account, balancesOf(List.of(account)).get(id));
    }

    /** The client's accounts, oldest first. Balances are read in one query for the whole page. */
    @Transactional(readOnly = true)
    public Page<AccountView> list(ClientId owner, int page, int size) {
        var oldestFirst = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        var accountsPage = accounts.findByOwnerId(owner.value(), PageRequest.of(page, size, oldestFirst));
        var balances = balancesOf(accountsPage.getContent());
        return accountsPage.map(account -> view(account, balances.get(account.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOwnedBy(ClientId client, UUID accountId) {
        owned(client, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> ownedAmong(ClientId client, Collection<UUID> accountIds) {
        return accounts.findByOwnerIdAndIdIn(client.value(), accountIds).stream()
                .map(CustomerAccount::getId)
                .collect(Collectors.toSet());
    }

    private CustomerAccount owned(ClientId owner, UUID id) {
        return accounts.findByIdAndOwnerId(id, owner.value()).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private Map<UUID, Money> balancesOf(List<CustomerAccount> page) {
        return ledger.balances(page.stream().map(CustomerAccount::getId).toList());
    }

    private static AccountView view(CustomerAccount account, Money balance) {
        if (balance == null) {
            throw new IllegalStateException("Account " + account.getId() + " has no ledger account");
        }
        return new AccountView(
                account.getId(),
                account.getName(),
                balance.currency().getCurrencyCode(),
                balance.amount(),
                account.getCreatedAt());
    }
}
