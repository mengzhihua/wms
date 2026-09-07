package com.wms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 写操作审计：谁在何时调用了哪个接口 */
@Data
@TableName("wms_op_log")
public class OpLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String method;
    private String path;
    private String query;
    private Integer httpStatus;
    private Integer costMs;
    private String clientIp;
    private LocalDateTime createdAt;
}
