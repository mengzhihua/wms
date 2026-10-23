package com.wms.integration.client;

import com.wms.inventory.entity.Inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 寄售库龄负债。只统计供应商物权，不进按件操作费。 */
public final class ConsignmentLiability {
    private ConsignmentLiability() {
    }

    public static List<Map<String, Object>> rows(List<Inventory> stock, LocalDate today) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (stock == null || today == null) {
            return out;
        }
        for (Inventory inv : stock) {
            if (inv == null || inv.getQty() == null || inv.getQty().signum() <= 0) {
                continue;
            }
            String ownership = inv.getOwnership() == null || inv.getOwnership().isEmpty() ? "CUSTOMER" : inv.getOwnership();
            if (!"VENDOR".equals(ownership)) {
                continue;
            }
            long age = inv.getReceiveDate() == null ? 0 : ChronoUnit.DAYS.between(inv.getReceiveDate(), today);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("warehouseCode", inv.getWarehouseCode());
            row.put("ownerCode", inv.getOwnerCode());
            row.put("itemCode", inv.getItemCode());
            row.put("locationCode", inv.getLocationCode());
            row.put("lotNo", inv.getLotNo());
            row.put("qty", inv.getQty());
            row.put("receiveDate", inv.getReceiveDate());
            row.put("ageDays", age);
            row.put("chargeItemCode", "VMI_STORAGE");
            row.put("statedAmount", inv.getQty().multiply(BigDecimal.valueOf(age)));
            out.add(row);
        }
        return out;
    }
}
