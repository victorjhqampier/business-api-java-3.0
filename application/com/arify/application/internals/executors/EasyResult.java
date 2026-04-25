package com.arify.application.internals.executors;

import com.arify.application.internals.adapters.ValidationResultAdapter;

import java.util.List;

public final class EasyResult<T> {

    private final boolean success;
    private final String errorCode;
    private final T value;
    private final List<ValidationResultAdapter> validationErrors;

    private EasyResult(boolean success, String errorCode, T value, List<ValidationResultAdapter> validationErrors) {
        this.success = success;
        this.errorCode = errorCode;
        this.value = value;
        this.validationErrors = List.copyOf(validationErrors);
    }

    public static <T> EasyResult<T> success(T value) {
        return new EasyResult<>(true, null, value, List.of());
    }

    public static <T> EasyResult<T> failure(String errorCode, List<ValidationResultAdapter> validationErrors) {
        return new EasyResult<>(false, errorCode, null, validationErrors);
    }

    public static <T> EasyResult<T> empty() {
        return new EasyResult<>(true, null, null, List.of());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public T getValue() {
        return value;
    }

    public List<ValidationResultAdapter> getValidationErrors() {
        return validationErrors;
    }
}
