package com.arify.models.internals;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BianErrorInternalModel(
        @JsonProperty("Status_code") String statusCode,
        @JsonProperty("Message") String message) {
}
