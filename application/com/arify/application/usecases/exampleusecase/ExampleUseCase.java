package com.arify.application.usecases.exampleusecase;

import com.arify.application.ports.ExamplePort;
import com.arify.domain.entities.ExampleMessage;

public class ExampleUseCase implements ExamplePort {

    @Override
    public ExampleMessage retrieveExample() {
        return new ExampleMessage(
                "Hello World from Java",
                "Application",
                "ExampleUseCase",
                "Framework-agnostic use case");
    }
}
