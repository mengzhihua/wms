package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_inventory")
public class Inventory extends BaseEntity {
    private String warehouseCode;
    private String locationCode;
    private String ownerCode;
    private String itemCode;
    private String lotNo;
    private BigDecimal qty;
    private BigDecimal allocatedQty;
    private String status;
    private LocalDate receiveDate;
    private LocalDate expiryDate;
    private String refNo;
    /** 盘点锁定: 锁定期间不可分配/移库/调整 */
    private Boolean countLock;
    private Long countPlanId;
    /** 容器编码。空字符串表示散货 */
    private String lpnCode;
    /** CUSTOMER 货主已有 / VENDOR 供应商寄售 */
    private String ownership;

    public BigDecimal getAvailableQty() {
        if (!"AVAILABLE".equals(status) || qty == null || Boolean.TRUE.equals(countLock)) {
            return BigDecimal.ZERO;
        }
        return qty.subtract(allocatedQty == null ? BigDecimal.ZERO : allocatedQty);
    }
}
