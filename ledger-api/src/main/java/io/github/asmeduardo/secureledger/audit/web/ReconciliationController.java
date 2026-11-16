package io.github.asmeduardo.secureledger.audit.web;

import io.github.asmeduardo.secureledger.audit.application.ReconciliationReport;
import io.github.asmeduardo.secureledger.audit.application.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reconciliation", description = "Back office checks of the ledger (scope ledger.audit)")
public class ReconciliationController {

    private final ReconciliationService reconciliation;

    public ReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping("/api/v1/admin/reconciliations")
    @Operation(
            summary = "Reconcile the ledger now",
            description = "Compares every materialized balance with the sum of its entries and lists "
                    + "transactions that do not balance. Reports only; it never changes the ledger.")
    public ReconciliationReport reconcile(@AuthenticationPrincipal Jwt auditor) {
        return reconciliation.run(auditor.getSubject());
    }
}
