package com.wms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 月台预约。refType 为 ASN 或 SHIP，状态 BOOKED / ARRIVED / WORKING / LEFT。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_appointment")
public class Appointment extends BaseEntity {
    private String code;
    private String warehouseCode;
    private String dockCode;
    private String refType;
    private String refCode;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String status;
    private String remark;
}
