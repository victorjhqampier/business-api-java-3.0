package com.arify.startup.infrastructure;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.fakeapiinfra.queries.FakeApiCommand;
import com.arify.httpclientbuilder.HttpClientConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FakeApiSetting {

//    @Inject
//    @ConfigProperty(name = "REDISDATABASE_HOST")
//    String redisHost;
//
//    @Inject
//    @ConfigProperty(name = "REDISDATABASE_HOST")
//    String redisHost;

    @Produces
    public IFakeApiInfrastructure fakeApiInfrastructure(MicroserviceCallMemoryQueue memoryQueue, HttpClientConnector httpClientConnector) {
        /*LOGGER.info("services.AddSingleton<IFakeApiInfrastructure, FakeApiCommand>()");*/
        return new FakeApiCommand(memoryQueue, httpClientConnector);
    }
}
