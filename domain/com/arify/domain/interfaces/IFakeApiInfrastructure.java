package com.arify.domain.interfaces;

import com.arify.domain.commons.CancellationToken;
import com.arify.domain.entities.FakeApiEntity;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// Equivalente a IExampleTitleQuery en C# .NET que retorna Task<T>.
// Los métodos retornan CompletableFuture para permitir composición asíncrona
// sin bloquear hilos de plataforma, similar a async/await en .NET.
public interface IFakeApiInfrastructure {
    CompletableFuture<Optional<FakeApiEntity>> getUserAsync(int id, CancellationToken cancellationToken);

    CompletableFuture<Optional<FakeApiEntity>> getTitleAsync(int id, CancellationToken cancellationToken);
}
