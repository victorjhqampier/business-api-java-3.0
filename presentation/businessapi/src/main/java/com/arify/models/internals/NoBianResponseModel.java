package com.arify.models.internals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoBianResponseModel<T>(
        @JsonProperty("Response") T response,
        @JsonProperty("Errors") List<FieldErrorInternalModel> errors) {
}
