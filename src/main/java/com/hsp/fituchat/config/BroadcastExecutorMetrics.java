package com.hsp.fituchat.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * broadcastExecutor 스레드풀 상태를 Gauge 메트릭으로 노출한다.
 *
 * Gauge는 참조 객체를 WeakReference로 보유하므로,
 * 이 @Component 빈이 강한 참조를 유지하여 GC 수집을 방지한다.
 */
@Slf4j
@Component
public class BroadcastExecutorMetrics {

    private final ThreadPoolExecutor threadPool;
    private final MeterRegistry registry;

    public BroadcastExecutorMetrics(
            @Qualifier("broadcastExecutor") ThreadPoolTaskExecutor executor,
            MeterRegistry registry) {
        this.threadPool = executor.getThreadPoolExecutor();
        this.registry = registry;
        log.info("BroadcastExecutorMetrics: threadPool class={}, max={}",
                threadPool.getClass().getName(), threadPool.getMaximumPoolSize());
    }

    @PostConstruct
    public void registerMetrics() {
        // this는 Spring 빈이므로 GC에 수집되지 않음 → Gauge의 WeakReference가 유효
        Gauge.builder("chat.broadcast.pool.active", this, self -> self.threadPool.getActiveCount())
                .description("브로드캐스트 스레드풀 활성 스레드 수").register(registry);
        Gauge.builder("chat.broadcast.pool.size", this, self -> self.threadPool.getPoolSize())
                .description("브로드캐스트 스레드풀 현재 크기").register(registry);
        Gauge.builder("chat.broadcast.pool.max", this, self -> self.threadPool.getMaximumPoolSize())
                .description("브로드캐스트 스레드풀 최대 크기").register(registry);
        Gauge.builder("chat.broadcast.queue.size", this, self -> self.threadPool.getQueue().size())
                .description("브로드캐스트 큐 대기 작업 수").register(registry);
        log.info("BroadcastExecutorMetrics: Gauge 4개 등록 완료");
    }
}
