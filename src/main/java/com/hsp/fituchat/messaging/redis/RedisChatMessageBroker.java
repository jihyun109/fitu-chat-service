package com.hsp.fituchat.messaging.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsp.fituchat.error.BusinessException;
import com.hsp.fituchat.error.ErrorCode;
import com.hsp.fituchat.messaging.ChatBrokerMessage;
import com.hsp.fituchat.messaging.MessageBrokerPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Pub/Sub을 이용한 MessageBrokerPort 구현체.
 *
 * 다른 브로커(Kafka, RabbitMQ 등)로 교체하려면:
 *   1. 새 어댑터 클래스를 생성하여 MessageBrokerPort를 구현
 *   2. 이 클래스의 @Component를 제거하거나 @Profile로 전환
 *
 * OTel trace 전파:
 *   Redis pub/sub은 OTel agent가 자동으로 trace context를 메시지 본문에 넣어주지 않음.
 *   따라서 publish 시점에 W3C Trace Context propagator로 현재 context를
 *   메시지의 traceContext 필드에 inject. subscriber 측에서 extract하여 trace 이어붙임.
 */
@Slf4j
@Component
public class RedisChatMessageBroker implements MessageBrokerPort {

    /** Map을 carrier로 사용해 traceparent 헤더 등을 채워 넣는 setter */
    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> {
                if (carrier != null) carrier.put(key, value);
            };

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic chatMessageTopic;
    private final Counter publishFailureCounter;

    public RedisChatMessageBroker(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            ChannelTopic chatMessageTopic,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatMessageTopic = chatMessageTopic;
        this.publishFailureCounter = meterRegistry.counter("chat.redis.publish.failures");
    }

    @Override
    @WithSpan("chat.publish")
    public void publish(ChatBrokerMessage message) {
        // [OTEL-DEBUG]
        io.opentelemetry.api.trace.Span s = io.opentelemetry.api.trace.Span.current();
        log.info("[OTEL-DEBUG] publish() called traceId={} spanId={} valid={}",
                s.getSpanContext().getTraceId(),
                s.getSpanContext().getSpanId(),
                s.getSpanContext().isValid());

        try {
            // 현재 trace context를 메시지에 inject → subscriber가 같은 trace로 이어 받음
            injectTraceContext(message);

            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(chatMessageTopic.getTopic(), payload);
        } catch (JsonProcessingException e) {
            publishFailureCounter.increment();
            log.error("채팅 메시지 직렬화 실패: roomId={}, senderId={}", message.getRoomId(), message.getSenderId(), e);
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_PUBLISH_FAILED);
        }
    }

    private void injectTraceContext(ChatBrokerMessage message) {
        TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(Context.current(), carrier, MAP_SETTER);
        // 빈 맵이면 굳이 set 안 함 — agent 미부착 환경 / non-sampled trace 대응
        if (!carrier.isEmpty()) {
            message.setTraceContext(carrier);
        }
    }
}
