package com.arify.helpers;

import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.models.internals.FieldErrorInternalModel;
import com.arify.models.internals.NoBianResponseModel;
import jakarta.ws.rs.core.Response;

import java.util.List;

public final class EasyResponseHelper {
    private EasyResponseHelper() {
    }

    public static <T> Response successResponse(T dataResponse) {
        return Response.ok(new NoBianResponseModel<>(dataResponse, null)).build();
    }

    public static Response warningResponse(List<ValidationResultAdapter> errorList, int statusCode) {
        List<FieldErrorInternalModel> errors = errorList.stream()
                .map(error -> new FieldErrorInternalModel(error.code(), error.message(), error.field()))
                .toList();

        return Response.status(statusCode)
                .entity(new NoBianResponseModel<>(null, errors))
                .build();
    }

    public static Response errorResponse(String errorCode, String message, int statusCode) {
        return Response.status(statusCode)
                .entity(new NoBianResponseModel<>(null, List.of(new FieldErrorInternalModel(errorCode, message, null))))
                .build();
    }
}
