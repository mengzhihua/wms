package com.wms.basic.controller;

import com.wms.basic.entity.Lpn;
import com.wms.basic.mapper.LpnMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/lpn")
public class LpnController extends BaseCrudController<Lpn, LpnMapper> {
    public LpnController() {
        super(Lpn.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "location_code", "warehouse_code"};
    }

    @Override
    protected void beforeSave(Lpn entity) {
        if (entity.getCode() == null || entity.getWarehouseCode() == null) {
            throw new BizException("容器编码和仓库不能为空");
        }
        if (entity.getType() == null || entity.getType().isEmpty()) {
            entity.setType("PALLET");
        }
        if (entity.getStatus() == null || entity.getStatus().isEmpty()) {
            entity.setStatus("OPEN");
        }
    }
}
