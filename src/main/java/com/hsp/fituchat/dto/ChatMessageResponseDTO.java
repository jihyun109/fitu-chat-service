package com.hsp.fituchat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class ChatMessageResponseDTO {
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String message;
    private LocalDateTime sendTime;
    private Long _vuId;
    private Long _seq;
}
