package com.wms.basic.controller;

import com.wms.basic.entity.VmiAgreement;
import com.wms.basic.mapper.VmiAgreementMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/vmi-agreement")
public class VmiAgreementController extends BaseCrudController<VmiAgreement, VmiAgreementMapper> {
    public VmiAgreementController() {
        super(VmiAgreement.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"supplier_code", "item_code", "warehouse_code", "owner_code"};
    }

    @Override
    protected void beforeSave(VmiAgreement entity) {
        if (entity.getSupplierCode() == null || entity.getOwnerCode() == null
                || entity.getWarehouseCode() == null || entity.getItemCode() == null
                || entity.getTransferPoint() == null) {
            throw new BizException("供应商、货主、仓库、物料和转移时点不能为空");
        }
        String point = entity.getTransferPoint().trim().toUpperCase();
        if (!"RECEIPT".equals(point) && !"SHIP".equals(point) && !"CONSUMPTION".equals(point)) {
            throw new BizException("转移时点只能是 RECEIPT、SHIP、CONSUMPTION");
        }
        entity.setTransferPoint(point);
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
    }
}
