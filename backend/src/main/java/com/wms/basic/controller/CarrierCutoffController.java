package com.wms.basic.controller;

import com.wms.basic.entity.CarrierCutoff;
import com.wms.basic.mapper.CarrierCutoffMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/carrier-cutoff")
public class CarrierCutoffController extends BaseCrudController<CarrierCutoff, CarrierCutoffMapper> {
    public CarrierCutoffController() {
        super(CarrierCutoff.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"carrier", "warehouse_code"};
    }

    @Override
    protected void beforeSave(CarrierCutoff entity) {
        if (entity.getWarehouseCode() == null || entity.getCarrier() == null || entity.getCutoffHour() == null) {
            throw new BizException("仓库、承运商和截单小时不能为空");
        }
        if (entity.getCutoffHour() < 0 || entity.getCutoffHour() > 23) {
            throw new BizException("截单小时必须在 0 到 23 之间");
        }
    }
}
