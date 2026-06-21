package com.arify.helpers;

import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.models.internals.FieldErrorInternalModel;
import com.arify.models.internals.NoBianResponseModel;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

public final class EasyResponseHelper {
    private EasyResponseHelper() {
    }

    public static <T> Response successResponse(T dataResponse) {
        return Response.ok(new NoBianResponseModel<>(dataResponse, null)).build();
    }

    // Optimizado: loop tradicional en lugar de stream para minimizar asignaciones en hot path.
    public static Response warningResponse(List<ValidationResultAdapter> errorList, int statusCode) {
        List<FieldErrorInternalModel> errors = new ArrayList<>(errorList.size());
        for (ValidationResultAdapter error : errorList) {
            errors.add(new FieldErrorInternalModel(error.code(), error.message(), error.field()));
        }

        return Response.status(statusCode)
                .entity(new NoBianResponseModel<>(null, errors))
                .build();
    }

    public static Response errorResponse(String errorCode, String message) {
        return Response.status(500)
                .entity(new NoBianResponseModel<>(null, List.of(new FieldErrorInternalModel(errorCode, message, null))))
                .build();
    }

    public static Response noContent(int statusCode) {
        return Response.status(statusCode).build();
    }
}
