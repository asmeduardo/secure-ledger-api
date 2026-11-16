package io.github.asmeduardo.secureledger.shared.idempotency;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs a write at most once per client and idempotency key. A retry with the same request gets the
 * stored response back; the same key with a different request is refused. Only successful results
 * are stored: a request refused by a business rule had no effect, so its key may be used again.
 */
@Component
public class Idempotency {

    private final IdempotencyRecords records;
    private final TransactionOperations transactions;
    private final JsonMapper json;
    private final Clock clock;

    Idempotency(IdempotencyRecords records, TransactionOperations transactions, JsonMapper json, Clock clock) {
        this.records = records;
        this.transactions = transactions;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Claims the key and runs the operation in one transaction, so the key, the effect and anything
     * the operation records with it are committed together.
     */
    public <T> IdempotentResult<T> execute(IdempotentRequest request, Class<T> responseType, Supplier<T> operation) {
        var previous = transactions.execute(status -> replay(request, responseType));
        if (previous.isPresent()) {
            return previous.get();
        }
        try {
            var response = transactions.execute(status -> {
                records.claim(request, clock.instant());
                var result = operation.get();
                records.complete(request, json.writeValueAsString(result));
                return result;
            });
            return new IdempotentResult<>(response, false);
        } catch (DuplicateKeyException race) {
            // A concurrent request with the same key committed first, and this transaction rolled
            // back with everything it did. Answer with the response the winner stored.
            return transactions.execute(status -> replay(request, responseType)).orElseThrow(() -> race);
        }
    }

    private <T> Optional<IdempotentResult<T>> replay(IdempotentRequest request, Class<T> responseType) {
        return records.find(request).map(stored -> {
            if (!stored.fingerprint().equals(request.fingerprint())) {
                throw new IdempotencyKeyReusedException();
            }
            return new IdempotentResult<>(json.readValue(stored.responseBody(), responseType), true);
        });
    }
}
