package io.github.asmeduardo.secureledger.accounts.web;

import io.github.asmeduardo.secureledger.accounts.application.AccountService;
import io.github.asmeduardo.secureledger.accounts.application.AccountView;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import io.github.asmeduardo.secureledger.shared.web.IdempotentResponses;
import io.github.asmeduardo.secureledger.shared.web.PageResponse;
import io.github.asmeduardo.secureledger.shared.web.Problems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Accounts of the calling client and their balances")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @Operation(summary = "Open an account", description = "The account belongs to the client of the access token.")
    @ApiResponse(responseCode = "201", description = "Account opened with a zero balance")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request or unsupported currency",
            content = @Content(mediaType = Problems.MEDIA_TYPE))
    public ResponseEntity<AccountView> open(
            @Parameter(description = IdempotentResponses.KEY_DESCRIPTION)
                    @RequestHeader(IdempotentResponses.KEY_HEADER)
                    @Pattern(regexp = IdempotentResponses.KEY_PATTERN, message = IdempotentResponses.KEY_MESSAGE)
                    String idempotencyKey,
            @Valid @RequestBody OpenAccountRequest request,
            @AuthenticationPrincipal Jwt client) {
        var result = accounts.open(ClientId.of(client), idempotencyKey, request.name(), request.currency());
        return IdempotentResponses.created(
                URI.create("/api/v1/accounts/" + result.response().id()), result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account with its current balance")
    @ApiResponse(
            responseCode = "404",
            description = "Unknown account or account of another client",
            content = @Content(mediaType = Problems.MEDIA_TYPE))
    public AccountView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt client) {
        return accounts.get(ClientId.of(client), id);
    }

    @GetMapping
    @Operation(summary = "List the client's accounts, oldest first")
    public PageResponse<AccountView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt client) {
        return PageResponse.of(accounts.list(ClientId.of(client), page, size));
    }
}
