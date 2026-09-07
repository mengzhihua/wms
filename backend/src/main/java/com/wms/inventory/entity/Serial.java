package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 序列号(SN/IMEI)：IN_STOCK 在库 / SHIPPED 已发运。退货入库同一 SN 重新回到 IN_STOCK。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_serial")
public class Serial extends BaseEntity {
    private String ownerCode;
    private String itemCode;
    private String serialNo;
    private String lotNo;
    private String status;
    private String asnCode;
    private String orderCode;
    private String locationCode;
}
