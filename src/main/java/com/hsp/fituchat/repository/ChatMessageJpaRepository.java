package com.hsp.fituchat.repository;

import com.hsp.fituchat.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByChatRoomIdOrderByCreatedAtDesc(String chatRoomId, Pageable pageable);

    List<ChatMessageEntity> findByChatRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            String chatRoomId, LocalDateTime before, Pageable pageable);

    List<ChatMessageEntity> findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(
            String chatRoomId, LocalDateTime after);
}
