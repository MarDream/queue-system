package com.queue.dto;

import lombok.Data;

@Data
public class SysUserDTO {
    private Long id;
    private String username;
    private String password;
    private String name;
    private String role;
    private Long regionId;
    private String regionCode;
    private Integer status;
}
