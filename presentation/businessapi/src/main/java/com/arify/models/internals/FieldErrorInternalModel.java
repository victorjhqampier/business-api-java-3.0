package com.arify.models.internals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorInternalModel(
        @JsonProperty("StatusCode") String statusCode,
        @JsonProperty("Message") String message,
        @JsonProperty("Field") String field) {
}
