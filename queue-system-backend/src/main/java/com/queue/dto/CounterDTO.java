package com.queue.dto;

import com.queue.entity.BusinessType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CounterDTO {
    private Long id;
    private Long regionId;
    private Integer number;
    private String name;
    private String status;
    private String operatorName;
    private List<Long> businessTypeIds;
    private List<BusinessType> businessTypes;
    private List<Long> operatorIds;
    private List<String> operatorNames;
}
