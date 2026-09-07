package com.wms.inbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Item;
import com.wms.basic.entity.Location;
import com.wms.basic.mapper.ItemMapper;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.entity.QcTask;
import com.wms.inbound.mapper.AsnLineMapper;
import com.wms.inbound.mapper.AsnMapper;
import com.wms.inbound.mapper.PutawayTaskMapper;
import com.wms.inbound.mapper.QcTaskMapper;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.service.InventoryService;
import com.wms.inventory.service.SerialService;
import com.wms.outbound.service.CrossDockService;
import com.wms.system.auth.CurrentUser;
import com.wms.system.entity.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 入库流程: ASN(NEW) -> 收货(RECEIVING/RECEIVED, 库存进入收货暂存区) -> 上架任务 -> 上架确认(PUTAWAY -> CLOSED)
 * 需质检的物料(item.qcRequired)或退货入库(type=RETURN)在收货时进入质检位并冻结，质检放行后才生成上架任务。
 */
@Service
@RequiredArgsConstructor
public class AsnService {

    private final AsnMapper asnMapper;
    private final AsnLineMapper lineMapper;
    private final PutawayTaskMapper taskMapper;
    private final QcTaskMapper qcTaskMapper;
    private final ItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final CrossDockService crossDockService;
    private final SerialService serialService;
    private final CodeGenerator codeGenerator;

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public Asn create(Asn asn) {
        validate(asn);
        asn.setId(null);
        asn.setCode(codeGenerator.next("ASN"));
        asn.setStatus("NEW");
        asn.setReceivedQty(BigDecimal.ZERO);
        asn.setPutawayQty(BigDecimal.ZERO);
        asn.setCrossDockQty(BigDecimal.ZERO);
        asn.setQcQty(BigDecimal.ZERO);
        asn.setRejectedQty(BigDecimal.ZERO);
        asn.setCrossDockOrderCode(blankToNull(asn.getCrossDockOrderCode()));
        asn.setTotalQty(asn.getLines().stream().map(AsnLine::getExpectedQty).reduce(BigDecimal.ZERO, BigDecimal::add));
        asnMapper.insert(asn);
        saveLines(asn);
        return load(asn.getId());
    }

    @Transactional
    public Asn update(Long id, Asn asn) {
        Asn db = require(id);
        if (!"NEW".equals(db.getStatus())) {
            throw new BizException("仅新建状态的入库单可修改");
        }
        validate(asn);
        db.setWarehouseCode(asn.getWarehouseCode());
        db.setOwnerCode(asn.getOwnerCode());
        db.setSupplierCode(asn.getSupplierCode());
        db.setCustomerCode(asn.getCustomerCode());
        db.setType(asn.getType());
        db.setExpectedDate(asn.getExpectedDate());
        db.setExternalNo(asn.getExternalNo());
        db.setRemark(asn.getRemark());
        db.setCrossDockOrderCode(blankToNull(asn.getCrossDockOrderCode()));
        db.setTotalQty(asn.getLines().stream().map(AsnLine::getExpectedQty).reduce(BigDecimal.ZERO, BigDecimal::add));
        asnMapper.updateById(db);
        lineMapper.delete(new LambdaQueryWrapper<AsnLine>().eq(AsnLine::getAsnId, id));
        db.setLines(asn.getLines());
        saveLines(db);
        return load(id);
    }

    @Transactional
    public void cancel(Long id) {
        Asn db = require(id);
        if (!"NEW".equals(db.getStatus())) {
            throw new BizException("已开始收货的入库单不能取消");
        }
        db.setStatus("CANCELLED");
        asnMapper.updateById(db);
    }

    public Asn load(Long id) {
        Asn asn = require(id);
        asn.setLines(lineMapper.selectList(new LambdaQueryWrapper<AsnLine>()
                .eq(AsnLine::getAsnId, id).orderByAsc(AsnLine::getLineNo)));
        return asn;
    }

    // ------------------------------------------------------------------ receiving

    @Data
    public static class ReceiveLine {
        private Long lineId;
        private BigDecimal qty;
        private String lotNo;
        private LocalDate expiryDate;
        /** optional; defaults to the warehouse STAGING_IN location */
        private String locationCode;
        /** 序列号管理物料必填，个数 = qty */
        private List<String> serialNos;
    }

    @Transactional
    public Asn receive(Long asnId, List<ReceiveLine> receipts) {
        Asn asn = require(asnId);
        if (!"NEW".equals(asn.getStatus()) && !"RECEIVING".equals(asn.getStatus())) {
            throw new BizException("入库单状态 " + asn.getStatus() + " 不可收货");
        }
        if (receipts == null || receipts.isEmpty()) {
            throw new BizException("收货明细为空");
        }
        Location staging = inventoryService.requireLocationByType(asn.getWarehouseCode(), "STAGING_IN");
        boolean isReturn = "RETURN".equals(asn.getType());
        for (ReceiveLine r : receipts) {
            if (r.getQty() == null || r.getQty().signum() <= 0) {
                continue;
            }
            AsnLine line = lineMapper.selectById(r.getLineId());
            if (line == null || !line.getAsnId().equals(asnId)) {
                throw new BizException("入库明细不存在: " + r.getLineId());
            }
            Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                    .eq(Item::getOwnerCode, asn.getOwnerCode()).eq(Item::getCode, line.getItemCode()));
            if (item == null) {
                throw new BizException("物料不存在: " + line.getItemCode());
            }
            String lot = r.getLotNo() != null && !r.getLotNo().isEmpty() ? r.getLotNo() : line.getLotNo();
            if (Boolean.TRUE.equals(item.getLotControl()) && (lot == null || lot.isEmpty())) {
                throw new BizException("物料 " + item.getCode() + " 启用批次管理，必须输入批次号");
            }
            LocalDate expiry = r.getExpiryDate() != null ? r.getExpiryDate() : line.getExpiryDate();
            if (expiry == null && item.getShelfLifeDays() != null && item.getShelfLifeDays() > 0) {
                expiry = LocalDate.now().plusDays(item.getShelfLifeDays());
            }
            boolean needQc = isReturn || Boolean.TRUE.equals(item.getQcRequired());
            String loc = r.getLocationCode() != null && !r.getLocationCode().isEmpty() ? r.getLocationCode() : staging.getCode();
            if (needQc) {
                loc = inventoryService.requireLocationByType(asn.getWarehouseCode(), "QC").getCode();
            }

            Inventory inv = inventoryService.add(asn.getWarehouseCode(), loc, asn.getOwnerCode(), line.getItemCode(),
                    lot, r.getQty(), expiry, asn.getCode(), "RECEIVE", null);
            if (Boolean.TRUE.equals(item.getSnControl())) {
                serialService.receive(asn.getOwnerCode(), item.getCode(), lot, loc, asn.getCode(), r.getQty(), r.getSerialNos());
            }

            line.setReceivedQty(nz(line.getReceivedQty()).add(r.getQty()));
            if (line.getLotNo() == null || line.getLotNo().isEmpty()) {
                line.setLotNo(lot);
            }
            lineMapper.updateById(line);
            asn.setReceivedQty(nz(asn.getReceivedQty()).add(r.getQty()));

            BigDecimal toPutaway = r.getQty();
            if (needQc) {
                inventoryService.setFrozen(inv.getId(), true, "待质检 " + asn.getCode());
                QcTask qc = new QcTask();
                qc.setCode(codeGenerator.next("QC"));
                qc.setAsnId(asnId);
                qc.setAsnLineId(line.getId());
                qc.setAsnCode(asn.getCode());
                qc.setWarehouseCode(asn.getWarehouseCode());
                qc.setOwnerCode(asn.getOwnerCode());
                qc.setItemCode(line.getItemCode());
                qc.setLotNo(lot == null ? "" : lot);
                qc.setInventoryId(inv.getId());
                qc.setLocationCode(loc);
                qc.setQty(r.getQty());
                qc.setStatus("NEW");
                qcTaskMapper.insert(qc);
                asn.setQcQty(nz(asn.getQcQty()).add(r.getQty()));
                continue;
            }
            if (asn.getCrossDockOrderCode() != null) {
                BigDecimal xd = crossDockService.crossDock(asn.getCrossDockOrderCode(), inv, r.getQty(), asn.getCode());
                asn.setCrossDockQty(nz(asn.getCrossDockQty()).add(xd));
                toPutaway = toPutaway.subtract(xd);
                if (toPutaway.signum() <= 0) {
                    continue;
                }
            }

            createPutawayTask(asn, line, lot, inv.getId(), loc, toPutaway);
        }
        boolean complete = load(asnId).getLines().stream()
                .allMatch(l -> nz(l.getReceivedQty()).compareTo(l.getExpectedQty()) >= 0);
        asn.setStatus(complete ? "RECEIVED" : "RECEIVING");
        asnMapper.updateById(asn);
        refreshStatus(asnId);
        return load(asnId);
    }

    private PutawayTask createPutawayTask(Asn asn, AsnLine line, String lot, Long inventoryId, String fromLoc, BigDecimal qty) {
        PutawayTask task = new PutawayTask();
        task.setCode(codeGenerator.next("PA"));
        task.setAsnId(asn.getId());
        task.setAsnLineId(line.getId());
        task.setAsnCode(asn.getCode());
        task.setWarehouseCode(asn.getWarehouseCode());
        task.setOwnerCode(asn.getOwnerCode());
        task.setItemCode(line.getItemCode());
        task.setLotNo(lot == null ? "" : lot);
        task.setInventoryId(inventoryId);
        task.setFromLocation(fromLoc);
        task.setSuggestLocation(inventoryService.suggestPutawayLocation(asn.getWarehouseCode(), asn.getOwnerCode(),
                line.getItemCode(), lot, reservedByPendingTasks(asn.getWarehouseCode())));
        task.setQty(qty);
        task.setStatus("NEW");
        taskMapper.insert(task);
        return task;
    }

    // ------------------------------------------------------------------ QC

    /** 质检放行：合格数量生成上架任务，拒收数量直接从质检位扣减出库（供应商退货/报废） */
    @Transactional
    public QcTask inspect(Long taskId, BigDecimal passQty, BigDecimal rejectQty, String rejectReason) {
        QcTask task = qcTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("质检任务不存在");
        }
        if (!"NEW".equals(task.getStatus())) {
            throw new BizException("质检任务已处理");
        }
        BigDecimal pass = nz(passQty);
        BigDecimal reject = nz(rejectQty);
        if (pass.signum() < 0 || reject.signum() < 0 || pass.add(reject).compareTo(task.getQty()) != 0) {
            throw new BizException("合格数量 + 拒收数量必须等于质检数量 " + task.getQty());
        }
        if (reject.signum() > 0 && (rejectReason == null || rejectReason.trim().isEmpty())) {
            throw new BizException("拒收必须填写原因");
        }
        Asn asn = require(task.getAsnId());
        AsnLine line = lineMapper.selectById(task.getAsnLineId());
        inventoryService.setFrozen(task.getInventoryId(), false, "质检完成 " + task.getCode());
        if (reject.signum() > 0) {
            inventoryService.deduct(task.getInventoryId(), reject, false, asn.getCode(), "QC_REJECT");
            line.setRejectedQty(nz(line.getRejectedQty()).add(reject));
            asn.setRejectedQty(nz(asn.getRejectedQty()).add(reject));
        }
        if (pass.signum() > 0) {
            createPutawayTask(asn, line, task.getLotNo(), task.getInventoryId(), task.getLocationCode(), pass);
        }
        lineMapper.updateById(line);
        asn.setQcQty(nz(asn.getQcQty()).subtract(task.getQty()));
        asnMapper.updateById(asn);

        task.setPassQty(pass);
        task.setRejectQty(reject);
        task.setRejectReason(rejectReason);
        User u = CurrentUser.get();
        task.setInspector(u != null ? u.getUsername() : "system");
        task.setStatus("DONE");
        qcTaskMapper.updateById(task);
        refreshStatus(asn.getId());
        return task;
    }

    public List<QcTask> qcTasks(Long asnId, String status) {
        return qcTaskMapper.selectList(new LambdaQueryWrapper<QcTask>()
                .eq(asnId != null, QcTask::getAsnId, asnId)
                .eq(status != null && !status.isEmpty(), QcTask::getStatus, status)
                .orderByAsc(QcTask::getId));
    }

    /** Mark receiving finished even if short (短收关闭收货). */
    @Transactional
    public Asn closeReceiving(Long asnId) {
        Asn asn = require(asnId);
        if (!"RECEIVING".equals(asn.getStatus())) {
            throw new BizException("仅收货中的入库单可关闭收货");
        }
        asn.setStatus("RECEIVED");
        asnMapper.updateById(asn);
        refreshStatus(asnId);
        return load(asnId);
    }

    /** Locations already suggested to open putaway tasks, modelled as pseudo-inventory so they count as occupied. */
    private List<Inventory> reservedByPendingTasks(String warehouse) {
        return taskMapper.selectList(new LambdaQueryWrapper<PutawayTask>()
                        .eq(PutawayTask::getWarehouseCode, warehouse)
                        .eq(PutawayTask::getStatus, "NEW")
                        .isNotNull(PutawayTask::getSuggestLocation))
                .stream().map(t -> {
                    Inventory i = new Inventory();
                    i.setWarehouseCode(warehouse);
                    i.setLocationCode(t.getSuggestLocation());
                    i.setOwnerCode(t.getOwnerCode());
                    i.setItemCode(t.getItemCode());
                    i.setLotNo(t.getLotNo());
                    i.setQty(t.getQty());
                    return i;
                }).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ putaway

    @Transactional
    public PutawayTask putaway(Long taskId, String toLocation) {
        PutawayTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("上架任务不存在");
        }
        if (!"NEW".equals(task.getStatus())) {
            throw new BizException("任务已处理");
        }
        String target = toLocation != null && !toLocation.isEmpty() ? toLocation : task.getSuggestLocation();
        if (target == null || target.isEmpty()) {
            throw new BizException("请指定上架库位");
        }
        Location loc = inventoryService.requireLocation(task.getWarehouseCode(), target);
        if (!"STORAGE".equals(loc.getType()) && !"PICKING".equals(loc.getType())) {
            throw new BizException("只能上架到存储/拣货类型库位");
        }
        inventoryService.move(task.getInventoryId(), task.getQty(), target, task.getAsnCode(), "PUTAWAY", "");

        task.setToLocation(target);
        task.setStatus("DONE");
        taskMapper.updateById(task);

        AsnLine line = lineMapper.selectById(task.getAsnLineId());
        line.setPutawayQty(nz(line.getPutawayQty()).add(task.getQty()));
        lineMapper.updateById(line);

        Asn asn = require(task.getAsnId());
        asn.setPutawayQty(nz(asn.getPutawayQty()).add(task.getQty()));
        asnMapper.updateById(asn);
        refreshStatus(asn.getId());
        return task;
    }

    public List<PutawayTask> tasks(Long asnId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PutawayTask>()
                .eq(PutawayTask::getAsnId, asnId).orderByAsc(PutawayTask::getId));
    }

    // ------------------------------------------------------------------ helpers

    private void refreshStatus(Long asnId) {
        Asn asn = require(asnId);
        long open = taskMapper.selectCount(new LambdaQueryWrapper<PutawayTask>()
                .eq(PutawayTask::getAsnId, asnId).eq(PutawayTask::getStatus, "NEW"));
        long openQc = qcTaskMapper.selectCount(new LambdaQueryWrapper<QcTask>()
                .eq(QcTask::getAsnId, asnId).eq(QcTask::getStatus, "NEW"));
        if (openQc > 0) {
            if ("RECEIVED".equals(asn.getStatus()) || "PUTAWAY".equals(asn.getStatus())) {
                asn.setStatus("QC");
            }
        } else if ("QC".equals(asn.getStatus())) {
            asn.setStatus(open > 0 ? "PUTAWAY" : "CLOSED");
        } else if (open > 0) {
            if ("RECEIVED".equals(asn.getStatus())) {
                asn.setStatus("PUTAWAY");
            }
        } else if ("RECEIVED".equals(asn.getStatus()) || "PUTAWAY".equals(asn.getStatus())) {
            asn.setStatus("CLOSED");
        }
        asnMapper.updateById(asn);
    }

    private void validate(Asn asn) {
        if (asn.getWarehouseCode() == null || asn.getOwnerCode() == null) {
            throw new BizException("仓库和货主不能为空");
        }
        if ("RETURN".equals(asn.getType()) && blankToNull(asn.getCustomerCode()) == null) {
            throw new BizException("退货入库必须选择客户");
        }
        if (blankToNull(asn.getCrossDockOrderCode()) != null) {
            crossDockService.requireTarget(asn.getCrossDockOrderCode().trim(), asn.getWarehouseCode(), asn.getOwnerCode());
        }
        if (asn.getLines() == null || asn.getLines().isEmpty()) {
            throw new BizException("入库单至少需要一行明细");
        }
        for (AsnLine l : asn.getLines()) {
            if (l.getItemCode() == null || l.getItemCode().isEmpty()) {
                throw new BizException("明细物料不能为空");
            }
            if (l.getExpectedQty() == null || l.getExpectedQty().signum() <= 0) {
                throw new BizException("明细数量必须大于0");
            }
            Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                    .eq(Item::getOwnerCode, asn.getOwnerCode()).eq(Item::getCode, l.getItemCode()));
            if (item == null) {
                throw new BizException("货主 " + asn.getOwnerCode() + " 下不存在物料 " + l.getItemCode());
            }
        }
    }

    private void saveLines(Asn asn) {
        int no = 1;
        for (AsnLine l : asn.getLines()) {
            l.setId(null);
            l.setAsnId(asn.getId());
            l.setLineNo(no++);
            l.setReceivedQty(BigDecimal.ZERO);
            l.setPutawayQty(BigDecimal.ZERO);
            l.setRejectedQty(BigDecimal.ZERO);
            lineMapper.insert(l);
        }
    }

    private Asn require(Long id) {
        Asn asn = asnMapper.selectById(id);
        if (asn == null) {
            throw new BizException("入库单不存在");
        }
        return asn;
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
