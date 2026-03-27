package com.hsp.fituchat.repository;

import com.hsp.fituchat.document.ChatRoomDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatRoomRepository extends MongoRepository<ChatRoomDocument, String> {

    List<ChatRoomDocument> findByMembersUserIdOrderByLastMessageTimeDesc(Long userId);

    List<ChatRoomDocument> findByMembersUserIdInAndMembersUserId(List<Long> memberIds, Long userId);
}
