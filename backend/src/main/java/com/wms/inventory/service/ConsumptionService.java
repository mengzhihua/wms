package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import com.wms.inventory.entity.Consumption;
import com.wms.inventory.entity.ConsumptionLine;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.ConsumptionLineMapper;
import com.wms.inventory.mapper.ConsumptionMapper;
import com.wms.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 线边消耗。只扣供应商物权，并写物权转移流水。 */
@Service
@RequiredArgsConstructor
public class ConsumptionService {
    private final ConsumptionMapper consumptionMapper;
    private final ConsumptionLineMapper lineMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;
    private final CodeGenerator codeGenerator;

    @Transactional
    public Consumption create(Consumption req) {
        if (req.getWarehouseCode() == null || req.getOwnerCode() == null) {
            throw new BizException("仓库和货主不能为空");
        }
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new BizException("消耗明细不能为空");
        }
        req.setId(null);
        req.setCode(codeGenerator.next("CS"));
        req.setStatus("NEW");
        BigDecimal total = BigDecimal.ZERO;
        for (ConsumptionLine line : req.getLines()) {
            if (line.getItemCode() == null || line.getQty() == null || line.getQty().signum() <= 0) {
                throw new BizException("消耗明细需要物料和正数数量");
            }
            total = total.add(line.getQty());
        }
        req.setTotalQty(total);
        consumptionMapper.insert(req);
        int n = 1;
        for (ConsumptionLine line : req.getLines()) {
            line.setId(null);
            line.setConsumptionId(req.getId());
            line.setLineNo(n++);
            lineMapper.insert(line);
        }
        return load(req.getId());
    }

    public Consumption load(Long id) {
        Consumption c = consumptionMapper.selectById(id);
        if (c == null) {
            throw new BizException("消耗单不存在");
        }
        c.setLines(lineMapper.selectList(new LambdaQueryWrapper<ConsumptionLine>()
                .eq(ConsumptionLine::getConsumptionId, id).orderByAsc(ConsumptionLine::getLineNo)));
        return c;
    }

    @Transactional
    public Consumption post(Long id) {
        Consumption c = load(id);
        if (!"NEW".equals(c.getStatus())) {
            throw new BizException("消耗单已过账");
        }
        for (ConsumptionLine line : c.getLines()) {
            BigDecimal need = line.getQty();
            List<Inventory> stock = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                    .eq(Inventory::getWarehouseCode, c.getWarehouseCode())
                    .eq(Inventory::getOwnerCode, c.getOwnerCode())
                    .eq(Inventory::getItemCode, line.getItemCode())
                    .eq(Inventory::getOwnership, InventoryService.VENDOR)
                    .eq(Inventory::getStatus, InventoryService.AVAILABLE)
                    .eq(Inventory::getRefNo, "")
                    .gt(Inventory::getQty, 0));
            stock.sort(Comparator
                    .comparing((Inventory i) -> i.getExpiryDate() == null ? LocalDate.MAX : i.getExpiryDate())
                    .thenComparing(i -> i.getReceiveDate() == null ? LocalDate.MIN : i.getReceiveDate())
                    .thenComparing(Inventory::getId));
            for (Inventory inv : stock) {
                if (need.signum() <= 0) {
                    break;
                }
                BigDecimal take = inv.getAvailableQty().min(need);
                if (take.signum() <= 0) {
                    continue;
                }
                inventoryService.issueVendor(inv.getId(), take, c.getCode());
                need = need.subtract(take);
            }
            if (need.signum() > 0) {
                throw new BizException("寄售库存不足，物料 " + line.getItemCode() + " 缺 " + need);
            }
        }
        c.setStatus("POSTED");
        consumptionMapper.updateById(c);
        return load(id);
    }
}
