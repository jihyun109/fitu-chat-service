package com.hsp.fituchat.config.websocket;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * STOMP 인바운드 채널에 trace span을 부여하는 인터셉터.
 *
 * 왜 필요한가:
 *   OTel Java agent는 Spring WebSocket/STOMP 프레임 처리를 자동 계측하지 않는다.
 *   따라서 STOMP SEND가 들어와 MessageController.message() 같은 핸들러로 dispatch될 때
 *   부모 trace context가 없어, 그 안에서 RedisChatMessageBroker.publish()가 호출되어도
 *   carrier에 inject할 valid traceparent가 없다.
 *
 * 해결 패턴:
 *   1. preSend()에서 STOMP 프레임 종류별로 새 span 시작 + makeCurrent
 *      → caller 스레드의 ThreadLocal에 active context 등록
 *   2. message header에 span/scope 객체 저장 (afterSendCompletion에서 꺼내 종료하기 위해)
 *   3. ExecutorSubscribableChannel의 dispatch가 별도 스레드 풀에서 실행되지만,
 *      OTel agent의 Executor 자동 instrumentation이 caller 스레드의 active context를
 *      task와 함께 capture/restore해주므로 handler 스레드에서도 같은 trace context 사용 가능
 *   4. afterSendCompletion()에서 caller 스레드의 scope.close() + span.end()로 leak 방지
 *
 * 결과:
 *   STOMP CONNECT/SUBSCRIBE/SEND/DISCONNECT 각각이 root span으로 기록되고,
 *   그 안에서 호출되는 핸들러·서비스·Redis publish가 모두 자식 span으로 한 trace에 묶임.
 */
@Slf4j
@Component
public class StompTracingChannelInterceptor implements ChannelInterceptor {

    /** message header에 span 보관 (afterSendCompletion에서 종료) */
    private static final String SPAN_HEADER = "otelSpan";
    /** message header에 scope 보관 (afterSendCompletion에서 close, ThreadLocal leak 방지) */
    private static final String SCOPE_HEADER = "otelScope";

    private final Tracer tracer;

    public StompTracingChannelInterceptor() {
        this.tracer = GlobalOpenTelemetry.getTracer("fitu-chat", "1.0");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        // [OTEL-DEBUG]
        log.info("[OTEL-DEBUG] StompTracingChannelInterceptor.preSend command={}", command);

        if (command == null) {
            // heartbeat 등 command 없는 frame은 trace 안 만듦
            return message;
        }

        String spanName = "stomp." + command.name().toLowerCase();
        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("messaging.system", "stomp")
                .setAttribute("messaging.operation", command.name())
                .startSpan();

        if (accessor.getDestination() != null) {
            span.setAttribute("messaging.destination", accessor.getDestination());
        }
        if (accessor.getSessionId() != null) {
            span.setAttribute("stomp.session.id", accessor.getSessionId());
        }

        // makeCurrent → caller 스레드 ThreadLocal에 등록
        // OTel agent의 Executor instrumentation이 channel.send 안의 executor.execute 호출 시점에
        // 이 context를 capture하여 handler 스레드로 자동 propagation
        Scope scope = span.makeCurrent();

        accessor.setHeader(SPAN_HEADER, span);
        accessor.setHeader(SCOPE_HEADER, scope);
        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        Object scopeObj = accessor.getHeader(SCOPE_HEADER);
        Object spanObj = accessor.getHeader(SPAN_HEADER);

        if (scopeObj instanceof Scope) {
            try {
                ((Scope) scopeObj).close();
            } catch (Throwable t) {
                log.debug("scope.close() 중 예외", t);
            }
        }
        if (spanObj instanceof Span) {
            Span span = (Span) spanObj;
            try {
                if (ex != null) {
                    span.recordException(ex);
                    span.setStatus(StatusCode.ERROR, ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                }
            } finally {
                span.end();
            }
        }
    }
}
