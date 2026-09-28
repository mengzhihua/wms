package com.wms.basic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Warehouse;
import com.wms.basic.mapper.WarehouseMapper;
import com.wms.common.ApprovalStatus;
import com.wms.common.BizException;
import com.wms.system.auth.CurrentUser;
import com.wms.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 单据审核门禁: 仓库开启 approvalRequired 时, 入库单/出库单新建或修改后进入 PENDING, 审核通过后才可收货/分配 */
@Service
@RequiredArgsConstructor
public class ApprovalService {
    private final WarehouseMapper warehouseMapper;

    public boolean required(String warehouseCode) {
        Warehouse wh = warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getCode, warehouseCode).last("LIMIT 1"));
        return wh != null && Boolean.TRUE.equals(wh.getApprovalRequired());
    }

    /** 新建/修改后的初始审核状态 */
    public String initialStatus(String warehouseCode) {
        return required(warehouseCode) ? ApprovalStatus.PENDING : ApprovalStatus.NONE;
    }

    /** 未放行时抛出业务异常 */
    public void requireReleased(String docName, String code, String status) {
        if (!ApprovalStatus.released(status)) {
            throw new BizException(docName + " " + code + " 尚未审核通过(当前: " + label(status) + "), 不可作业");
        }
    }

    /** 审核/驳回时校验当前状态为待审核, 返回操作人 */
    public String requirePending(String docName, String code, String status) {
        if (!ApprovalStatus.PENDING.equals(status)) {
            throw new BizException(docName + " " + code + " 不在待审核状态(当前: " + label(status) + ")");
        }
        User u = CurrentUser.get();
        return u == null ? "system" : u.getUsername();
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static String label(String status) {
        if (status == null || ApprovalStatus.NONE.equals(status)) {
            return "无需审核";
        }
        switch (status) {
            case ApprovalStatus.PENDING:
                return "待审核";
            case ApprovalStatus.APPROVED:
                return "已审核";
            case ApprovalStatus.REJECTED:
                return "已驳回";
            default:
                return status;
        }
    }
}
