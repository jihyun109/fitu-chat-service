package com.hsp.fituchat.service;

import com.hsp.fituchat.entity.ChatMessageEntity;
import com.hsp.fituchat.repository.ChatMessageJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("mysql")
public class JpaChatMessageStore implements ChatMessageStore {

    private static final String INSERT_SQL =
            "INSERT INTO chat_messages (chat_room_id, sender_id, message_type, content, created_at) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final int JDBC_BATCH_SIZE = 200;

    private final ChatMessageJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public JpaChatMessageStore(ChatMessageJpaRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(List<ChatMessageRecord> records) {
        // JPA saveAll은 IDENTITY 전략이라 Hibernate batching이 비활성화됨 (1건씩 INSERT).
        // 진짜 multi-row INSERT를 위해 raw JDBC batchUpdate 사용.
        // rewriteBatchedStatements=true 와 결합하면 mysql-connector-j가
        // INSERT INTO ... VALUES (...), (...), ... 한 statement로 변환.
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                records,
                JDBC_BATCH_SIZE,
                (ps, r) -> {
                    ps.setString(1, r.getChatRoomId());
                    ps.setLong(2, r.getSenderId());
                    ps.setString(3, r.getMessageType());
                    ps.setString(4, r.getContent());
                    ps.setTimestamp(5, Timestamp.valueOf(r.getCreatedAt()));
                }
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageRecord> findLatest(String chatRoomId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable)
                .stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageRecord> findBefore(String chatRoomId, LocalDateTime before, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findByChatRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(chatRoomId, before, pageable)
                .stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageRecord> findAfter(String chatRoomId, LocalDateTime after) {
        return repository.findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(chatRoomId, after)
                .stream().map(this::toRecord).toList();
    }

    private ChatMessageRecord toRecord(ChatMessageEntity entity) {
        return ChatMessageRecord.builder()
                .id(String.valueOf(entity.getId()))
                .chatRoomId(entity.getChatRoomId())
                .senderId(entity.getSenderId())
                .messageType(entity.getMessageType())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
