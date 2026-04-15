package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_user_button")
public class SysUserButton {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long buttonId;
}
