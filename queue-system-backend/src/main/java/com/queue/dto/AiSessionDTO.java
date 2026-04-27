package com.queue.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSessionDTO {
    private String id;
    private String workspace;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

