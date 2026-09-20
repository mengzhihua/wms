package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_stock_adjust_line")
public class StockAdjustLine extends BaseEntity {
    private Long adjustId;
    private Long inventoryId;
    private String locationCode;
    private String ownerCode;
    private String itemCode;
    private String lotNo;
    private BigDecimal fromQty;
    private BigDecimal toQty;
    private BigDecimal diffQty;
    private String reason;
}
