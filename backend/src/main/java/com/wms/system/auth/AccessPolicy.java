package com.wms.system.auth;

import com.wms.system.entity.User;

/**
 * 角色访问策略（按 HTTP 方法 + 路径判断）：
 * <ul>
 *   <li>任何登录用户可读（GET）</li>
 *   <li>VIEWER 不可写</li>
 *   <li>OPERATOR 可做仓库作业，不可维护基础数据(/api/basic/**)与用户(/api/system/**)</li>
 *   <li>ADMIN 无限制</li>
 * </ul>
 * /api/auth/** 属于登录用户自助操作（改密、登出），所有角色均可。
 */
public final class AccessPolicy {
    private AccessPolicy() {
    }

    public static boolean allows(String role, String method, String path) {
        if (User.ADMIN.equals(role)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (User.OPERATOR.equals(role)) {
            return !path.startsWith("/api/basic/") && !path.startsWith("/api/system/");
        }
        return false;
    }
}
