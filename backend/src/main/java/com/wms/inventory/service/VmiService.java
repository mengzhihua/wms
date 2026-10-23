package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.VmiAgreement;
import com.wms.basic.mapper.VmiAgreementMapper;
import com.wms.basic.service.WarehouseProfileService;
import com.wms.common.BizException;
import com.wms.inbound.entity.Asn;
import com.wms.integration.client.SrmReceiptClient;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.mapper.AsnLineMapper;
import com.wms.inbound.mapper.AsnMapper;
import com.wms.inbound.service.AsnService;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 供应商拉动：寄售可用量加未收完 ASN 低于再订货点时生成建议入库单，不自动收货。 */
@Service
@RequiredArgsConstructor
public class VmiService {
    private final VmiAgreementMapper agreementMapper;
    private final InventoryMapper inventoryMapper;
    private final AsnMapper asnMapper;
    private final AsnLineMapper asnLineMapper;
    private final AsnService asnService;
    private final WarehouseProfileService profiles;
    private final SrmReceiptClient srmReceiptClient;

    @Transactional
    public List<Asn> suggest(String warehouse) {
        if (warehouse == null || warehouse.isEmpty()) {
            throw new BizException("仓库不能为空");
        }
        if (!profiles.isVmi(warehouse)) {
            throw new BizException("只有 VMI 仓生成供应商补货建议");
        }
        List<Asn> created = new ArrayList<>();
        for (VmiAgreement agreement : agreementMapper.selectList(new LambdaQueryWrapper<VmiAgreement>()
                .eq(VmiAgreement::getWarehouseCode, warehouse).eq(VmiAgreement::getStatus, 1))) {
            if (agreement.getReorderPoint() == null || agreement.getMaxQty() == null) {
                continue;
            }
            BigDecimal onHand = vendorQty(agreement);
            BigDecimal inbound = openQty(agreement);
            if (onHand.add(inbound).compareTo(agreement.getReorderPoint()) >= 0) {
                continue;
            }
            BigDecimal qty = agreement.getMaxQty().subtract(onHand).subtract(inbound);
            if (qty.signum() <= 0) {
                continue;
            }
            Asn asn = new Asn();
            asn.setWarehouseCode(agreement.getWarehouseCode());
            asn.setOwnerCode(agreement.getOwnerCode());
            asn.setSupplierCode(agreement.getSupplierCode());
            asn.setType("VMI_SUGGEST");
            asn.setRemark("VMI 建议补货，提前期 "
                    + (agreement.getLeadTimeDays() == null ? 0 : agreement.getLeadTimeDays()) + " 天");
            AsnLine line = new AsnLine();
            line.setLineNo(1);
            line.setItemCode(agreement.getItemCode());
            line.setExpectedQty(qty);
            asn.setLines(Collections.singletonList(line));
            Asn saved = asnService.create(asn);
            srmReceiptClient.pushSuggest(saved);
            created.add(saved);
        }
        return created;
    }

    /** 供应商可见：协议、寄售库存、未完成的建议入库。 */
    public Map<String, Object> visible(String supplierCode) {
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new BizException("供应商不能为空");
        }
        String supplier = supplierCode.trim();
        List<VmiAgreement> agreements = agreementMapper.selectList(new LambdaQueryWrapper<VmiAgreement>()
                .eq(VmiAgreement::getSupplierCode, supplier).eq(VmiAgreement::getStatus, 1));
        List<Map<String, Object>> stock = new ArrayList<>();
        for (VmiAgreement agreement : agreements) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("warehouseCode", agreement.getWarehouseCode());
            row.put("ownerCode", agreement.getOwnerCode());
            row.put("itemCode", agreement.getItemCode());
            row.put("vendorQty", vendorQty(agreement));
            row.put("openAsnQty", openQty(agreement));
            row.put("reorderPoint", agreement.getReorderPoint());
            row.put("maxQty", agreement.getMaxQty());
            row.put("transferPoint", agreement.getTransferPoint());
            stock.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplierCode", supplier);
        payload.put("agreements", agreements);
        payload.put("stock", stock);
        return payload;
    }

    private BigDecimal vendorQty(VmiAgreement agreement) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getWarehouseCode, agreement.getWarehouseCode())
                        .eq(Inventory::getOwnerCode, agreement.getOwnerCode())
                        .eq(Inventory::getItemCode, agreement.getItemCode())
                        .eq(Inventory::getOwnership, InventoryService.VENDOR)
                        .gt(Inventory::getQty, 0))
                .stream().map(Inventory::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal openQty(VmiAgreement agreement) {
        List<Asn> asns = asnMapper.selectList(new LambdaQueryWrapper<Asn>()
                .eq(Asn::getWarehouseCode, agreement.getWarehouseCode())
                .eq(Asn::getOwnerCode, agreement.getOwnerCode())
                .eq(Asn::getSupplierCode, agreement.getSupplierCode())
                .in(Asn::getStatus, "NEW", "RECEIVING"));
        BigDecimal sum = BigDecimal.ZERO;
        for (Asn asn : asns) {
            for (AsnLine line : asnLineMapper.selectList(new LambdaQueryWrapper<AsnLine>().eq(AsnLine::getAsnId, asn.getId()))) {
                if (!agreement.getItemCode().equals(line.getItemCode())) {
                    continue;
                }
                BigDecimal received = line.getReceivedQty() == null ? BigDecimal.ZERO : line.getReceivedQty();
                sum = sum.add(line.getExpectedQty().subtract(received).max(BigDecimal.ZERO));
            }
        }
        return sum;
    }
}
