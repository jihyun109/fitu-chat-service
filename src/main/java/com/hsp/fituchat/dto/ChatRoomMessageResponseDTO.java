package com.hsp.fituchat.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatRoomMessageResponseDTO {
    private List<ChatMessage> messages;
}
