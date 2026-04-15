package com.queue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CounterOperationRequest {
    @NotNull(message = "窗口ID不能为空")
    private Long counterId;
}
