package com.wms.system.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 权限码目录。所有登录用户可读(GET); 写操作按模块划分, 审核单独授权。 */
public final class Permission {
    public static final String ALL = "*";
    public static final String BASIC_WRITE = "basic:write";
    public static final String INBOUND_WRITE = "inbound:write";
    public static final String INBOUND_APPROVE = "inbound:approve";
    public static final String OUTBOUND_WRITE = "outbound:write";
    public static final String OUTBOUND_APPROVE = "outbound:approve";
    public static final String STRATEGY_RUN = "strategy:run";
    public static final String STRATEGY_WRITE = "strategy:write";
    public static final String INVENTORY_WRITE = "inventory:write";
    public static final String REPORT_WRITE = "report:write";
    public static final String SYSTEM_WRITE = "system:write";

    private static final Map<String, String> LABELS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(BASIC_WRITE, "基础数据维护");
        m.put(INBOUND_WRITE, "入库作业");
        m.put(INBOUND_APPROVE, "入库单审核");
        m.put(OUTBOUND_WRITE, "出库作业");
        m.put(OUTBOUND_APPROVE, "出库单审核");
        m.put(STRATEGY_RUN, "执行波次策略");
        m.put(STRATEGY_WRITE, "维护波次策略");
        m.put(INVENTORY_WRITE, "库内作业");
        m.put(REPORT_WRITE, "报表维护(ABC应用等)");
        m.put(SYSTEM_WRITE, "用户/角色维护");
        LABELS = Collections.unmodifiableMap(m);
    }

    /** 内置作业员角色的权限 */
    public static final List<String> OPERATOR_DEFAULTS = Collections.unmodifiableList(Arrays.asList(
            INBOUND_WRITE, OUTBOUND_WRITE, STRATEGY_RUN, INVENTORY_WRITE, REPORT_WRITE));

    private Permission() {
    }

    public static Map<String, String> labels() {
        return LABELS;
    }

    public static boolean isKnown(String code) {
        return ALL.equals(code) || LABELS.containsKey(code);
    }
}
