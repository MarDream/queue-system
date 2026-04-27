package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long regionId;             // 所属区域ID
    private String ticketNo;
    private Long businessTypeId;
    private String source;
    private String phone;
    private String name;
    private String status;
    private Long counterId;
    private LocalDateTime calledAt;
    private LocalDateTime servedAt;
    private LocalDateTime completedAt;
    private String skipType; // 过号来源类型：manual=人工跳过，system=系统过号
    private LocalDateTime reactivatedAt; // 重新激活时间，用于排序优先叫号
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
