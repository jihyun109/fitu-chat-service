package com.hsp.fituchat.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 채팅 메시지를 Redis Stream에 저장하는 버퍼.
 *
 * 메시지 전송 경로에서 DB INSERT를 제거하기 위해,
 * 메시지를 Redis Stream에 넣고 ChatMessagePersistConsumer가 비동기로 DB에 저장한다.
 *
 * Redis Stream을 사용하는 이유:
 * - List(LPUSH/RPOP)와 달리 Consumer Group + ACK을 지원
 * - Consumer가 메시지를 꺼낸 후 DB 저장에 실패해도 ACK하지 않으면 재처리 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessagePersistBuffer {

    private static final String STREAM_KEY = "chat:persist:stream";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 메시지를 Redis Stream에 추가한다.
     * XADD chat:persist:stream * roomId {roomId} senderId {senderId} content {content} sendTime {sendTime}
     */
    public void enqueue(long roomId, long senderId, String content, LocalDateTime sendTime) {
        Map<String, String> fields = Map.of(
                "roomId", String.valueOf(roomId),
                "senderId", String.valueOf(senderId),
                "content", content,
                "sendTime", sendTime.toString()
        );

        redisTemplate.opsForStream().add(
                MapRecord.create(STREAM_KEY, fields)
        );
    }

    public String getStreamKey() {
        return STREAM_KEY;
    }
}
