package com.wms.outbound.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_package")
public class Package extends BaseEntity {
    private String code;
    private Long orderId;
    private String orderCode;
    private Long waveId;
    private Integer seqNo;
    /** ONE_ORDER_ONE_PACKAGE / SPLIT_BY_WEIGHT / MANUAL */
    private String type;
    private String cartonCode;
    private BigDecimal weight;
    private String carrier;
    private String trackingNo;
    /** NEW -> SHIPPED / CANCELLED */
    private String status;
    private String remark;
    @TableField(exist = false)
    private List<PackageLine> lines;
}
