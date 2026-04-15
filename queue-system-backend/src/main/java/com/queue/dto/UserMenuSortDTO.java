package com.queue.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserMenuSortDTO {
    private Long userId;
    private List<Long> menuIds;
}
