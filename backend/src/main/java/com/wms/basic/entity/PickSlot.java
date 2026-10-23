package com.wms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 快消固定拣货位。一个仓库、货主、物料只绑一个拣货位。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_pick_slot")
public class PickSlot extends BaseEntity {
    private String warehouseCode;
    private String ownerCode;
    private String itemCode;
    private String locationCode;
    private BigDecimal minQty;
    private BigDecimal maxQty;
}
