package com.wms.report;

/** 计件班次。早班 8 点到 16 点，中班 16 点到 24 点，其余为晚班。 */
public final class LaborShift {
    private LaborShift() {}

    public static String code(int hour) {
        int h = hour % 24;
        if (h < 0) {
            h += 24;
        }
        if (h >= 8 && h < 16) {
            return "MORNING";
        }
        if (h >= 16) {
            return "AFTERNOON";
        }
        return "NIGHT";
    }

    public static String label(String code) {
        if ("MORNING".equals(code)) {
            return "早班";
        }
        if ("AFTERNOON".equals(code)) {
            return "中班";
        }
        return "晚班";
    }
}
