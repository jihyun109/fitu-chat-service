package com.hsp.fituchat.service;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageStore {

    void saveAll(List<ChatMessageRecord> records);

    List<ChatMessageRecord> findLatest(String chatRoomId, int limit);

    List<ChatMessageRecord> findBefore(String chatRoomId, LocalDateTime before, int limit);

    List<ChatMessageRecord> findAfter(String chatRoomId, LocalDateTime after);
}
