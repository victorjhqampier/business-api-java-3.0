package com.arify.domain.entities;

public record FakeApiEntity(
        int userId,
        int id,
        String title,
        boolean completed) {
}
