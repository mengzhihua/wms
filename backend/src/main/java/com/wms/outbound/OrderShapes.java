package com.wms.outbound;

import com.wms.outbound.entity.ShipOrderLine;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 出库单订单结构：单品单件、一品两件、一品多件、多品。 */
public final class OrderShapes {
    public static final String SISQ = "SISQ";
    public static final String SIFQ = "SIFQ";
    public static final String SIW = "SIW";
    public static final String MULTI = "MULTI";

    private OrderShapes() {
    }

    public static String of(List<ShipOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return MULTI;
        }
        Set<String> skus = new HashSet<>();
        BigDecimal qty = BigDecimal.ZERO;
        for (ShipOrderLine line : lines) {
            if (line.getItemCode() != null && !line.getItemCode().isEmpty()) {
                skus.add(line.getItemCode());
            }
            if (line.getOrderQty() != null) {
                qty = qty.add(line.getOrderQty());
            }
        }
        if (skus.size() != 1) {
            return MULTI;
        }
        if (qty.compareTo(BigDecimal.ONE) == 0) {
            return SISQ;
        }
        if (qty.compareTo(new BigDecimal("2")) == 0) {
            return SIFQ;
        }
        return SIW;
    }
}
