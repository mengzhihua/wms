package com.wms.report;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 作业节点单价（元/件）：收货 0.20，上架 0.30，拣货 0.50，发运 0.40，补货 0.35，质检拒收 0.25。 */
public final class LaborWage {
    private LaborWage() {}

    public static BigDecimal rate(String txnType) {
        if ("RECEIVE".equals(txnType)) {
            return new BigDecimal("0.20");
        }
        if ("PUTAWAY".equals(txnType)) {
            return new BigDecimal("0.30");
        }
        if ("PICK".equals(txnType)) {
            return new BigDecimal("0.50");
        }
        if ("SHIP".equals(txnType)) {
            return new BigDecimal("0.40");
        }
        if ("REPLENISH".equals(txnType)) {
            return new BigDecimal("0.35");
        }
        if ("QC_REJECT".equals(txnType)) {
            return new BigDecimal("0.25");
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal pay(String txnType, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return rate(txnType).multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }
}
