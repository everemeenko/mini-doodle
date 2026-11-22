package com.minidoodle.api.graphql;

import com.minidoodle.application.exception.IdempotencyConflictException;
import com.minidoodle.application.exception.ResourceNotFoundException;
import com.minidoodle.domain.exception.InvalidSlotOperationException;
import com.minidoodle.domain.exception.SlotOverlapException;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice(basePackages = "com.minidoodle.api.graphql")
public class GlobalGraphQlExceptionHandler {

    private static final ErrorClassification CONFLICT = ErrorClassification.errorClassification("CONFLICT");

    @GraphQlExceptionHandler(ResourceNotFoundException.class)
    public GraphQLError handleNotFound(ResourceNotFoundException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.NOT_FOUND)
                .message(ex.getMessage())
                .extensions(Map.of("code", "NOT_FOUND", "httpStatus", 404))
                .build();
    }

    @GraphQlExceptionHandler({
            SlotOverlapException.class,
            OptimisticLockingFailureException.class,
            IdempotencyConflictException.class
    })
    public GraphQLError handleConflict(Exception ex, DataFetchingEnvironment env) {
        String msg = (ex instanceof OptimisticLockingFailureException)
                ? "Time slot was modified or booked concurrently by another request"
                : ex.getMessage();
        return GraphqlErrorBuilder.newError(env)
                .errorType(CONFLICT)
                .message(msg)
                .extensions(Map.of("code", "CONFLICT", "httpStatus", 409))
                .build();
    }

    @GraphQlExceptionHandler({InvalidSlotOperationException.class, ConstraintViolationException.class})
    public GraphQLError handleBadRequest(Exception ex, DataFetchingEnvironment env) {
        String message = (ex instanceof ConstraintViolationException cve)
                ? cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "))
                : ex.getMessage();

        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.BAD_REQUEST)
                .message(message)
                .extensions(Map.of("code", "BAD_REQUEST", "httpStatus", 400))
                .build();
    }
}
