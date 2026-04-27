package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_user_menu_sort")
public class SysUserMenuSort {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long menuId;
    private Integer sortOrder;
}
