package com.wms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 包材/箱型：用于打包箱型推荐；关联包材 SKU 时打包会扣减包材库存（辅材商品化）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_carton")
public class Carton extends BaseEntity {
    private String code;
    private String name;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
    /** 内容积 m³，未填时按长宽高计算 */
    private BigDecimal volume;
    /** 最大承重 kg */
    private BigDecimal maxWeight;
    /** 关联包材库存 SKU（可选） */
    private String ownerCode;
    private String itemCode;
    private Integer status;
}
