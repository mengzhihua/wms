package com.wms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** VMI 协议。transferPoint: RECEIPT 收货买断 / SHIP 发运转移 / CONSUMPTION 消耗转移。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_vmi_agreement")
public class VmiAgreement extends BaseEntity {
    private String supplierCode;
    private String ownerCode;
    private String warehouseCode;
    private String itemCode;
    private BigDecimal minQty;
    private BigDecimal maxQty;
    private BigDecimal reorderPoint;
    private Integer leadTimeDays;
    private String transferPoint;
    private Integer status;
}
