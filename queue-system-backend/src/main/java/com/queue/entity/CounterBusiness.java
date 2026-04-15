package com.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("counter_business")
public class CounterBusiness {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long counterId;
    private Long businessTypeId;
}
