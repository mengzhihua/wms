package com.wms.basic.controller;

import com.wms.basic.entity.Dock;
import com.wms.basic.mapper.DockMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/dock")
public class DockController extends BaseCrudController<Dock, DockMapper> {
    public DockController() {
        super(Dock.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name", "warehouse_code"};
    }

    @Override
    protected void beforeSave(Dock entity) {
        if (entity.getWarehouseCode() == null || entity.getCode() == null || entity.getName() == null) {
            throw new BizException("仓库、月台编码和名称不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
    }
}
