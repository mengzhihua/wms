package com.wms.common;

/**
 * 单据审核状态(仓库开启 approvalRequired 时生效):
 * NONE(无需审核) / PENDING(待审核) / APPROVED(已审核) / REJECTED(已驳回, 可修改后重新提交)。
 * 旧数据 approval_status 为空视为 NONE。
 */
public final class ApprovalStatus {
    public static final String NONE = "NONE";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private ApprovalStatus() {
    }

    /** 是否已放行(无需审核或已审核通过) */
    public static boolean released(String status) {
        return status == null || NONE.equals(status) || APPROVED.equals(status);
    }
}
