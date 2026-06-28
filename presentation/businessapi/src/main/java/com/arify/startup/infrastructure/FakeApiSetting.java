package com.arify.startup.infrastructure;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.fakeapiinfra.queries.FakeApiCommand;
import com.arify.httpclientbuilder.HttpClientConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class FakeApiSetting {
    @Produces
    @ApplicationScoped
    public IFakeApiInfrastructure fakeApiInfrastructure(MicroserviceCallMemoryQueue memoryQueue, HttpClientConnector httpClientConnector) {
        /*LOGGER.info("services.AddSingleton<IFakeApiInfrastructure, FakeApiCommand>()");*/
        return new FakeApiCommand(memoryQueue, httpClientConnector);
    }
}
