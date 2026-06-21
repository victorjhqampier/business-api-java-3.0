package com.arify.domain.interfaces;

import com.arify.domain.commons.CancellationToken;
import com.arify.domain.entities.FakeApiEntity;

import java.util.Optional;

public interface IFakeApiInfrastructure {
    Optional<FakeApiEntity> getUserAsync(int id, CancellationToken cancellationToken);

    Optional<FakeApiEntity> getTitleAsync(int id, CancellationToken cancellationToken);
}
