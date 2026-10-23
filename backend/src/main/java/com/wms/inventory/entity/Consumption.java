package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_consumption")
public class Consumption extends BaseEntity {
    private String code;
    private String warehouseCode;
    private String ownerCode;
    private String supplierCode;
    private String status;
    private BigDecimal totalQty;
    private String remark;

    @TableField(exist = false)
    private List<ConsumptionLine> lines;
}
