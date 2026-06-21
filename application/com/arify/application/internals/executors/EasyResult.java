package com.arify.application.internals.executors;

import com.arify.application.internals.adapters.ValidationResultAdapter;
import java.util.List;

public record EasyResult<T>(
        boolean isSuccess,
        int status,
        T successValue,
        List<ValidationResultAdapter> validationValues) {

    public EasyResult {
        validationValues = validationValues == null ? List.of() : List.copyOf(validationValues);
    }

    public static <T> EasyResult<T> success(T successValue) {
        return success(successValue, 200);
    }

    public static <T> EasyResult<T> success(T successValue, int status) {
        return new EasyResult<>(true, status, successValue, List.of());
    }

    public static <T> EasyResult<T> failure(int status, List<ValidationResultAdapter> validationValues) {
        return new EasyResult<>(false, status, null, validationValues);
    }

    public static <T> EasyResult<T> empty() {
        return new EasyResult<>(true, 204, null, List.of());
    }
}
