package io.github.asmeduardo.secureledger.audit.api;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.idempotency.Idempotency;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentRequest;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Runs a client write idempotently and records its outcome. A success is recorded inside the
 * transaction of the write, so the effect and its evidence are committed together. A replay or a
 * refusal changes nothing and is recorded afterwards in a transaction of its own. Every outcome is
 * also counted in the {@code ledger.operations} metric, tagged by operation and outcome.
 */
@Component
public class AuditedWrites {

    private final Idempotency idempotency;
    private final AuditTrail trail;
    private final MeterRegistry meters;

    AuditedWrites(Idempotency idempotency, AuditTrail trail, MeterRegistry meters) {
        this.idempotency = idempotency;
        this.trail = trail;
        this.meters = meters;
    }

    /**
     * @param details identifiers and amounts to keep in the trail
     * @param resourceOf the id of what the write created, taken from its response
     */
    public <T> IdempotentResult<T> execute(
            IdempotentRequest request,
            Class<T> responseType,
            Map<String, String> details,
            Function<T, UUID> resourceOf,
            Supplier<T> write) {
        try {
            var result = idempotency.execute(request, responseType, () -> {
                var response = write.get();
                trail.record(record(request, AuditOutcome.SUCCEEDED, resourceOf.apply(response), null, details));
                return response;
            });
            if (result.replayed()) {
                trail.record(
                        record(request, AuditOutcome.REPLAYED, resourceOf.apply(result.response()), null, details));
            }
            count(request, result.replayed() ? AuditOutcome.REPLAYED : AuditOutcome.SUCCEEDED);
            return result;
        } catch (BusinessException refused) {
            trail.record(record(request, AuditOutcome.REJECTED, null, refused.code(), details));
            count(request, AuditOutcome.REJECTED);
            throw refused;
        }
    }

    private void count(IdempotentRequest request, AuditOutcome outcome) {
        Counter.builder("ledger.operations")
                .description("Client writes by operation and outcome")
                .tag("operation", request.operation())
                .tag("outcome", outcome.name())
                .register(meters)
                .increment();
    }

    private static AuditRecord record(
            IdempotentRequest request,
            AuditOutcome outcome,
            UUID resourceId,
            String reason,
            Map<String, String> details) {
        return new AuditRecord(
                request.client().value(), request.operation(), outcome, resourceId, request.key(), reason, details);
    }
}
