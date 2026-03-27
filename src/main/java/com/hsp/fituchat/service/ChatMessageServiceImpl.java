package com.hsp.fituchat.service;

import com.hsp.fituchat.document.ChatMessageDocument;
import com.hsp.fituchat.dto.ChatMessageRequestDTO;
import com.hsp.fituchat.dto.ChatRoomMessageResponseDTO;
import com.hsp.fituchat.messaging.ChatBrokerMessage;
import com.hsp.fituchat.messaging.ChatMessagePersistBuffer;
import com.hsp.fituchat.messaging.MessageBrokerPort;
import com.hsp.fituchat.repository.ChatMessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final MessageBrokerPort messageBrokerPort;
    private final ChatMessagePersistBuffer chatMessagePersistBuffer;

    private final Counter messagesSentCounter;
    private final Timer messageSendTimer;

    public ChatMessageServiceImpl(
            ChatMessageRepository chatMessageRepository,
            MessageBrokerPort messageBrokerPort,
            ChatMessagePersistBuffer chatMessagePersistBuffer,
            MeterRegistry meterRegistry) {
        this.chatMessageRepository = chatMessageRepository;
        this.messageBrokerPort = messageBrokerPort;
        this.chatMessagePersistBuffer = chatMessagePersistBuffer;

        this.messagesSentCounter = meterRegistry.counter("chat.messages.sent");
        this.messageSendTimer = meterRegistry.timer("chat.message.send.duration");
    }

    @Override
    public void sendMessage(ChatMessageRequestDTO message, long userId) {
        messageSendTimer.record(() -> doSendMessage(message, userId));
    }

    private void doSendMessage(ChatMessageRequestDTO message, long userId) {
        LocalDateTime sendTime = LocalDateTime.now();
        String senderName = message.getSenderName() != null ? message.getSenderName() : "알 수 없음";

        // 1. Redis Pub/Sub으로 실시간 메시지 전달
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

        // 2. MongoDB 저장을 Redis Stream에 위임 (비동기)
        try {
            chatMessagePersistBuffer.enqueue(message.getRoomId(), userId, message.getMessage(), sendTime);
        } catch (Exception e) {
            log.warn("메시지 영구 저장 큐 추가 실패. roomId={}, senderId={}", message.getRoomId(), userId, e);
        }

        messagesSentCounter.increment();
    }

    @Override
    public ChatRoomMessageResponseDTO getChatRoomMessages(String chatRoomId, LocalDateTime before, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessageDocument> messages;

        if (before != null) {
            messages = chatMessageRepository.findByChatRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    chatRoomId, before, pageable);
        } else {
            messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable);
        }

        List<ChatMessageDocument> reversed = new ArrayList<>(messages);
        Collections.reverse(reversed);

        return ChatRoomMessageResponseDTO.builder()
                .messages(reversed.stream().map(doc -> new com.hsp.fituchat.dto.ChatMessage(
                        null, // senderName — 프론트에서 senderId로 표시
                        doc.getContent(),
                        null, // senderProfileUrl
                        doc.getCreatedAt(),
                        doc.getSenderId()
                )).toList()).build();
    }

    @Override
    public ChatRoomMessageResponseDTO getChatRoomMessageAfter(String chatRoomId, LocalDateTime after) {
        List<ChatMessageDocument> messages =
                chatMessageRepository.findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(chatRoomId, after);

        return ChatRoomMessageResponseDTO.builder()
                .messages(messages.stream().map(doc -> new com.hsp.fituchat.dto.ChatMessage(
                        null,
                        doc.getContent(),
                        null,
                        doc.getCreatedAt(),
                        doc.getSenderId()
                )).toList()).build();
    }
}
