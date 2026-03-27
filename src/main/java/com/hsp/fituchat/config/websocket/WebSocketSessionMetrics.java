package com.hsp.fituchat.config.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 세션 연결/해제 이벤트를 감지하여 활성 세션 수를 메트릭으로 노출한다.
 *
 * Spring WebSocket은 STOMP CONNECT/DISCONNECT 시 이벤트를 발행하는데,
 * 이 리스너가 해당 이벤트를 받아서 카운터를 증감한다.
 *
 * Prometheus에서 chat_websocket_sessions 로 수집되며,
 * Grafana 대시보드에서 현재 활성 WebSocket 연결 수를 실시간으로 확인할 수 있다.
 *
 * heartbeat 도입 전후 비교:
 *   - Before: 클라이언트가 끊겨도 세션 수가 안 줄어듦 (좀비 연결)
 *   - After: 25~50초 내에 세션 수가 감소 (정상 정리)
 */
@Slf4j
@Component
public class WebSocketSessionMetrics {

    // AtomicInteger: 여러 스레드에서 동시에 증감해도 안전한 정수형
    // Gauge는 "현재 값"을 보여주는 메트릭이므로, 이 값을 직접 참조한다
    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public WebSocketSessionMetrics(MeterRegistry meterRegistry) {
        // Gauge: 현재 시점의 값을 보여주는 메트릭 (Counter와 달리 올라갔다 내려갈 수 있음)
        // activeSessions의 현재 값을 chat.websocket.sessions라는 이름으로 Prometheus에 노출
        Gauge.builder("chat.websocket.sessions", activeSessions, AtomicInteger::get)
                .description("현재 활성 WebSocket 세션 수")
                .register(meterRegistry);
    }

    // STOMP CONNECT 성공 시 Spring이 이 이벤트를 발행한다
    @EventListener
    public void onConnect(SessionConnectEvent event) {
        int count = activeSessions.incrementAndGet();
        log.debug("WebSocket 세션 연결: 활성 세션 수={}", count);
    }

    // STOMP DISCONNECT 또는 연결 끊김 시 Spring이 이 이벤트를 발행한다
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        int count = activeSessions.decrementAndGet();
        log.debug("WebSocket 세션 해제: 활성 세션 수={}", count);
    }
}
