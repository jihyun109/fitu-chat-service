package com.hsp.fituchat.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 브로커를 통해 전달되는 채팅 메시지 payload.
 * roomMemberIds를 포함하여 구독자가 DB 조회 없이 라우팅 가능하게 한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatBrokerMessage {

    private Long roomId;
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime sendTime;

    /** 채팅 목록 업데이트 대상 멤버 ID 목록 */
    private List<Long> roomMemberIds;

    private Long _vuId;
    private Long _seq;

    /**
     * W3C Trace Context (traceparent, tracestate 등) 전파용 필드.
     * publisher가 OTel propagator로 현재 trace context를 이 맵에 inject하고,
     * subscriber가 extract하여 같은 trace에 자식 span을 이어 붙임.
     *
     * 옛 메시지(이 필드가 없는)와의 호환을 위해 nullable + @JsonInclude(NON_NULL).
     */
    private Map<String, String> traceContext;
}
