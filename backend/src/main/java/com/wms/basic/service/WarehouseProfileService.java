package com.wms.basic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Warehouse;
import com.wms.basic.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 仓型解析。未维护或无法识别时按一般仓，行为与引入仓型之前一致。 */
@Service
@RequiredArgsConstructor
public class WarehouseProfileService {
    public static final String GENERAL = "GENERAL";
    public static final String FMCG = "FMCG";
    public static final String BULKY = "BULKY";
    public static final String VMI = "VMI";

    private final WarehouseMapper warehouseMapper;

    public String profile(String warehouseCode) {
        if (warehouseCode == null || warehouseCode.isEmpty()) {
            return GENERAL;
        }
        Warehouse wh = warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getCode, warehouseCode).last("LIMIT 1"));
        return normalize(wh == null ? null : wh.getProfile());
    }

    public static String normalize(String profile) {
        if (profile == null || profile.trim().isEmpty()) {
            return GENERAL;
        }
        String value = profile.trim().toUpperCase();
        if (FMCG.equals(value) || BULKY.equals(value) || VMI.equals(value) || GENERAL.equals(value)) {
            return value;
        }
        return GENERAL;
    }

    public boolean isFmcg(String warehouseCode) {
        return FMCG.equals(profile(warehouseCode));
    }

    public boolean isBulky(String warehouseCode) {
        return BULKY.equals(profile(warehouseCode));
    }

    public boolean isVmi(String warehouseCode) {
        return VMI.equals(profile(warehouseCode));
    }
}
