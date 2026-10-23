package com.wms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_dock")
public class Dock extends BaseEntity {
    private String warehouseCode;
    private String code;
    private String name;
    private Integer status;
}
