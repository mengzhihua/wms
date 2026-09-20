package com.wms.outbound.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_shortage")
public class Shortage extends BaseEntity {
    private Long taskId;
    private Long orderId;
    private String orderCode;
    private Long waveId;
    private String warehouseCode;
    private String ownerCode;
    private String itemCode;
    private String lotNo;
    private String locationCode;
    private Long inventoryId;
    private BigDecimal qty;
    private String reason;
    private String operator;
    /** OPEN 待处理 / CLOSED 已处理 */
    private String status;
}
