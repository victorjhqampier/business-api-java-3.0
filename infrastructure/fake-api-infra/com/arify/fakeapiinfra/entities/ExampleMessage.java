package com.arify.domain.entities;

public record ExampleMessage(
        String message,
        String layer,
        String useCase,
        String detail) {
}
