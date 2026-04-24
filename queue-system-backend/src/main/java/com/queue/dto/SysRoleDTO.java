package com.queue.dto;

import lombok.Data;

@Data
public class SysRoleDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
}
