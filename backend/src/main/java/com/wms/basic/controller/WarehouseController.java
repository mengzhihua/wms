package com.wms.basic.controller;

import com.wms.basic.entity.Warehouse;
import com.wms.basic.mapper.WarehouseMapper;
import com.wms.basic.service.WarehouseProfileService;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/warehouse")
public class WarehouseController extends BaseCrudController<Warehouse, WarehouseMapper> {
    public WarehouseController() {
        super(Warehouse.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name"};
    }

    @Override
    protected void beforeSave(Warehouse entity) {
        String raw = entity.getProfile();
        if (raw == null || raw.trim().isEmpty()) {
            entity.setProfile(WarehouseProfileService.GENERAL);
            return;
        }
        String profile = raw.trim().toUpperCase();
        if (!WarehouseProfileService.GENERAL.equals(profile)
                && !WarehouseProfileService.FMCG.equals(profile)
                && !WarehouseProfileService.BULKY.equals(profile)
                && !WarehouseProfileService.VMI.equals(profile)) {
            throw new BizException("仓型只能是 GENERAL、FMCG、BULKY、VMI");
        }
        entity.setProfile(profile);
    }
}
