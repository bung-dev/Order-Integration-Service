package com.inspien.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    XML_PARSE_ERROR(400, "XML_PARSE_ERROR"),
    VALIDATION_ERROR(400, "VALIDATION_ERROR"),
    DUPLICATE_KEY(409, "DUPLICATE_KEY"),

    SFTP_SEND_FAIL(400, "SFTP_SEND_FAIL"),
    FILE_NOT_FOUND(404, "FILE_NOT_FOUND"),
    FILE_WRITE_FAIL(500, "FILE_WRITE_FAIL"),
    INVALID_PATH(400, "INVALID_PATH"),
    SFTP_UPLOAD_FAIL(500, "SFTP_UPLOAD_FAIL"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR"),

    SFTP_CONNECTION_FAIL(503, "SFTP_CONNECTION_FAIL"),
    INVALID_ORDER_DATA(400, "INVALID_ORDER_DATA"),
    TENANT_NOT_FOUND(404, "TENANT_NOT_FOUND");

    private final int status;
    private final String code;

    public CustomException exception() {
        return new CustomException(this);
    }
}
