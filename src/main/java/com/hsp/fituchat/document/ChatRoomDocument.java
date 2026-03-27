package com.hsp.fituchat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅방 Document.
 * lastMessage/lastMessageTime은 채팅방 목록에서 마지막 메시지를 보여주기 위해 저장.
 */
@Document(collection = "chat_rooms")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDocument {

    @Id
    private String id;
    private String roomName;
    private List<ChatRoomMember> members;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
}
