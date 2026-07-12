package com.hsp.fituchat.service;

import com.hsp.fituchat.dto.ChatMessage;
import com.hsp.fituchat.dto.ChatMessageRequestDTO;
import com.hsp.fituchat.dto.ChatRoomMessageResponseDTO;
import com.hsp.fituchat.messaging.ChatBrokerMessage;
import com.hsp.fituchat.messaging.ChatMessagePersistBuffer;
import com.hsp.fituchat.messaging.MessageBrokerPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageStore chatMessageStore;
    private final MessageBrokerPort messageBrokerPort;
    private final ChatMessagePersistBuffer chatMessagePersistBuffer;

    private final Counter messagesSentCounter;
    private final Timer messageSendTimer;

    public ChatMessageServiceImpl(
            ChatMessageStore chatMessageStore,
            MessageBrokerPort messageBrokerPort,
            ChatMessagePersistBuffer chatMessagePersistBuffer,
            MeterRegistry meterRegistry) {
        this.chatMessageStore = chatMessageStore;
        this.messageBrokerPort = messageBrokerPort;
        this.chatMessagePersistBuffer = chatMessagePersistBuffer;

        this.messagesSentCounter = meterRegistry.counter("chat.messages.sent");
        this.messageSendTimer = meterRegistry.timer("chat.message.send.duration");
    }

    @Override
    @WithSpan("chat.sendMessage")
    public void sendMessage(ChatMessageRequestDTO message, long userId) {
        messageSendTimer.record(() -> doSendMessage(message, userId));
    }

    private void doSendMessage(ChatMessageRequestDTO message, long userId) {
        LocalDateTime sendTime = LocalDateTime.now();
        String senderName = message.getSenderName() != null ? message.getSenderName() : "알 수 없음";

        try {
            messageBrokerPort.publish(ChatBrokerMessage.builder()
                    .roomId(message.getRoomId())
                    .senderId(userId)
                    .senderName(senderName)
                    .content(message.getMessage())
                    .sendTime(sendTime)
                    ._vuId(message.get_vuId())
                    ._seq(message.get_seq())
                    .build());
        } catch (Exception e) {
            log.warn("Redis 메시지 발행 실패. roomId={}, senderId={}", message.getRoomId(), userId, e);
        }

        try {
            chatMessagePersistBuffer.enqueue(message.getRoomId(), userId, message.getMessage(), sendTime);
        } catch (Exception e) {
            log.warn("메시지 영구 저장 큐 추가 실패. roomId={}, senderId={}", message.getRoomId(), userId, e);
        }

        messagesSentCounter.increment();
    }

    @Override
    public ChatRoomMessageResponseDTO getChatRoomMessages(String chatRoomId, LocalDateTime before, int limit) {
        List<ChatMessageRecord> messages = (before != null)
                ? chatMessageStore.findBefore(chatRoomId, before, limit)
                : chatMessageStore.findLatest(chatRoomId, limit);

        List<ChatMessageRecord> reversed = new ArrayList<>(messages);
        Collections.reverse(reversed);

        return ChatRoomMessageResponseDTO.builder()
                .messages(reversed.stream().map(this::toChatMessage).toList())
                .build();
    }

    @Override
    public ChatRoomMessageResponseDTO getChatRoomMessageAfter(String chatRoomId, LocalDateTime after) {
        List<ChatMessageRecord> messages = chatMessageStore.findAfter(chatRoomId, after);

        return ChatRoomMessageResponseDTO.builder()
                .messages(messages.stream().map(this::toChatMessage).toList())
                .build();
    }

    private ChatMessage toChatMessage(ChatMessageRecord record) {
        return new ChatMessage(
                null,
                record.getContent(),
                null,
                record.getCreatedAt(),
                record.getSenderId()
        );
    }
}
