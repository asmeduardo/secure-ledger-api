package io.github.asmeduardo.secureledger.audit.api;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.idempotency.Idempotency;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentRequest;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentResult;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Runs a client write idempotently and records its outcome. A success is recorded inside the
 * transaction of the write, so the effect and its evidence are committed together. A replay or a
 * refusal changes nothing and is recorded afterwards in a transaction of its own.
 */
@Component
public class AuditedWrites {

    private final Idempotency idempotency;
    private final AuditTrail trail;

    AuditedWrites(Idempotency idempotency, AuditTrail trail) {
        this.idempotency = idempotency;
        this.trail = trail;
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
            return result;
        } catch (BusinessException refused) {
            trail.record(record(request, AuditOutcome.REJECTED, null, refused.code(), details));
            throw refused;
        }
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
