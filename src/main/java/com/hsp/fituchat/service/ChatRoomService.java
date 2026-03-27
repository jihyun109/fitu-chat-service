package com.hsp.fituchat.service;

import com.hsp.fituchat.dto.ChatRoomCreateRequestDTO;
import com.hsp.fituchat.dto.ChatRoomCreateResponseDTO;
import com.hsp.fituchat.dto.ChatRoomListResponseDTO;

public interface ChatRoomService {
    ChatRoomCreateResponseDTO createChatRoom(Long userId, ChatRoomCreateRequestDTO chatRoomCreateRequestDTO);

    ChatRoomListResponseDTO getChatRoomList(Long userId);
}
