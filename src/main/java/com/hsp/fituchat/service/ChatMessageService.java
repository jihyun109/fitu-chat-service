package com.hsp.fituchat.service;

import com.hsp.fituchat.dto.ChatMessageRequestDTO;
import com.hsp.fituchat.dto.ChatRoomMessageResponseDTO;

import java.time.LocalDateTime;

public interface ChatMessageService {

    void sendMessage(ChatMessageRequestDTO message, long userId);

    ChatRoomMessageResponseDTO getChatRoomMessages(String chatRoomId, LocalDateTime before, int limit);

    ChatRoomMessageResponseDTO getChatRoomMessageAfter(String chatRoomId, LocalDateTime after);
}
