package com.wms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 承运商每日截单小时。尾单策略优先用这里的时刻，没有则用策略上的 cutoffHour。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_carrier_cutoff")
public class CarrierCutoff extends BaseEntity {
    private String warehouseCode;
    private String carrier;
    private Integer cutoffHour;
}
