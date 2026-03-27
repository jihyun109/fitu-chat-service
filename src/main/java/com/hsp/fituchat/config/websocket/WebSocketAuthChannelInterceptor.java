package com.hsp.fituchat.config.websocket;

import com.hsp.fituchat.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STOMP 채널 인터셉터 — WebSocket 인증 처리.
 *
 * STOMP CONNECT 프레임의 Authorization 헤더에서 JWT를 검증하고,
 * 인증된 userId를 세션 속성과 Principal에 저장한다.
 * 이후 메시지 핸들러(MessageController)에서 세션 속성으로 userId를 꺼내 사용한다.
 *
 * [보안 개선] SEND 시 JWT 만료 체크:
 * CONNECT 시점에만 JWT를 검증하면, access token 만료(1시간) 후에도
 * WebSocket 세션이 유지되어 메시지를 계속 보낼 수 있는 보안 취약점이 있다.
 * SEND 명령마다 세션에 저장된 만료 시각을 체크하여 만료된 세션의 메시지를 거부한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final JwtUtil jwtUtil;

    private static final String SESSION_KEY_TOKEN_EXPIRY = "tokenExpiry";

    @SneakyThrows
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            return handleConnect(accessor, message);
        }

        if (StompCommand.SEND.equals(command)) {
            return handleSend(accessor, message);
        }

        return message;
    }

    /**
     * CONNECT: JWT 검증 후 userId와 토큰 만료 시각을 세션에 저장한다.
     */
    private Message<?> handleConnect(StompHeaderAccessor accessor, Message<?> message) throws Exception {
        String token = accessor.getFirstNativeHeader("Authorization");

        if (token == null) {
            return null;
        }
        if (token.startsWith("Bearer ")) token = token.substring(7);

        Claims claims = jwtUtil.validateAndGetClaims(token);
        Long userId = claims.get("userId", Long.class);

        accessor.setUser(new StompPrincipal(String.valueOf(userId)));

        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        sessionAttrs.put("userId", userId);
        sessionAttrs.put(SESSION_KEY_TOKEN_EXPIRY, jwtUtil.getExpiryMillis(claims));

        return message;
    }

    /**
     * SEND: 세션에 저장된 토큰 만료 시각을 체크한다.
     * 만료되었으면 null을 반환하여 메시지를 거부한다.
     * 클라이언트는 토큰을 갱신한 후 재연결해야 한다.
     */
    private Message<?> handleSend(StompHeaderAccessor accessor, Message<?> message) {
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null) {
            return null;
        }

        Long tokenExpiry = (Long) sessionAttrs.get(SESSION_KEY_TOKEN_EXPIRY);
        if (tokenExpiry == null) {
            return null;
        }

        if (jwtUtil.isExpired(tokenExpiry)) {
            log.info("JWT 만료로 STOMP SEND 거부: userId={}", sessionAttrs.get("userId"));
            return null;
        }

        return message;
    }
}