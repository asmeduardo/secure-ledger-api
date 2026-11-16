package io.github.asmeduardo.secureledger.audit.application;

import io.github.asmeduardo.secureledger.audit.api.AuditOutcome;
import io.github.asmeduardo.secureledger.audit.api.AuditRecord;
import io.github.asmeduardo.secureledger.audit.api.AuditTrail;
import io.github.asmeduardo.secureledger.ledger.api.LedgerIntegrity;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reconciles the ledger and leaves evidence of every run: an audit event, and a warning in the log
 * when something diverges. It only reports; fixing a divergence is a reversal decided by a person.
 */
@Service
public class ReconciliationService {

    static final String OPERATION = "RECONCILIATION";
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final LedgerIntegrity integrity;
    private final AuditTrail trail;
    private final Clock clock;

    ReconciliationService(LedgerIntegrity integrity, AuditTrail trail, Clock clock) {
        this.integrity = integrity;
        this.trail = trail;
        this.clock = clock;
    }

    public ReconciliationReport run(String actor) {
        var report = ReconciliationReport.of(integrity.check(), clock.instant());
        var divergences = report.balanceDivergences().size();
        var unbalanced = report.unbalancedTransactions().size();
        trail.record(new AuditRecord(
                actor,
                OPERATION,
                AuditOutcome.SUCCEEDED,
                null,
                null,
                null,
                Map.of(
                        "balanceDivergences", String.valueOf(divergences),
                        "unbalancedTransactions", String.valueOf(unbalanced))));
        if (report.consistent()) {
            log.atInfo()
                    .addKeyValue("accountsChecked", report.accountsChecked())
                    .addKeyValue("transactionsChecked", report.transactionsChecked())
                    .log("Reconciliation found the ledger consistent");
        } else {
            log.atWarn()
                    .addKeyValue("balanceDivergences", divergences)
                    .addKeyValue("unbalancedTransactions", unbalanced)
                    .log("Reconciliation found divergences in the ledger");
        }
        return report;
    }
}
