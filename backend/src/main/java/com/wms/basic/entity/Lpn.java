package com.wms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 容器。PALLET 托盘 / CASE 整箱 / TOTE 周转箱。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_lpn")
public class Lpn extends BaseEntity {
    private String code;
    private String warehouseCode;
    private String type;
    private String parentCode;
    private String locationCode;
    private String ownerCode;
    private String status;
}
