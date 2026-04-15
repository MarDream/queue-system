package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessTypeDTO {
    private Long id;
    private String name;
    private String prefix;
    private String description;
    private Integer waitingCount;
}
