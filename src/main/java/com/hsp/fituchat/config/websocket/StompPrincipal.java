package com.hsp.fituchat.config.websocket;

import java.security.Principal;

/**
 * WebSocket 세션에 인증된 사용자를 나타내는 Principal 구현체.
 *
 * Spring의 SimpMessageSendingOperations.convertAndSendToUser() 등에서
 * 사용자를 식별할 때 이 객체의 name(= userId 문자열)이 사용된다.
 * WebSocketAuthChannelInterceptor가 STOMP CONNECT 시점에 생성하여 세션에 등록한다.
 */
public class StompPrincipal implements Principal {
    private final String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
