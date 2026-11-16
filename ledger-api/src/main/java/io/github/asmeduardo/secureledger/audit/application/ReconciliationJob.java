package io.github.asmeduardo.secureledger.audit.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reconciles the ledger every day; the schedule is {@code secure-ledger.reconciliation.cron}. */
@Component
public class ReconciliationJob {

    static final String ACTOR = "system:reconciliation";

    private final ReconciliationService reconciliation;

    ReconciliationJob(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(cron = "${secure-ledger.reconciliation.cron:0 30 3 * * *}", zone = "UTC")
    public void run() {
        reconciliation.run(ACTOR);
    }
}
