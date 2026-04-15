package com.queue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelTicketRequest {
    @NotBlank(message = "票号不能为空")
    private String ticketNo;
}
