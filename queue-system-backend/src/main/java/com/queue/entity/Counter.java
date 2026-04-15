package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("counter")
public class Counter {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long regionId;             // 所属区域ID
    private Integer number;
    private String name;
    private String status;
    private Long currentTicketId;
    private String operatorName;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
