package com.hsp.fituchat.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class ChatRoomCreateRequestDTO {
    private String name;
    private List<Long>  memberIds;
}
