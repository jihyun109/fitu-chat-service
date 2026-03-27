package com.hsp.fituchat.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 에러코드 정리
@AllArgsConstructor
@Getter
public enum ErrorCode {
    // 공통 에러 (COMMON)
    NOT_FOUND(404, "COMMON-404", "요청한 리소스를 찾을 수 없습니다"),
    INTER_SERVER_ERROR(500, "COMMON-500", "서버 내부 오류가 발생했습니다"),
    METHOD_ARGUMENT_NOT_VALID(400, "COMMON-400", "잘못된 요청 파라미터입니다"),

    // 인증/인가 에러 (AUTH)
    UNAUTHORIZED(401, "AUTH-401", "인증이 필요합니다"),
    INVALID_REFRESH_TOKEN(401, "AUTH-401", "유효하지 않은 리프레시 토큰입니다"),
    INVALID_ACCESS_TOKEN(401, "AUTH-401", "유효하지 않은 액세스 토큰입니다"),
    TOKEN_EXPIRED(401, "AUTH-401", "토큰이 만료되었습니다"),
    INVALID_PASSWORD(401, "AUTH-401", "잘못된 비밀번호입니다"),

    // 사용자 관련 에러 (USER)
    USER_NOT_FOUND(404, "USER-404", "사용자를 찾을 수 없습니다"),
    DUPLICATE_USER(409, "USER-409", "이미 존재하는 사용자입니다"),
    DUPLICATE_EMAIL(409, "USER-409", "이미 등록된 이메일입니다. 다른 이메일을 사용해주세요"),
    INVALID_EMAIL_VERIFICATION_CODE(401, "USER-401", "유효하지 않은 이메일 인증 코드입니다"),
    INVALID_FRIEND_CODE(404, "USER-404", "유효하지 않은 친구 코드입니다."),
    EMAIL_CODE_TIMEOUT(410, "USER-410", "이메일 인증 코드가 만료되었습니다"),
    EMAIL_VERIFICATION_REQUEST_NOT_FOUND(400, "USER-400", "이메일 인증 요청을 찾을 수 없습니다"),

    // 파일 업로드 에러 (FILE)
    EMPTY_FILE(400, "FILE-400", "파일이 비어있습니다"),
    MISSING_FILE_EXTENSION(400, "FILE-400", "파일 확장자가 없습니다. 유효한 확장자를 포함한 파일을 업로드해주세요"),
    INVALID_FILE_EXTENSION(400, "FILE-400", "지원하지 않는 파일 확장자입니다. jpg, png, gif, jpeg 파일만 업로드 가능합니다"),
    INVALID_IMAGE_FILE(400, "FILE-400", "유효하지 않은 이미지 파일입니다"),
    FILE_UPLOAD_FAILED(500, "FILE-500", "파일 업로드에 실패했습니다"),

    // S3 관련 에러 (S3)
    S3_UPLOAD_FAILED(500, "S3-500", "S3 업로드에 실패했습니다"),
    S3_DELETE_FAILED(500, "S3-500", "S3 삭제에 실패했습니다"),

    // 운동 관련 에러 (WORKOUT)
    WORKOUT_NOT_FOUND(404, "WORKOUT-404", "운동을 찾을 수 없습니다"),
    WORKOUT_CALENDAR_NOT_FOUND(404, "WORKOUT-404", "운동 일정을 찾을 수 없습니다"),
    INVALID_WORKOUT_ID(400, "WORKOUT-400", "유효하지 않은 운동 ID입니다"),
    WORKOUT_RECORD_NOT_FOUND(404, "WORKOUT-RECORD-404", "운동 기록을 찾을 수 없습니다"),
    INVALID_TOTAL_MIN(400, "MIN-400", "총 minute이 유효하지 않습니다"),

    // 피지컬 정보 관련 에러 (PHYSICAL)
    PHYSICAL_INFO_NOT_FOUND(404, "PHYSICAL-404", "신체 정보를 찾을 수 없습니다"),
    INVALID_PHYSICAL_DATA(400, "PHYSICAL-400", "유효하지 않은 신체 데이터입니다."),

    // 친구 관계 관련 에러 (FRIENDSHIP)
    FRIENDSHIP_ALREADY_EXISTS(409, "FRIENDSHIP-409", "이미 친구 관계입니다."),
    INVALID_FRIEND_REQUEST(400, "FRIENDSHIP-400", "자기 자신은 친구로 추가할 수 없습니다."),

    INVALID_JWT(401, "AUTH-401", "유효하지 않은 JWT 토큰입니다"),
    JWT_EXPIRED(401, "AUTH-401", "JWT 토큰이 만료되었습니다"),
    JWT_SIGNATURE_INVALID(401, "AUTH-401", "JWT 서명이 유효하지 않습니다"),
    JWT_UNSUPPORTED(401, "AUTH-401", "지원되지 않는 JWT 토큰입니다"),
    JWT_EMPTY(400, "AUTH-400", "토큰이 비어있습니다"),

    // 댓글 관련 에러 (COMMENT)
    COMMENT_NOT_FOUND(404, "COMMENT-404", "댓글을 찾을 수 없습니다"),
    INVALID_COMMENT_AUTHOR(401, "COMMENT-401", "댓글 작성자가 아닙니다"),

    // 게시글 관련 에러 (POST)
    POST_NOT_FOUND(404, "POST-404", "게시글을 찾을 수 없습니다"),
    POST_WRITER_NOT_FOUND(404, "POST-404", "게시글 작성자를 찾을 수 없습니다"),

    // 채팅 관련 에러 (CHAT)
    CHAT_MESSAGE_PUBLISH_FAILED(500, "CHAT-500", "채팅 메시지 발행 중 오류가 발생했습니다")
    ;

    private int status;
    private String errorCode;
    private String message;
}
