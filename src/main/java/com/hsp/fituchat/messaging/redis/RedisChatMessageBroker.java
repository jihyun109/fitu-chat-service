package com.hsp.fituchat.messaging.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsp.fituchat.error.BusinessException;
import com.hsp.fituchat.error.ErrorCode;
import com.hsp.fituchat.messaging.ChatBrokerMessage;
import com.hsp.fituchat.messaging.MessageBrokerPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub을 이용한 MessageBrokerPort 구현체.
 *
 * 다른 브로커(Kafka, RabbitMQ 등)로 교체하려면:
 *   1. 새 어댑터 클래스를 생성하여 MessageBrokerPort를 구현
 *   2. 이 클래스의 @Component를 제거하거나 @Profile로 전환
 */
@Slf4j
@Component
public class RedisChatMessageBroker implements MessageBrokerPort {

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
    public void publish(ChatBrokerMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(chatMessageTopic.getTopic(), payload);
        } catch (JsonProcessingException e) {
            publishFailureCounter.increment();
            log.error("채팅 메시지 직렬화 실패: roomId={}, senderId={}", message.getRoomId(), message.getSenderId(), e);
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_PUBLISH_FAILED);
        }
    }
}
