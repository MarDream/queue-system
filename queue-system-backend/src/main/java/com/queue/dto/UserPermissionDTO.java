package com.queue.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserPermissionDTO {
    private Long userId;
    private List<Long> menuIds;
    private List<Long> buttonIds;
}
