package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_user")
public class AdminUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String name;
    private String role;
    private Long regionId;
    private String regionCode;         // 冗余，方便查询

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
