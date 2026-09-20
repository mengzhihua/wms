package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_count_task")
public class CountTask extends BaseEntity {
    private Long planId;
    private String planCode;
    private Long inventoryId;
    private String warehouseCode;
    private String locationCode;
    private String ownerCode;
    private String itemCode;
    private String lotNo;
    private BigDecimal systemQty;
    private BigDecimal countQty;
    private BigDecimal diffQty;
    private String assignee;
    /** PENDING -> CLAIMED -> DONE / CANCELLED */
    private String status;
    private LocalDateTime countedAt;
}
