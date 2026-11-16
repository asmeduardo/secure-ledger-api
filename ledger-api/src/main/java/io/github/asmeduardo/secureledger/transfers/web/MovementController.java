package io.github.asmeduardo.secureledger.transfers.web;

import io.github.asmeduardo.secureledger.shared.security.ClientId;
import io.github.asmeduardo.secureledger.transfers.application.MovementService;
import io.github.asmeduardo.secureledger.transfers.application.TransactionView;
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
public class MovementController {

    private final MovementService movements;

    public MovementController(MovementService movements) {
        this.movements = movements;
    }

    @PostMapping("/deposits")
    public ResponseEntity<TransactionView> deposit(
            @Valid @RequestBody AccountMovementRequest request, @AuthenticationPrincipal Jwt client) {
        return created(
                movements.deposit(ClientId.of(client), request.accountId(), request.money(), request.reference()));
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<TransactionView> withdraw(
            @Valid @RequestBody AccountMovementRequest request, @AuthenticationPrincipal Jwt client) {
        return created(
                movements.withdraw(ClientId.of(client), request.accountId(), request.money(), request.reference()));
    }

    @PostMapping("/transfers")
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
    public TransactionView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt client) {
        return movements.get(ClientId.of(client), id);
    }

    private static ResponseEntity<TransactionView> created(TransactionView transaction) {
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.id()))
                .body(transaction);
    }
}
