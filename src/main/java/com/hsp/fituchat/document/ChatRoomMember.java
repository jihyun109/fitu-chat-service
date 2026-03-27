package com.hsp.fituchat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방 멤버 정보 (ChatRoomDocument에 embedded).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMember {

    private Long userId;
    private String name;
    private String profileUrl;
}
