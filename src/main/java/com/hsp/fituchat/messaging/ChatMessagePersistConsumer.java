package com.hsp.fituchat.messaging;

import com.hsp.fituchat.service.ChatMessageRecord;
import com.hsp.fituchat.service.ChatMessageStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream에서 채팅 메시지를 꺼내서 활성 프로파일의 DB(MongoDB or MySQL)에 배치 저장.
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
    private final ChatMessageStore chatMessageStore;
    private final Timer batchSaveTimer;
    private final Counter batchSaveCounter;

    public ChatMessagePersistConsumer(
            RedisTemplate<String, String> redisTemplate,
            ChatMessageStore chatMessageStore,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.chatMessageStore = chatMessageStore;
        this.batchSaveTimer = meterRegistry.timer("chat.persist.batch.duration");
        this.batchSaveCounter = meterRegistry.counter("chat.persist.batch.count");

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

            List<ChatMessageRecord> messages = records.stream()
                    .map(record -> {
                        Map<Object, Object> fields = record.getValue();
                        return ChatMessageRecord.builder()
                                .chatRoomId(fields.get("roomId").toString())
                                .senderId(Long.parseLong(fields.get("senderId").toString()))
                                .messageType("TALK")
                                .content(fields.get("content").toString())
                                .createdAt(LocalDateTime.parse(fields.get("sendTime").toString()))
                                .build();
                    })
                    .toList();

            batchSaveTimer.record(() -> chatMessageStore.saveAll(messages));
            batchSaveCounter.increment(messages.size());

            for (MapRecord<String, Object, Object> record : records) {
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                redisTemplate.opsForStream().delete(STREAM_KEY, record.getId());
            }

            log.debug("채팅 메시지 {}건 배치 저장 완료", messages.size());

        } catch (Exception e) {
            log.error("채팅 메시지 배치 저장 실패", e);
        }
    }
}
