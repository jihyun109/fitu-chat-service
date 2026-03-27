package com.hsp.fituchat.config.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket(STOMP) 전반 설정.
 * 연결 엔드포인트, 메시지 라우팅 prefix, 채널 인터셉터를 등록한다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    /**
     * 클라이언트가 WebSocket 연결을 맺을 엔드포인트 등록.
     * (HTTP Handshake 단계 인증은 현재 비활성화 — STOMP CONNECT에서 처리)
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
//                .addInterceptors(webSocketAuthInterceptor)  // HTTP Handshake 단계 인증 (현재 미사용)
                .setAllowedOriginPatterns("*");
    }

    /**
     * STOMP 메시지 라우팅 규칙 설정.
     * /pub/** : 클라이언트 → 서버 (MessageController의 @MessageMapping과 매핑)
     * /sub/** : 서버 → 클라이언트 (클라이언트가 subscribe할 목적지 prefix)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/pub");
        // heartbeat: [서버→클라이언트, 클라이언트→서버] 각 25초 간격으로 핑/퐁 교환
        // 일정 시간 응답이 없으면 죽은 연결로 판단하여 자동으로 정리한다
        registry.enableSimpleBroker("/sub")
                .setHeartbeatValue(new long[]{25000, 25000})
                .setTaskScheduler(heartbeatScheduler());
    }

    /**
     * heartbeat 전송을 담당하는 스케줄러.
     * SimpleBroker가 주기적으로 heartbeat 프레임을 보내려면 TaskScheduler가 필요하다.
     */
    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); // heartbeat 전송 전용이므로 1개면 충분
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }

    /**
     * 인바운드 채널에 JWT 인증 인터셉터 등록.
     * STOMP CONNECT 프레임 수신 시 WebSocketAuthChannelInterceptor가 먼저 실행된다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor);
        // 0.6 vCPU 환경에서 스레드 폭발 방지 — 큐 상한으로 무제한 적재 차단
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(8)
                .queueCapacity(200);
    }
}

