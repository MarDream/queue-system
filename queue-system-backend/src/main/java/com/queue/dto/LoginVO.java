package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {
    private Long userId;
    private String username;
    private String name;
    private String role;
    private Long regionId;
    private String regionCode;
    private String regionName;  // 区域名称
    private String token;
    private List<String> menuPaths;
    private List<String> buttonCodes;
}
