package com.hsp.fituchat.dto;

import lombok.Getter;

@Getter
public class ChatMessageRequestDTO {
    private long roomId;
    private String message;
    private String senderName;
    private String senderProfileUrl;
    private Long _vuId;
    private Long _seq;
}
