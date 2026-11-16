package io.github.asmeduardo.secureledger.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

class IdempotencyTest {

    record Receipt(String id, BigDecimal amount) {}

    private static final IdempotentRequest REQUEST = IdempotentRequest.of(
            new ClientId("wallet-app"), "key-00000001", "DEPOSIT", "account-1", new BigDecimal("10"));
    private static final String STORED = "{\"id\":\"tx-1\",\"amount\":10.00}";

    private final IdempotencyRecords records = mock(IdempotencyRecords.class);
    private final Idempotency idempotency = new Idempotency(
            records,
            TransactionOperations.withoutTransaction(),
            JsonMapper.builder().build(),
            Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC));
    private final AtomicInteger executions = new AtomicInteger();

    @Test
    void returnsTheStoredResponseWithoutRunningTheOperationAgain() {
        when(records.find(REQUEST))
                .thenReturn(Optional.of(new IdempotencyRecords.Stored(REQUEST.fingerprint(), STORED)));

        var result = idempotency.execute(REQUEST, Receipt.class, this::operation);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(new Receipt("tx-1", new BigDecimal("10.00")));
        assertThat(executions).hasValue(0);
        verify(records, never()).claim(any(), any());
    }

    @Test
    void refusesAKeyStoredForAnotherRequest() {
        when(records.find(REQUEST)).thenReturn(Optional.of(new IdempotencyRecords.Stored("0".repeat(64), STORED)));

        assertThatThrownBy(() -> idempotency.execute(REQUEST, Receipt.class, this::operation))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(executions).hasValue(0);
    }

    @Test
    void claimsTheKeyBeforeTheEffectAndStoresTheResponseAfterIt() {
        when(records.find(REQUEST)).thenReturn(Optional.empty());

        var result = idempotency.execute(REQUEST, Receipt.class, this::operation);

        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(new Receipt("tx-new", new BigDecimal("10.00")));
        var order = inOrder(records);
        order.verify(records).claim(REQUEST, Instant.parse("2026-11-16T12:00:00Z"));
        order.verify(records).complete(REQUEST, "{\"id\":\"tx-new\",\"amount\":10.00}");
        assertThat(executions).hasValue(1);
    }

    @Test
    void answersALostRaceWithTheResponseOfTheWinner() {
        when(records.find(REQUEST))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyRecords.Stored(REQUEST.fingerprint(), STORED)));
        doThrow(new DuplicateKeyException("pk_idempotency_record"))
                .when(records)
                .claim(any(), any());

        var result = idempotency.execute(REQUEST, Receipt.class, this::operation);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().id()).isEqualTo("tx-1");
        assertThat(executions).hasValue(0);
    }

    @Test
    void aDuplicateThatIsNotAStoredKeyIsNotHidden() {
        when(records.find(REQUEST)).thenReturn(Optional.empty());
        var duplicate = new DuplicateKeyException("uk_ledger_transaction_reverses");

        assertThatThrownBy(() -> idempotency.execute(REQUEST, Receipt.class, () -> {
                    throw duplicate;
                }))
                .isSameAs(duplicate);
    }

    @Test
    void aFailedOperationStoresNoResponse() {
        when(records.find(REQUEST)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> idempotency.execute(REQUEST, Receipt.class, () -> {
                    throw new IllegalStateException("insufficient funds");
                }))
                .isInstanceOf(IllegalStateException.class);
        verify(records, never()).complete(any(), anyString());
    }

    private Receipt operation() {
        executions.incrementAndGet();
        return new Receipt("tx-new", new BigDecimal("10.00"));
    }
}
