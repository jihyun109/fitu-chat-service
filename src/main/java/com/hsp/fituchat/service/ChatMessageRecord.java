package com.hsp.fituchat.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRecord {
    private String id;
    private String chatRoomId;
    private Long senderId;
    private String messageType;
    private String content;
    private LocalDateTime createdAt;
}
