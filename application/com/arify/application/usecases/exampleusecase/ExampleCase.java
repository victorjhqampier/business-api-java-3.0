package com.arify.application.usecases.exampleusecase;

import com.arify.application.adapters.ExecuteExampleTwoAdapter;
import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.internals.executors.FluentValidationExecutor;

import java.util.List;

public class ExampleCase {

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    public EasyResult<RetrieveExampleAdapter> showExample(TraceIdentifierAdapter traceIdentifier) {
        List<ValidationResultAdapter> errors = FluentValidationExecutor.validate(traceIdentifier);
        if (!errors.isEmpty()) {
            return EasyResult.failure(VALIDATION_FAILED, errors);
        }

        RetrieveExampleAdapter result = new RetrieveExampleAdapter("ping", "pong");
        return EasyResult.success(result);
    }

    public EasyResult<ExecuteExampleTwoAdapter> executeExampleTwo(TraceIdentifierAdapter traceIdentifier) {
        List<ValidationResultAdapter> errors = FluentValidationExecutor.validate(traceIdentifier);
        if (!errors.isEmpty()) {
            return EasyResult.failure(VALIDATION_FAILED, errors);
        }

        ExecuteExampleTwoAdapter result = new ExecuteExampleTwoAdapter("pong", "ping");
        return EasyResult.success(result);
    }
}
