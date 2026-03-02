package com.inspien.common.exception;

public class OrderValidationException extends CustomException {
    public OrderValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
