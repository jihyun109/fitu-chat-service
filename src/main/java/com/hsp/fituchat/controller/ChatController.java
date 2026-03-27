package com.hsp.fituchat.controller;

import com.hsp.fituchat.dto.ChatRoomCreateRequestDTO;
import com.hsp.fituchat.dto.ChatRoomCreateResponseDTO;
import com.hsp.fituchat.dto.ChatRoomListResponseDTO;
import com.hsp.fituchat.dto.ChatRoomMessageResponseDTO;
import com.hsp.fituchat.jwt.JwtUtil;
import com.hsp.fituchat.service.ChatMessageService;
import com.hsp.fituchat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/chat")
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final JwtUtil jwtUtil;

    @PostMapping("/room")
    public ResponseEntity<ChatRoomCreateResponseDTO> createChatRoom(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChatRoomCreateRequestDTO request) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(chatRoomService.createChatRoom(userId, request));
    }

    @GetMapping("/room/list")
    public ResponseEntity<ChatRoomListResponseDTO> getChatRoomList(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(chatRoomService.getChatRoomList(userId));
    }

    @GetMapping("/message/{chatRoomId}")
    public ResponseEntity<ChatRoomMessageResponseDTO> getChatMessage(
            @PathVariable String chatRoomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "50") int limit) {

        if (after != null) {
            return ResponseEntity.ok(chatMessageService.getChatRoomMessageAfter(chatRoomId, after));
        }
        return ResponseEntity.ok(chatMessageService.getChatRoomMessages(chatRoomId, before, limit));
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return jwtUtil.getUserId(token);
    }
}
