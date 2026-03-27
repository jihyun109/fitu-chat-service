package com.hsp.fituchat.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ErrorResponse(
        int status,
        String code,
        String message,
        LocalDateTime timestamp,
        List<FieldError> errors,
        String path
) {

    // 내부 static class였던 FieldError도 record로 간단하게 정의
    public record FieldError(
            String field,
            String value,
            String reason
    ) {
    }

    // ErrorCode만 있는 경우 (errors는 null 처리)
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return ErrorResponse.builder()
                .status(errorCode.getStatus())
                .code(errorCode.getErrorCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }

    // ErrorCode + 유효성 검사 에러 목록이 있는 경우
    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors, String path) {
        return ErrorResponse.builder()
                .status(errorCode.getStatus())
                .code(errorCode.getErrorCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .path(path)
                .build();
    }
}