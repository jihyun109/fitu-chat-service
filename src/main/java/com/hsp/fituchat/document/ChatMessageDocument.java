package com.hsp.fituchat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@CompoundIndex(name = "roomId_createdAt", def = "{'chatRoomId': 1, 'createdAt': -1}")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDocument {

    @Id
    private String id;
    private String chatRoomId;
    private Long senderId;
    private String messageType;     // ENTER, TALK, QUIT
    private String content;
    private LocalDateTime createdAt;
}
