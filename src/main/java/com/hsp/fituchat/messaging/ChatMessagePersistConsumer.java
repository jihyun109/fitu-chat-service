package com.hsp.fituchat.messaging;

import com.hsp.fituchat.document.ChatMessageDocument;
import com.hsp.fituchat.repository.ChatMessageRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream에서 채팅 메시지를 꺼내서 MongoDB에 배치 저장하는 Consumer.
 * 3초마다 폴링하여 쌓인 메시지를 한 번에 saveAll()로 INSERT.
 */
@Slf4j
@Component
public class ChatMessagePersistConsumer {

    private static final String STREAM_KEY = "chat:persist:stream";
    private static final String GROUP_NAME = "persist-group";
    private static final int BATCH_SIZE = 200;

    private final String consumerName;
    private final RedisTemplate<String, String> redisTemplate;
    private final ChatMessageRepository chatMessageRepository;

    public ChatMessagePersistConsumer(
            RedisTemplate<String, String> redisTemplate,
            ChatMessageRepository chatMessageRepository) {
        this.redisTemplate = redisTemplate;
        this.chatMessageRepository = chatMessageRepository;

        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "consumer-" + ProcessHandle.current().pid();
        }
        this.consumerName = hostname;
    }

    @PostConstruct
    public void createConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            log.info("Redis Stream Consumer Group 생성: stream={}, group={}", STREAM_KEY, GROUP_NAME);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer Group 이미 존재: {}", GROUP_NAME);
            } else {
                log.warn("Consumer Group 생성 실패: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void consumeAndPersist() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP_NAME, consumerName),
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );

            if (records == null || records.isEmpty()) {
                return;
            }

            // MongoDB Document 변환
            List<ChatMessageDocument> documents = records.stream()
                    .map(record -> {
                        Map<Object, Object> fields = record.getValue();
                        return ChatMessageDocument.builder()
                                .chatRoomId(fields.get("roomId").toString())
                                .senderId(Long.parseLong(fields.get("senderId").toString()))
                                .content(fields.get("content").toString())
                                .messageType("TALK")
                                .createdAt(LocalDateTime.parse(fields.get("sendTime").toString()))
                                .build();
                    })
                    .toList();

            // 배치 INSERT
            chatMessageRepository.saveAll(documents);

            // ACK + 삭제
            for (MapRecord<String, Object, Object> record : records) {
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                redisTemplate.opsForStream().delete(STREAM_KEY, record.getId());
            }

            log.debug("채팅 메시지 {}건 MongoDB 배치 저장 완료", documents.size());

        } catch (Exception e) {
            log.error("채팅 메시지 배치 저장 실패", e);
        }
    }
}
