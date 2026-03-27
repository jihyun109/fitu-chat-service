package com.hsp.fituchat.messaging;

/**
 * 채팅 메시지 브로커 포트 (추상화 계층)
 *
 * 서비스 계층은 이 인터페이스에만 의존하므로,
 * 브로커 구현체(Redis, Kafka, RabbitMQ 등)를 교체해도 서비스 코드는 변경 불필요.
 */
public interface MessageBrokerPort {

    void publish(ChatBrokerMessage message);
}
