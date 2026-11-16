package io.github.asmeduardo.secureledger.ledger.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Checks the ledger against its own invariants, as a second line of defence after the database. */
public interface LedgerIntegrity {

    /**
     * Compares every materialized customer balance with the sum of its entries and looks for
     * transactions that do not have at least two entries summing to zero. Reads one consistent
     * snapshot, so movements posted meanwhile never show up as divergences.
     */
    IntegrityReport check();

    record IntegrityReport(
            long accountsChecked,
            long transactionsChecked,
            List<BalanceDivergence> balanceDivergences,
            List<UnbalancedTransaction> unbalancedTransactions) {

        public boolean consistent() {
            return balanceDivergences.isEmpty() && unbalancedTransactions.isEmpty();
        }
    }

    record BalanceDivergence(
            UUID accountId, String currency, BigDecimal materializedBalance, BigDecimal ledgerBalance) {}

    record UnbalancedTransaction(UUID transactionId, int entryCount, BigDecimal entrySum) {}
}
