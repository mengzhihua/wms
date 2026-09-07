package com.wms.outbound.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_ship_order")
public class ShipOrder extends BaseEntity {
    private String code;
    private String warehouseCode;
    private String ownerCode;
    private String customerCode;
    private String type;
    private String status;
    private Integer priority;
    private LocalDate expectedShipDate;
    private String externalNo;
    private String carrier;
    private String address;
    private String remark;
    private BigDecimal totalQty;
    private BigDecimal allocatedQty;
    private BigDecimal pickedQty;
    private BigDecimal shippedQty;
    private String trackingNo;
    private String cartonCode;
    private Integer packageCount;
    private BigDecimal grossWeight;
    private LocalDateTime packedAt;
    private LocalDateTime shippedAt;

    @TableField(exist = false)
    private List<ShipOrderLine> lines;
}
