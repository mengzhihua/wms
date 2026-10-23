package com.wms.basic.controller;

import com.wms.basic.entity.PickSlot;
import com.wms.basic.mapper.PickSlotMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/pick-slot")
public class PickSlotController extends BaseCrudController<PickSlot, PickSlotMapper> {
    public PickSlotController() {
        super(PickSlot.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"item_code", "location_code", "warehouse_code"};
    }

    @Override
    protected void beforeSave(PickSlot entity) {
        if (entity.getWarehouseCode() == null || entity.getOwnerCode() == null
                || entity.getItemCode() == null || entity.getLocationCode() == null) {
            throw new BizException("仓库、货主、物料和拣货位不能为空");
        }
    }
}
