package com.hsp.fituchat.repository;

import com.hsp.fituchat.document.ChatMessageDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessageDocument, String> {

    List<ChatMessageDocument> findByChatRoomIdOrderByCreatedAtDesc(String chatRoomId, Pageable pageable);

    List<ChatMessageDocument> findByChatRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            String chatRoomId, LocalDateTime before, Pageable pageable);

    List<ChatMessageDocument> findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(
            String chatRoomId, LocalDateTime after);
}
