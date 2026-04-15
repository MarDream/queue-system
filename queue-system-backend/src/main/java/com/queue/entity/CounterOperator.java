package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("counter_operator")
public class CounterOperator {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long counterId;
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
