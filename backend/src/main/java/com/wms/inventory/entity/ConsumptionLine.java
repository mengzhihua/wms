package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_consumption_line")
public class ConsumptionLine extends BaseEntity {
    private Long consumptionId;
    private Integer lineNo;
    private String itemCode;
    private BigDecimal qty;
}
