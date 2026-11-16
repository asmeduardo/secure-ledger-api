package io.github.asmeduardo.secureledger.accounts.web;

import io.github.asmeduardo.secureledger.accounts.application.StatementLineView;
import io.github.asmeduardo.secureledger.accounts.application.StatementService;
import io.github.asmeduardo.secureledger.shared.pagination.CursorPage;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import io.github.asmeduardo.secureledger.shared.web.Problems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{id}")
@Tag(name = "Statements", description = "Entries of an own account")
@ApiResponse(
        responseCode = "404",
        description = "Unknown account or account of another client",
        content = @Content(mediaType = Problems.MEDIA_TYPE))
public class StatementController {

    private final StatementService statements;

    public StatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping("/entries")
    @Operation(
            summary = "Statement of an account, newest entries first",
            description = "Cursor-paginated: pass nextCursor back to read older entries. Entries posted "
                    + "meanwhile never shift or repeat the pages that follow.")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid cursor or limit",
            content = @Content(mediaType = Problems.MEDIA_TYPE))
    public CursorPage<StatementLineView> entries(
            @PathVariable UUID id,
            @Parameter(description = "nextCursor of the previous page; omit for the newest entries")
                    @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal Jwt client) {
        return statements.page(ClientId.of(client), id, cursor, limit);
    }
}
