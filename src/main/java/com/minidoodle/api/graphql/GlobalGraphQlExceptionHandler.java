package com.minidoodle.api.graphql;

import com.minidoodle.application.exception.IdempotencyConflictException;
import com.minidoodle.application.exception.ResourceNotFoundException;
import com.minidoodle.domain.exception.InvalidSlotOperationException;
import com.minidoodle.domain.exception.SlotOverlapException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import java.util.Map;

@ControllerAdvice(basePackages = "com.minidoodle.api.graphql")
public class GlobalGraphQlExceptionHandler {

    @GraphQlExceptionHandler(ResourceNotFoundException.class)
    public GraphQLError handleNotFound(ResourceNotFoundException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.NOT_FOUND)
            .message(ex.getMessage())
            .build();
    }

    @GraphQlExceptionHandler({SlotOverlapException.class, OptimisticLockingFailureException.class, IdempotencyConflictException.class})
    public GraphQLError handleConflict(Exception ex, DataFetchingEnvironment env) {
        String msg = (ex instanceof OptimisticLockingFailureException)
            ? "Time slot was modified or booked concurrently by another request"
            : ex.getMessage();
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message(msg)
            .extensions(Map.of("code", "CONFLICT"))
            .build();
    }

    @GraphQlExceptionHandler(InvalidSlotOperationException.class)
    public GraphQLError handleBadRequest(InvalidSlotOperationException ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message(ex.getMessage())
            .build();
    }
}
