package com.hsp.fituchat.messaging.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsp.fituchat.dto.ChatMessageResponseDTO;
import com.hsp.fituchat.messaging.ChatBrokerMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

/**
 * Redis Pub/Sub 구독자.
 * Redis에서 메시지를 수신하여 WebSocket 클라이언트에 브로드캐스트한다.
 *
 * 모든 서버 인스턴스가 Redis를 구독하므로,
 * 어느 인스턴스로 WebSocket 연결된 클라이언트든 메시지를 수신할 수 있다.
 */
@Slf4j
@Component
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper;
    private final Executor broadcastExecutor;
    private final Counter broadcastCounter;

    public RedisMessageSubscriber(
            SimpMessageSendingOperations messagingTemplate,
            ObjectMapper objectMapper,
            @Qualifier("broadcastExecutor") Executor broadcastExecutor,
            MeterRegistry meterRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.broadcastExecutor = broadcastExecutor;
        this.broadcastCounter = meterRegistry.counter("chat.messages.broadcast");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            ChatBrokerMessage brokerMessage = objectMapper.readValue(payload, ChatBrokerMessage.class);
            // redis-listener 스레드 즉시 해방 — 팬아웃은 broadcastExecutor에 위임
            broadcastExecutor.execute(() -> broadcast(brokerMessage));
        } catch (JsonProcessingException e) {
            log.error("Redis 채팅 메시지 역직렬화 실패: payload={}", payload, e);
        }
    }

    private void broadcast(ChatBrokerMessage brokerMessage) {
        ChatMessageResponseDTO responseDTO = ChatMessageResponseDTO.builder()
                .roomId(brokerMessage.getRoomId())
                .senderId(brokerMessage.getSenderId())
                .senderName(brokerMessage.getSenderName())
                .message(brokerMessage.getContent())
                .sendTime(brokerMessage.getSendTime())
                ._vuId(brokerMessage.get_vuId())
                ._seq(brokerMessage.get_seq())
                .build();

        // DTO를 byte[]로 1회만 직렬화 — 이후 모든 목적지에 동일 bytes 재사용
        byte[] serialized;
        try {
            serialized = objectMapper.writeValueAsBytes(responseDTO);
        } catch (JsonProcessingException e) {
            log.error("채팅 메시지 직렬화 실패: roomId={}", brokerMessage.getRoomId(), e);
            return;
        }

        // 채팅방 구독자에게 메시지 전달
        send("/sub/chat/room/" + brokerMessage.getRoomId(), serialized);
        broadcastCounter.increment();
    }

    private void send(String destination, byte[] payload) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);
        org.springframework.messaging.Message<byte[]> msg =
                MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
        messagingTemplate.send(destination, msg);
    }
}
