package io.github.asmeduardo.secureledger.audit.web;

import io.github.asmeduardo.secureledger.audit.application.AuditEventView;
import io.github.asmeduardo.secureledger.audit.application.AuditService;
import io.github.asmeduardo.secureledger.shared.pagination.CursorPage;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import io.github.asmeduardo.secureledger.shared.web.Problems;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Audit", description = "Append-only trail of who requested what, when and with which outcome")
@ApiResponse(responseCode = "400", description = "Invalid cursor", content = @Content(mediaType = Problems.MEDIA_TYPE))
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/audit-events")
    @Operation(summary = "Audit events of the calling client, newest first")
    public CursorPage<AuditEventView> ownEvents(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_LIMIT) int limit,
            @AuthenticationPrincipal Jwt client) {
        return audit.ownEvents(ClientId.of(client), cursor, limit);
    }

    @GetMapping("/api/v1/admin/audit-events")
    @Operation(summary = "Audit events of every client (scope ledger.audit)")
    public CursorPage<AuditEventView> allEvents(
            @RequestParam(required = false) @Size(max = 100) String actor,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_LIMIT) int limit) {
        return audit.allEvents(actor, cursor, limit);
    }
}
