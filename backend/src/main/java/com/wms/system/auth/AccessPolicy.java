package com.wms.system.auth;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 基于权限码的访问策略（按 HTTP 方法 + 路径判断）：
 * <ul>
 *   <li>任何登录用户可读（GET/HEAD）, /api/auth/** 自助操作所有人可用</li>
 *   <li>写操作映射到所需权限码, 见 {@link #requiredPermission(String, String)}</li>
 *   <li>持有 "*" 的角色不受限制</li>
 * </ul>
 */
public final class AccessPolicy {
    private static final Pattern ASN_APPROVAL = Pattern.compile("/api/inbound/asn/\\d+/(approve|reject)");
    private static final Pattern ORDER_APPROVAL = Pattern.compile("/api/outbound/order/\\d+/(approve|reject)");
    private AccessPolicy() {
    }

    public static boolean allows(Set<String> perms, String method, String path) {
        String required = requiredPermission(method, path);
        if (required == null) {
            return true;
        }
        return perms != null && (perms.contains(Permission.ALL) || perms.contains(required));
    }

    /** 该请求所需权限码; 无需权限时返回 null */
    public static String requiredPermission(String method, String path) {
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return null;
        }
        if (path.startsWith("/api/auth/")) {
            return null;
        }
        if (path.startsWith("/api/basic/")) {
            return Permission.BASIC_WRITE;
        }
        if (path.startsWith("/api/system/")) {
            return Permission.SYSTEM_WRITE;
        }
        if (path.startsWith("/api/outbound/wave-strategy")) {
            return path.equals("/api/outbound/wave-strategy/run") ? Permission.STRATEGY_RUN : Permission.STRATEGY_WRITE;
        }
        if (path.startsWith("/api/inbound/")) {
            return ASN_APPROVAL.matcher(path).matches() ? Permission.INBOUND_APPROVE : Permission.INBOUND_WRITE;
        }
        if (path.startsWith("/api/outbound/")) {
            return ORDER_APPROVAL.matcher(path).matches() ? Permission.OUTBOUND_APPROVE : Permission.OUTBOUND_WRITE;
        }
        if (path.startsWith("/api/inventory/")) {
            return Permission.INVENTORY_WRITE;
        }
        if (path.startsWith("/api/report/")) {
            return Permission.REPORT_WRITE;
        }
        return Permission.ALL;
    }
}
