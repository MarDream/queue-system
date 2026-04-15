package com.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TakeTicketRequest {
    // 区域ID可选，不传则从业务类型关联的区域中获取
    private Long regionId;

    @NotNull(message = "业务类型不能为空")
    private Long businessTypeId;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "姓名不能为空")
    private String name;
}
