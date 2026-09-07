package com.wms.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户。role: ADMIN(全部) / OPERATOR(作业, 不含基础数据与用户维护) / VIEWER(只读)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_user")
public class User extends BaseEntity {
    public static final String ADMIN = "ADMIN";
    public static final String OPERATOR = "OPERATOR";
    public static final String VIEWER = "VIEWER";

    private String username;
    /** 写入时可携带明文密码，响应中永不输出 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String realName;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginAt;
}
