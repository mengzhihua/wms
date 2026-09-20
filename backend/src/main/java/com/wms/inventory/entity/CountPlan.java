package com.wms.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_count_plan")
public class CountPlan extends BaseEntity {
    private String code;
    private String name;
    private String warehouseCode;
    /** CYCLE 循环 / MOVEMENT 动碰 / RANDOM 随机抽盘 / ABNORMAL 异动 */
    private String type;
    /** WAREHOUSE / ZONE / ITEM */
    private String scopeType;
    private String zoneCode;
    private String itemCodes;
    private String abcClasses;
    private LocalDate sinceDate;
    private Integer samplePercent;
    /** DRAFT -> PENDING -> APPROVED -> EXECUTING -> COMPLETED / CANCELLED */
    private String status;
    private String approver;
    private String approveOpinion;
    private Integer taskCount;
    private Integer doneCount;
    private Integer diffCount;
    private String remark;
    private String createdBy;
}
