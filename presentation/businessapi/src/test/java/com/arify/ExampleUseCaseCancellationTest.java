package com.arify;

import com.arify.application.adapters.CreateExampleAdapter;
import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.usecases.exampleusecase.ExampleUseCase;
import com.arify.domain.commons.CancellationReason;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleUseCaseCancellationTest {

    @Test
    void preCancelledTokenReturnsClientClosedWithoutCallingInfrastructure() {
        FakeInfrastructure fakeInfrastructure = new FakeInfrastructure();
        ExampleUseCase useCase = new ExampleUseCase(fakeInfrastructure);
        CancellationToken token = CancellationToken.withDefault();
        token.cancel(CancellationReason.CLIENT_DISCONNECTED);

        EasyResult<CreateExampleAdapter> result = useCase.getDataAsync(token, validTrace(), validRequest());

        assertFalse(result.isSuccess());
        assertEquals(499, result.status());
        assertEquals(0, fakeInfrastructure.callCount());
    }

    @Test
    void expiredTokenReturnsTimeoutWithoutCallingInfrastructure() throws InterruptedException {
        FakeInfrastructure fakeInfrastructure = new FakeInfrastructure();
        ExampleUseCase useCase = new ExampleUseCase(fakeInfrastructure);
        CancellationToken token = CancellationToken.withTimeout(Duration.ofMillis(1));

        Thread.sleep(5);
        EasyResult<CreateExampleAdapter> result = useCase.getDataAsync(token, validTrace(), validRequest());

        assertFalse(result.isSuccess());
        assertEquals(408, result.status());
        assertEquals(0, fakeInfrastructure.callCount());
    }

    @Test
    void successfulCallsStillReturnData() {
        FakeInfrastructure fakeInfrastructure = new FakeInfrastructure();
        ExampleUseCase useCase = new ExampleUseCase(fakeInfrastructure);

        EasyResult<CreateExampleAdapter> result = useCase.getDataAsync(
                CancellationToken.withTimeout(Duration.ofSeconds(5)),
                validTrace(),
                validRequest());

        assertTrue(result.isSuccess());
        assertEquals(200, result.status());
        assertEquals("user-title", result.successValue().name());
        assertEquals("email-title", result.successValue().email());
        assertEquals(2, fakeInfrastructure.callCount());
    }

    private TraceIdentifierAdapter validTrace() {
        return new TraceIdentifierAdapter("device-123", "message-123", "channel-123");
    }

    private ExampleRequestAdapter validRequest() {
        return new ExampleRequestAdapter(
                "channel1",
                "message1",
                "device12",
                "12345",
                "account1234");
    }

    private static final class FakeInfrastructure implements IFakeApiInfrastructure {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Optional<FakeApiEntity> getUserAsync(int id, CancellationToken cancellationToken) {
            calls.incrementAndGet();
            return Optional.of(new FakeApiEntity(1, id, "user-title", false));
        }

        @Override
        public Optional<FakeApiEntity> getTitleAsync(int id, CancellationToken cancellationToken) {
            calls.incrementAndGet();
            return Optional.of(new FakeApiEntity(1, id, "email-title", false));
        }

        int callCount() {
            return calls.get();
        }
    }
}
