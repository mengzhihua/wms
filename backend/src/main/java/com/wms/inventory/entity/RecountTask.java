package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_recount_task")
public class RecountTask extends BaseEntity {
    private Long planId;
    private Integer roundNo;
    private Long countTaskId;
    private Long inventoryId;
    private String locationCode;
    private String ownerCode;
    private String itemCode;
    private String lotNo;
    private BigDecimal systemQty;
    private BigDecimal firstQty;
    private BigDecimal recountQty;
    private BigDecimal recountDiff;
    private BigDecimal finalQty;
    private BigDecimal finalDiff;
    /** MATCH 一致 / MISMATCH 不一致 */
    private String finalResult;
    private String assignee;
    /** PENDING -> RECOUNTED -> CONFIRMED */
    private String status;
}
