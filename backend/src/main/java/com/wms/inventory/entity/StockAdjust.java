package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_stock_adjust")
public class StockAdjust extends BaseEntity {
    private String code;
    private Long planId;
    private String planCode;
    private String warehouseCode;
    /** COUNT 盘点 / MANUAL 手工 */
    private String source;
    /** PENDING -> APPROVED / REJECTED */
    private String status;
    private Integer lineCount;
    private BigDecimal gainQty;
    private BigDecimal lossQty;
    private String approver;
    private String approveOpinion;
    private String remark;
}
