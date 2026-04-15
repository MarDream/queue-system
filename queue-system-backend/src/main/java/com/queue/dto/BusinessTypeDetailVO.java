package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessTypeDetailVO {
    private String regionName;
    private Integer counterNumber;
    private String counterName;
    private String operatorName;
    private Long ticketCount;
}
