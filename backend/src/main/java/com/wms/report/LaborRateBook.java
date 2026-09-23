package com.wms.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** 仓库 + 货主 + 作业节点的单价覆盖。没有覆盖时用默认单价。 */
public final class LaborRateBook {
    private LaborRateBook() {}

    public static String key(String warehouse, String owner, String txnType) {
        return text(warehouse) + "|" + text(owner) + "|" + text(txnType);
    }

    public static BigDecimal resolve(Map<String, BigDecimal> overrides, String warehouse, String owner, String txnType) {
        if (overrides != null && !blank(warehouse) && !blank(owner)) {
            BigDecimal hit = overrides.get(key(warehouse, owner, txnType));
            if (hit != null) {
                return hit;
            }
        }
        return LaborWage.rate(txnType);
    }

    public static BigDecimal pay(BigDecimal rate, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0 || rate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return rate.multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }

    private static String text(String value) {
        return blank(value) ? "" : value.trim();
    }
}
