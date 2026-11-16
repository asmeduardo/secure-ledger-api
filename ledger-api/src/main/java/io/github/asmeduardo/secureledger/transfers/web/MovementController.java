package io.github.asmeduardo.secureledger.transfers.web;

import io.github.asmeduardo.secureledger.shared.security.ClientId;
import io.github.asmeduardo.secureledger.shared.web.Problems;
import io.github.asmeduardo.secureledger.transfers.application.MovementService;
import io.github.asmeduardo.secureledger.transfers.application.TransactionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Movements", description = "Deposits, simulated withdrawals and transfers between own accounts")
@ApiResponse(
        responseCode = "400",
        description = "Invalid request, amount or currency",
        content = @Content(mediaType = Problems.MEDIA_TYPE))
@ApiResponse(
        responseCode = "404",
        description = "Account or transaction unknown, or of another client",
        content = @Content(mediaType = Problems.MEDIA_TYPE))
public class MovementController {

    private final MovementService movements;

    public MovementController(MovementService movements) {
        this.movements = movements;
    }

    @PostMapping("/deposits")
    @Operation(summary = "Deposit into an own account", description = "Credits the account against settlement.")
    @ApiResponse(responseCode = "201", description = "Deposit posted")
    @ApiResponse(
            responseCode = "422",
            description = "Currency of the request differs from the account",
            content = @Content(mediaType = Problems.MEDIA_TYPE))
    public ResponseEntity<TransactionView> deposit(
            @Valid @RequestBody AccountMovementRequest request, @AuthenticationPrincipal Jwt client) {
        return created(
                movements.deposit(ClientId.of(client), request.accountId(), request.money(), request.reference()));
    }

    @PostMapping("/withdrawals")
    @Operation(
            summary = "Withdraw from an own account",
            description = "Simulated payout: debits the account against settlement; no money leaves the system.")
    @ApiResponse(responseCode = "201", description = "Withdrawal posted")
    @ApiResponse(
            responseCode = "422",
            description = "Insufficient funds or currency mismatch",
            content = @Content(mediaType = Problems.MEDIA_TYPE))
    public ResponseEntity<TransactionView> withdraw(
            @Valid @RequestBody AccountMovementRequest request, @AuthenticationPrincipal Jwt client) {
        return created(
                movements.withdraw(ClientId.of(client), request.accountId(), request.money(), request.reference()));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfer between two own accounts in the same currency")
    @ApiResponse(responseCode = "201", description = "Transfer posted")
    @ApiResponse(
            responseCode = "422",
            description = "Insufficient funds, currency mismatch or same source and destination",
            content = @Content(mediaType = Problems.MEDIA_TYPE))
    public ResponseEntity<TransactionView> transfer(
            @Valid @RequestBody TransferRequest request, @AuthenticationPrincipal Jwt client) {
        return created(movements.transfer(
                ClientId.of(client),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.money(),
                request.reference()));
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get a transaction with its entries")
    public TransactionView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt client) {
        return movements.get(ClientId.of(client), id);
    }

    private static ResponseEntity<TransactionView> created(TransactionView transaction) {
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.id()))
                .body(transaction);
    }
}
