package com.queue.dto;

import lombok.Data;

@Data
public class AiAskRequest {
    private String workspace; // admin | counter
    private String sessionId;
    private String question;
    private Long regionId;
    private Long businessTypeId;
    private Long counterId;
    private Integer limit;
}
