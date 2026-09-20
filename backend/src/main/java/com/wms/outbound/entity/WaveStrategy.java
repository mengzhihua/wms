package com.wms.outbound.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 波次策略: 按优先级依次扫描已分配出库单, 满足约束的订单分组成波, 超过上限自动拆波 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_wave_strategy")
public class WaveStrategy extends BaseEntity {
    private String code;
    private String name;
    /** 数字越小越先匹配 */
    private Integer priority;
    private Integer minOrders;
    private Integer maxOrders;
    /** 单订单 SKU 品项数范围, 0 表示不限 */
    private Integer minSkuPerOrder;
    private Integer maxSkuPerOrder;
    /** 单订单总件数范围, 0 表示不限 */
    private BigDecimal minQtyPerOrder;
    private BigDecimal maxQtyPerOrder;
    /** 波次 SKU 品项数上限, 0 不限 */
    private Integer maxSkuItems;
    /** 波次总件数上限, 0 不限 */
    private BigDecimal maxTotalQty;
    /** 同 SKU 才能进同一波次 */
    private Boolean groupByItem;
    private Boolean groupByOwner;
    private Boolean groupByCarrier;
    /** 限定订单库存全部位于该库区 */
    private String zoneCode;
    /** 尾单策略: 仅当日该小时之后才执行 */
    private Integer cutoffHour;
    /** ONE_ORDER_ONE_PACKAGE / SPLIT_BY_WEIGHT */
    private String packStrategy;
    private BigDecimal maxPackageWeight;
    private Boolean enabled;
    private String remark;
}
