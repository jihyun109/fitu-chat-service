package com.hsp.fituchat.service;

import com.hsp.fituchat.document.ChatRoomDocument;
import com.hsp.fituchat.document.ChatRoomMember;
import com.hsp.fituchat.dto.ChatRoom;
import com.hsp.fituchat.dto.ChatRoomCreateRequestDTO;
import com.hsp.fituchat.dto.ChatRoomCreateResponseDTO;
import com.hsp.fituchat.dto.ChatRoomListResponseDTO;
import com.hsp.fituchat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    @Override
    public ChatRoomCreateResponseDTO createChatRoom(Long userId, ChatRoomCreateRequestDTO request) {
        List<Long> memberIds = request.getMemberIds();

        // 멤버 목록 구성 (요청자 + 초대 멤버)
        List<ChatRoomMember> members = new ArrayList<>();
        for (Long memberId : memberIds) {
            members.add(ChatRoomMember.builder()
                    .userId(memberId)
                    .build());
        }
        members.add(ChatRoomMember.builder()
                .userId(userId)
                .build());

        // 채팅방 생성 및 저장
        ChatRoomDocument saved = chatRoomRepository.save(ChatRoomDocument.builder()
                .roomName(request.getName())
                .members(members)
                .build());

        return ChatRoomCreateResponseDTO.builder()
                .id(saved.getId())
                .build();
    }

    @Override
    public ChatRoomListResponseDTO getChatRoomList(Long userId) {
        List<ChatRoomDocument> rooms = chatRoomRepository.findByMembersUserIdOrderByLastMessageTimeDesc(userId);

        List<ChatRoom> chatRoomList = rooms.stream()
                .map(room -> {
                    // 상대방 이름을 채팅방 이름으로 표시
                    String otherName = room.getMembers().stream()
                            .filter(m -> !m.getUserId().equals(userId))
                            .findFirst()
                            .map(ChatRoomMember::getName)
                            .orElse(room.getRoomName());

                    String otherProfileUrl = room.getMembers().stream()
                            .filter(m -> !m.getUserId().equals(userId))
                            .findFirst()
                            .map(ChatRoomMember::getProfileUrl)
                            .orElse(null);

                    return new ChatRoom(
                            room.getId(),
                            otherName != null ? otherName : room.getRoomName(),
                            room.getLastMessage(),
                            otherProfileUrl
                    );
                })
                .toList();

        return ChatRoomListResponseDTO.builder()
                .chatRoomList(chatRoomList).build();
    }
}
