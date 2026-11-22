package com.minidoodle.domain.exception;

public class SlotOverlapException extends DomainException {

    public SlotOverlapException(String message) {
        super(message);
    }

    public SlotOverlapException(String message, Throwable cause) {
        super(message, cause);
    }
}
