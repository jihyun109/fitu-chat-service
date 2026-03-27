package com.hsp.fituchat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChatMessage {
    private String senderName;
    private String message;
    private String senderProfileUrl;
    private LocalDateTime sendTime;
    private long senderId;
}
