package com.wms.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色: 一组权限码(逗号分隔, 见 com.wms.system.auth.Permission)。内置角色 ADMIN/OPERATOR/VIEWER 不可删除, ADMIN 权限固定为全部。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_role")
public class Role extends BaseEntity {
    private String code;
    private String name;
    /** 逗号分隔的权限码; "*" 表示全部 */
    private String perms;
    private Boolean builtin;
    private Integer status;
    private String remark;
}
