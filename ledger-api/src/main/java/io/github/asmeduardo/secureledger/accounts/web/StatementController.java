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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_LIMIT) int limit,
            @AuthenticationPrincipal Jwt client) {
        return statements.page(ClientId.of(client), id, cursor, limit);
    }

    @GetMapping(value = "/statement.csv", produces = "text/csv")
    @Operation(
            summary = "Export the statement of a period as CSV",
            description = "Entries posted in [from, to), oldest first, at most 366 days. RFC 4180 with a "
                    + "header line; text cells that a spreadsheet would run as formulas are prefixed with '.")
    @ApiResponse(
            responseCode = "400",
            description = "Missing or invalid period",
            content = @Content(mediaType = Problems.MEDIA_TYPE))
    public void export(
            @PathVariable UUID id,
            @Parameter(description = "First instant included, ISO-8601 UTC") @RequestParam Instant from,
            @Parameter(description = "First instant excluded, ISO-8601 UTC") @RequestParam Instant to,
            @AuthenticationPrincipal Jwt client,
            HttpServletResponse response)
            throws IOException {
        var export = statements.export(ClientId.of(client), id, from, to);
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename("statement-%s-%s-%s.csv".formatted(id, day(from), day(to)))
                        .build()
                        .toString());
        var csv = new StatementCsv(response.getWriter());
        csv.header();
        export.forEach(csv::line);
        response.getWriter().flush();
    }

    private static LocalDate day(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}
