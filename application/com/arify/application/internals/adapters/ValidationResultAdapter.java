package com.arify.application.internals.adapters;

public record ValidationResultAdapter(
        String code,
        String message,
        String field) {
}
