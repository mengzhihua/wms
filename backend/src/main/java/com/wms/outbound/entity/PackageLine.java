package com.wms.outbound.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_package_line")
public class PackageLine extends BaseEntity {
    private Long packageId;
    private String itemCode;
    private String lotNo;
    private BigDecimal qty;
}
