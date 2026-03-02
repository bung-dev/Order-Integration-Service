package com.inspien.common.exception;

import lombok.Getter;

@Getter
public class SftpException extends CustomException {
    private final boolean retryable;

    public SftpException(ErrorCode errorCode, boolean retryable) {
        super(errorCode);
        this.retryable = retryable;
    }

    public SftpException(ErrorCode errorCode, boolean retryable, String message) {
        super(errorCode, message);
        this.retryable = retryable;
    }
}
