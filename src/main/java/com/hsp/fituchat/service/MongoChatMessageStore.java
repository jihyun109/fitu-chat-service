package com.hsp.fituchat.service;

import com.hsp.fituchat.document.ChatMessageDocument;
import com.hsp.fituchat.repository.ChatMessageRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("mongo")
public class MongoChatMessageStore implements ChatMessageStore {

    private final ChatMessageRepository repository;

    public MongoChatMessageStore(ChatMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveAll(List<ChatMessageRecord> records) {
        List<ChatMessageDocument> documents = records.stream()
                .map(r -> ChatMessageDocument.builder()
                        .chatRoomId(r.getChatRoomId())
                        .senderId(r.getSenderId())
                        .messageType(r.getMessageType())
                        .content(r.getContent())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();
        repository.saveAll(documents);
    }

    @Override
    public List<ChatMessageRecord> findLatest(String chatRoomId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable)
                .stream().map(this::toRecord).toList();
    }

    @Override
    public List<ChatMessageRecord> findBefore(String chatRoomId, LocalDateTime before, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findByChatRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(chatRoomId, before, pageable)
                .stream().map(this::toRecord).toList();
    }

    @Override
    public List<ChatMessageRecord> findAfter(String chatRoomId, LocalDateTime after) {
        return repository.findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(chatRoomId, after)
                .stream().map(this::toRecord).toList();
    }

    private ChatMessageRecord toRecord(ChatMessageDocument doc) {
        return ChatMessageRecord.builder()
                .id(doc.getId())
                .chatRoomId(doc.getChatRoomId())
                .senderId(doc.getSenderId())
                .messageType(doc.getMessageType())
                .content(doc.getContent())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
