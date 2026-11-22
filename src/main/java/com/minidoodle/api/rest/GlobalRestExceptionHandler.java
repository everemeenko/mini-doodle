package com.minidoodle.api.rest;

import com.minidoodle.api.rest.dto.ErrorResponse;
import com.minidoodle.application.exception.IdempotencyConflictException;
import com.minidoodle.application.exception.ResourceNotFoundException;
import com.minidoodle.domain.exception.InvalidSlotOperationException;
import com.minidoodle.domain.exception.SlotOverlapException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;

@RestControllerAdvice(basePackages = "com.minidoodle.api.rest")
public class GlobalRestExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse(OffsetDateTime.now(), 404, "Not Found", ex.getMessage());
    }

    @ExceptionHandler({
            SlotOverlapException.class,
            OptimisticLockingFailureException.class,
            IdempotencyConflictException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(Exception ex) {
        String message = (ex instanceof OptimisticLockingFailureException)
            ? "Time slot was modified or booked concurrently by another request"
            : ex.getMessage();
        return new ErrorResponse(OffsetDateTime.now(), 409, "Conflict", message);
    }

    @ExceptionHandler({InvalidSlotOperationException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(Exception ex) {
        return new ErrorResponse(OffsetDateTime.now(), 400, "Bad Request", ex.getMessage());
    }


}
