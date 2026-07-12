package com.hsp.fituchat.messaging.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsp.fituchat.dto.ChatMessageResponseDTO;
import com.hsp.fituchat.messaging.ChatBrokerMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Redis Pub/Sub 구독자.
 * Redis에서 메시지를 수신하여 WebSocket 클라이언트에 브로드캐스트한다.
 *
 * 모든 서버 인스턴스가 Redis를 구독하므로,
 * 어느 인스턴스로 WebSocket 연결된 클라이언트든 메시지를 수신할 수 있다.
 *
 * OTel trace 전파:
 *   메시지의 traceContext 필드에서 publisher가 inject한 W3C trace context를
 *   extract → 그 context 안에서 broadcastExecutor.execute() 호출.
 *   OTel agent가 Executor 자동 instrumentation을 하므로 worker thread에서도
 *   같은 trace로 이어진 자식 span이 생성됨 (chat-service Pod #1·#2 모두에서).
 */
@Slf4j
@Component
public class RedisMessageSubscriber implements MessageListener {

    /** 메시지의 traceContext 맵에서 traceparent 등을 꺼내는 getter */
    private static final TextMapGetter<Map<String, String>> MAP_GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier == null ? Collections.emptyList() : carrier.keySet();
                }
                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier == null ? null : carrier.get(key);
                }
            };

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

            // publisher가 inject한 trace context를 추출 → 같은 trace에 자식 span으로 이어 붙임
            // makeCurrent() scope 안에서 execute()를 호출하면 OTel agent의 Executor 자동 계측이
            // worker thread에 context를 전파해줌
            Context extracted = extractTraceContext(brokerMessage);
            try (Scope ignored = extracted.makeCurrent()) {
                // redis-listener 스레드 즉시 해방 — 팬아웃은 broadcastExecutor에 위임
                broadcastExecutor.execute(() -> broadcast(brokerMessage));
            }
        } catch (JsonProcessingException e) {
            log.error("Redis 채팅 메시지 역직렬화 실패: payload={}", payload, e);
        }
    }

    private Context extractTraceContext(ChatBrokerMessage brokerMessage) {
        Map<String, String> carrier = brokerMessage.getTraceContext();
        if (carrier == null || carrier.isEmpty()) {
            // traceContext 없는 옛 메시지 또는 sampling drop 메시지 → 새 context 시작
            return Context.current();
        }
        TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
        return propagator.extract(Context.current(), carrier, MAP_GETTER);
    }

    @WithSpan("chat.broadcast")
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
