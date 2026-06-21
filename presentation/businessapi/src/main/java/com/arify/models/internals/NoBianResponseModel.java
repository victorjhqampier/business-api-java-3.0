package com.arify.models.internals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoBianResponseModel<T>(
        @JsonProperty("response") T response,
        @JsonProperty("errors") List<FieldErrorInternalModel> errors) {
}
