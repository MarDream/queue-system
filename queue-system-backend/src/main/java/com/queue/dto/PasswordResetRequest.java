package com.queue.dto;

import lombok.Data;

@Data
public class PasswordResetRequest {
    private String email;
    private String username;
}
