package io.github.asmeduardo.secureledger.accounts.web;

import io.github.asmeduardo.secureledger.accounts.application.AccountService;
import io.github.asmeduardo.secureledger.accounts.application.AccountView;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import io.github.asmeduardo.secureledger.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<AccountView> open(
            @Valid @RequestBody OpenAccountRequest request, @AuthenticationPrincipal Jwt client) {
        var account = accounts.open(ClientId.of(client), request.name(), request.currency());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id()))
                .body(account);
    }

    @GetMapping("/{id}")
    public AccountView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt client) {
        return accounts.get(ClientId.of(client), id);
    }

    @GetMapping
    public PageResponse<AccountView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt client) {
        return PageResponse.of(accounts.list(ClientId.of(client), page, size));
    }
}
