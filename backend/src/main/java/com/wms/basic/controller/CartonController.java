package com.wms.basic.controller;

import com.wms.basic.entity.Carton;
import com.wms.basic.mapper.CartonMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/basic/carton")
public class CartonController extends BaseCrudController<Carton, CartonMapper> {
    public CartonController() {
        super(Carton.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name"};
    }

    @Override
    protected void beforeSave(Carton c) {
        if (c.getVolume() == null || c.getVolume().signum() <= 0) {
            if (c.getLengthCm() == null || c.getWidthCm() == null || c.getHeightCm() == null) {
                throw new BizException("请填写内容积或长宽高");
            }
            c.setVolume(c.getLengthCm().multiply(c.getWidthCm()).multiply(c.getHeightCm())
                    .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP));
        }
        if ((c.getItemCode() == null) != (c.getOwnerCode() == null)) {
            throw new BizException("关联包材 SKU 时货主与物料需同时填写");
        }
    }
}
