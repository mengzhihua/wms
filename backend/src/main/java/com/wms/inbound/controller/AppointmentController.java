package com.wms.inbound.controller;

import com.wms.basic.entity.Appointment;
import com.wms.basic.mapper.AppointmentMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inbound/appointment")
public class AppointmentController extends BaseCrudController<Appointment, AppointmentMapper> {
    @Autowired
    private CodeGenerator codeGenerator;

    public AppointmentController() {
        super(Appointment.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "ref_code", "dock_code"};
    }

    @Override
    protected void beforeSave(Appointment entity) {
        if (entity.getWarehouseCode() == null || entity.getDockCode() == null
                || entity.getRefType() == null || entity.getRefCode() == null) {
            throw new BizException("仓库、月台、单据类型和单号不能为空");
        }
        String refType = entity.getRefType().trim().toUpperCase();
        if (!"ASN".equals(refType) && !"SHIP".equals(refType)) {
            throw new BizException("预约单据类型只能是 ASN 或 SHIP");
        }
        entity.setRefType(refType);
        if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
            entity.setCode(codeGenerator.next("AP"));
        }
        if (entity.getStatus() == null || entity.getStatus().isEmpty()) {
            entity.setStatus("BOOKED");
        }
    }
}
