package io.github.asmeduardo.secureledger.audit.application;

import io.github.asmeduardo.secureledger.ledger.api.LedgerIntegrity.BalanceDivergence;
import io.github.asmeduardo.secureledger.ledger.api.LedgerIntegrity.IntegrityReport;
import io.github.asmeduardo.secureledger.ledger.api.LedgerIntegrity.UnbalancedTransaction;
import java.time.Instant;
import java.util.List;

public record ReconciliationReport(
        Instant checkedAt,
        boolean consistent,
        long accountsChecked,
        long transactionsChecked,
        List<BalanceDivergence> balanceDivergences,
        List<UnbalancedTransaction> unbalancedTransactions) {

    static ReconciliationReport of(IntegrityReport report, Instant checkedAt) {
        return new ReconciliationReport(
                checkedAt,
                report.consistent(),
                report.accountsChecked(),
                report.transactionsChecked(),
                report.balanceDivergences(),
                report.unbalancedTransactions());
    }
}
