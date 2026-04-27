package com.queue.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiMessageDTO {
    private String id;
    private String role;
    private String text;
    private LocalDateTime createdAt;
}

