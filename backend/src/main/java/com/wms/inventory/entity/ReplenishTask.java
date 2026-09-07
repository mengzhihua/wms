package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 拣货位补货任务：存储位 -> 拣货位 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_replenish_task")
public class ReplenishTask extends BaseEntity {
    private String code;
    private String warehouseCode;
    private String ownerCode;
    private String itemCode;
    private String lotNo;
    private Long inventoryId;
    private String fromLocation;
    private String toLocation;
    private BigDecimal qty;
    /** NEW / DONE / CANCELLED */
    private String status;
    private String remark;
}
