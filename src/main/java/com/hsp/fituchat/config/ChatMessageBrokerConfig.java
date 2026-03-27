package com.hsp.fituchat.config;

import com.hsp.fituchat.messaging.redis.RedisMessageSubscriber;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 채팅 메시지 브로커 설정 (Redis Pub/Sub).
 *
 * 브로커를 교체할 때 이 설정 클래스를 대체하고,
 * 새 어댑터(MessageBrokerPort 구현체 + MessageListener)를 등록하면 된다.
 */
@Slf4j
@Configuration
public class ChatMessageBrokerConfig {

    @Bean
    public ChannelTopic chatMessageTopic() {
        return new ChannelTopic("chat:messages");
    }

    /**
     * redis-listener 스레드에서 팬아웃 작업을 분리하기 위한 전용 Executor.
     * redis-listener는 즉시 반환하여 다음 메시지 수신을 놓치지 않는다.
     *
     * CallerRunsPolicy: 큐가 가득 차면 호출 스레드(redis-listener)가 직접 실행한다.
     * 메시지 유실보다 지연 처리가 낫다.
     */
    @Bean
    public ThreadPoolTaskExecutor broadcastExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("broadcast-");
        executor.setRejectedExecutionHandler((runnable, pool) -> {
            log.warn("broadcastExecutor 큐 포화 — CallerRunsPolicy 적용. poolSize={}, queueSize={}",
                    pool.getPoolSize(), pool.getQueue().size());
            meterRegistry.counter("chat.broadcast.rejected").increment();
            new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, pool);
        });
        executor.initialize();
        return executor;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber subscriber,
            ChannelTopic chatMessageTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, chatMessageTopic);
        return container;
    }
}
