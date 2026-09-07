package com.wms.inbound.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 收货质检任务：库存冻结在质检位，放行后生成上架任务，拒收部分扣减出库 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_qc_task")
public class QcTask extends BaseEntity {
    private String code;
    private Long asnId;
    private Long asnLineId;
    private String asnCode;
    private String warehouseCode;
    private String ownerCode;
    private String itemCode;
    private String lotNo;
    private Long inventoryId;
    private String locationCode;
    private BigDecimal qty;
    private BigDecimal passQty;
    private BigDecimal rejectQty;
    private String rejectReason;
    private String inspector;
    /** NEW / DONE */
    private String status;
}
