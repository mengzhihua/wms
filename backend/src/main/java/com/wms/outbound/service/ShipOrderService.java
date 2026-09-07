package com.wms.outbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Carton;
import com.wms.basic.entity.Item;
import com.wms.basic.entity.Location;
import com.wms.basic.mapper.CartonMapper;
import com.wms.basic.mapper.ItemMapper;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.service.InventoryService;
import com.wms.inventory.service.SerialService;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.mapper.PickTaskMapper;
import com.wms.outbound.mapper.ShipOrderLineMapper;
import com.wms.outbound.mapper.ShipOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库流程: 出库单(NEW) -> 分配(ALLOCATED / PART_ALLOCATED, 生成拣货任务) -> 拣货(PICKING/PICKED, 库存移至发货暂存区)
 * -> 复核打包(PACKED, 可选, 记录箱数/毛重/运单号) -> 发运(SHIPPED, 库存扣减)
 */
@Service
@RequiredArgsConstructor
public class ShipOrderService {

    private final ShipOrderMapper orderMapper;
    private final ShipOrderLineMapper lineMapper;
    private final PickTaskMapper taskMapper;
    private final ItemMapper itemMapper;
    private final CartonMapper cartonMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;
    private final SerialService serialService;
    private final CodeGenerator codeGenerator;

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public ShipOrder create(ShipOrder order) {
        validate(order);
        order.setId(null);
        order.setCode(codeGenerator.next("SO"));
        order.setStatus("NEW");
        if (order.getPriority() == null) {
            order.setPriority(5);
        }
        order.setAllocatedQty(BigDecimal.ZERO);
        order.setPickedQty(BigDecimal.ZERO);
        order.setShippedQty(BigDecimal.ZERO);
        order.setTotalQty(sum(order.getLines()));
        orderMapper.insert(order);
        saveLines(order);
        return load(order.getId());
    }

    @Transactional
    public ShipOrder update(Long id, ShipOrder order) {
        ShipOrder db = require(id);
        if (!"NEW".equals(db.getStatus())) {
            throw new BizException("仅新建状态的出库单可修改");
        }
        validate(order);
        db.setWarehouseCode(order.getWarehouseCode());
        db.setOwnerCode(order.getOwnerCode());
        db.setCustomerCode(order.getCustomerCode());
        db.setType(order.getType());
        db.setPriority(order.getPriority());
        db.setExpectedShipDate(order.getExpectedShipDate());
        db.setExternalNo(order.getExternalNo());
        db.setCarrier(order.getCarrier());
        db.setAddress(order.getAddress());
        db.setRemark(order.getRemark());
        db.setTotalQty(sum(order.getLines()));
        orderMapper.updateById(db);
        lineMapper.delete(new LambdaQueryWrapper<ShipOrderLine>().eq(ShipOrderLine::getOrderId, id));
        db.setLines(order.getLines());
        saveLines(db);
        return load(id);
    }

    public ShipOrder load(Long id) {
        ShipOrder o = require(id);
        o.setLines(lineMapper.selectList(new LambdaQueryWrapper<ShipOrderLine>()
                .eq(ShipOrderLine::getOrderId, id).orderByAsc(ShipOrderLine::getLineNo)));
        return o;
    }

    public List<PickTask> tasks(Long orderId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PickTask>()
                .eq(PickTask::getOrderId, orderId).orderByAsc(PickTask::getFromLocation));
    }

    // ------------------------------------------------------------------ allocate

    @Transactional
    public ShipOrder allocate(Long orderId) {
        ShipOrder order = require(orderId);
        if (!"NEW".equals(order.getStatus()) && !"PART_ALLOCATED".equals(order.getStatus())) {
            throw new BizException("出库单状态 " + order.getStatus() + " 不可分配");
        }
        Location staging = inventoryService.requireLocationByType(order.getWarehouseCode(), "STAGING_OUT");
        boolean full = true;
        for (ShipOrderLine line : load(orderId).getLines()) {
            BigDecimal need = line.getOrderQty().subtract(nz(line.getAllocatedQty()));
            if (need.signum() <= 0) {
                continue;
            }
            List<InventoryService.Allocation> allocs = inventoryService.allocate(order.getWarehouseCode(),
                    order.getOwnerCode(), line.getItemCode(), line.getLotNo(), need, order.getCode());
            for (InventoryService.Allocation a : allocs) {
                PickTask t = new PickTask();
                t.setCode(codeGenerator.next("PK"));
                t.setOrderId(orderId);
                t.setOrderLineId(line.getId());
                t.setOrderCode(order.getCode());
                t.setWarehouseCode(order.getWarehouseCode());
                t.setOwnerCode(order.getOwnerCode());
                t.setItemCode(line.getItemCode());
                t.setLotNo(a.inventory.getLotNo());
                t.setInventoryId(a.inventory.getId());
                t.setFromLocation(a.inventory.getLocationCode());
                t.setToLocation(staging.getCode());
                t.setQty(a.qty);
                t.setPickedQty(BigDecimal.ZERO);
                t.setStatus("NEW");
                taskMapper.insert(t);
                line.setAllocatedQty(nz(line.getAllocatedQty()).add(a.qty));
                order.setAllocatedQty(nz(order.getAllocatedQty()).add(a.qty));
            }
            lineMapper.updateById(line);
            if (line.getAllocatedQty().compareTo(line.getOrderQty()) < 0) {
                full = false;
            }
        }
        if (order.getAllocatedQty().signum() == 0) {
            throw new BizException("无可用库存，分配失败");
        }
        order.setStatus(full ? "ALLOCATED" : "PART_ALLOCATED");
        orderMapper.updateById(order);
        return load(orderId);
    }

    /** 取消分配: release reservations and remove open pick tasks */
    @Transactional
    public ShipOrder deallocate(Long orderId) {
        ShipOrder order = require(orderId);
        if (!"ALLOCATED".equals(order.getStatus()) && !"PART_ALLOCATED".equals(order.getStatus())) {
            throw new BizException("仅已分配且未开始拣货的出库单可取消分配");
        }
        for (PickTask t : tasks(orderId)) {
            if ("NEW".equals(t.getStatus()) && t.getWaveId() != null) {
                throw new BizException("出库单已加入波次，请先取消波次");
            }
        }
        for (PickTask t : tasks(orderId)) {
            if ("NEW".equals(t.getStatus())) {
                inventoryService.release(t.getInventoryId(), t.getQty(), order.getCode());
                t.setStatus("CANCELLED");
                taskMapper.updateById(t);
            }
        }
        for (ShipOrderLine l : load(orderId).getLines()) {
            l.setAllocatedQty(BigDecimal.ZERO);
            lineMapper.updateById(l);
        }
        order.setAllocatedQty(BigDecimal.ZERO);
        order.setStatus("NEW");
        orderMapper.updateById(order);
        return load(orderId);
    }

    // ------------------------------------------------------------------ pick

    @Transactional
    public PickTask pick(Long taskId, BigDecimal qty) {
        PickTask task = taskMapper.selectById(taskId);
        if (task != null && task.getWaveId() != null && "NEW".equals(task.getStatus())) {
            throw new BizException("任务已加入波次，请在波次页面进行总拣");
        }
        return pick(taskId, qty, false);
    }

    /** {@code allowZero}: confirm a task with nothing picked (short pick), releasing its whole reservation. */
    @Transactional
    public PickTask pick(Long taskId, BigDecimal qty, boolean allowZero) {
        PickTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("拣货任务不存在");
        }
        if (!"NEW".equals(task.getStatus())) {
            throw new BizException("任务已处理");
        }
        BigDecimal pickQty = qty == null ? task.getQty() : qty;
        if ((pickQty.signum() < 0 || (!allowZero && pickQty.signum() == 0)) || pickQty.compareTo(task.getQty()) > 0) {
            throw new BizException("拣货数量必须在 0 到 " + task.getQty() + " 之间");
        }
        ShipOrder order = require(task.getOrderId());

        // take from storage (release reservation) and park in the outbound staging, reserved for this order
        if (pickQty.signum() > 0) {
            Inventory src = inventoryMapper.selectById(task.getInventoryId());
            inventoryService.deduct(task.getInventoryId(), pickQty, true, order.getCode(), "PICK");
            inventoryService.add(task.getWarehouseCode(), task.getToLocation(), task.getOwnerCode(), task.getItemCode(),
                    task.getLotNo(), pickQty, src == null ? null : src.getExpiryDate(), order.getCode(), "STAGE", pickQty);
        }
        BigDecimal shortQty = task.getQty().subtract(pickQty);
        if (shortQty.signum() > 0) {
            inventoryService.release(task.getInventoryId(), shortQty, order.getCode());
        }

        task.setPickedQty(pickQty);
        task.setStatus("DONE");
        taskMapper.updateById(task);

        ShipOrderLine line = lineMapper.selectById(task.getOrderLineId());
        line.setPickedQty(nz(line.getPickedQty()).add(pickQty));
        if (shortQty.signum() > 0) {
            line.setAllocatedQty(nz(line.getAllocatedQty()).subtract(shortQty));
        }
        lineMapper.updateById(line);

        order.setPickedQty(nz(order.getPickedQty()).add(pickQty));
        if (shortQty.signum() > 0) {
            order.setAllocatedQty(nz(order.getAllocatedQty()).subtract(shortQty));
        }
        long open = taskMapper.selectCount(new LambdaQueryWrapper<PickTask>()
                .eq(PickTask::getOrderId, order.getId()).eq(PickTask::getStatus, "NEW"));
        if (open > 0) {
            order.setStatus("PICKING");
        } else {
            order.setStatus(nz(order.getPickedQty()).signum() > 0 ? "PICKED" : "PART_ALLOCATED");
        }
        orderMapper.updateById(order);
        return task;
    }

    // ------------------------------------------------------------------ ship

    /** 复核打包：核对拣货数量与订单一致后记录包装信息与承运/运单，状态 PICKED -> PACKED */
    /** 箱型推荐：按已拣商品总体积/总重量，选能装下的最小箱型；装不下时按最大箱型估算箱数 */
    public Map<String, Object> suggestCarton(Long orderId) {
        ShipOrder order = load(orderId);
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (ShipOrderLine l : order.getLines()) {
            BigDecimal q = nz(l.getPickedQty()).signum() > 0 ? l.getPickedQty() : nz(l.getOrderQty());
            Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                    .eq(Item::getOwnerCode, order.getOwnerCode()).eq(Item::getCode, l.getItemCode()));
            if (item != null) {
                volume = volume.add(nz(item.getVolume()).multiply(q));
                weight = weight.add(nz(item.getWeight()).multiply(q));
            }
        }
        List<Carton> cartons = cartonMapper.selectList(new LambdaQueryWrapper<Carton>().eq(Carton::getStatus, 1));
        cartons.sort(Comparator.comparing(c -> nz(c.getVolume())));
        Carton fit = null;
        for (Carton c : cartons) {
            boolean volOk = nz(c.getVolume()).compareTo(volume) >= 0;
            boolean wtOk = c.getMaxWeight() == null || c.getMaxWeight().compareTo(weight) >= 0;
            if (volOk && wtOk) {
                fit = c;
                break;
            }
        }
        int packages = 1;
        if (fit == null && !cartons.isEmpty()) {
            fit = cartons.get(cartons.size() - 1);
            BigDecimal byVol = nz(fit.getVolume()).signum() > 0 ? volume.divide(fit.getVolume(), 0, BigDecimal.ROUND_CEILING) : BigDecimal.ONE;
            BigDecimal byWt = fit.getMaxWeight() != null && fit.getMaxWeight().signum() > 0
                    ? weight.divide(fit.getMaxWeight(), 0, BigDecimal.ROUND_CEILING) : BigDecimal.ONE;
            packages = Math.max(1, byVol.max(byWt).intValue());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalVolume", volume);
        out.put("totalWeight", weight);
        out.put("carton", fit);
        out.put("packageCount", packages);
        out.put("cartons", cartons);
        return out;
    }

    @Transactional
    public ShipOrder pack(Long orderId, Integer packageCount, BigDecimal grossWeight, String carrier, String trackingNo) {
        return pack(orderId, packageCount, grossWeight, carrier, trackingNo, null);
    }

    /** 指定箱型时记录 cartonCode；箱型关联包材 SKU 时按箱数扣减包材库存（PACK_CONSUME） */
    @Transactional
    public ShipOrder pack(Long orderId, Integer packageCount, BigDecimal grossWeight, String carrier, String trackingNo, String cartonCode) {
        ShipOrder order = load(orderId);
        if (!"PICKED".equals(order.getStatus())) {
            throw new BizException("拣货完成后才能复核打包");
        }
        for (ShipOrderLine l : order.getLines()) {
            if (nz(l.getPickedQty()).signum() <= 0) {
                throw new BizException("明细 " + l.getItemCode() + " 拣货数量为0，复核不通过");
            }
        }
        if (packageCount == null || packageCount <= 0) {
            throw new BizException("箱数必须大于0");
        }
        if (cartonCode != null && !cartonCode.trim().isEmpty()) {
            Carton carton = cartonMapper.selectOne(new LambdaQueryWrapper<Carton>().eq(Carton::getCode, cartonCode.trim()));
            if (carton == null) {
                throw new BizException("箱型不存在: " + cartonCode);
            }
            order.setCartonCode(carton.getCode());
            if (carton.getItemCode() != null) {
                consumePackaging(order, carton, packageCount);
            }
        }
        order.setPackageCount(packageCount);
        order.setGrossWeight(grossWeight);
        if (carrier != null && !carrier.trim().isEmpty()) {
            order.setCarrier(carrier.trim());
        }
        if (trackingNo != null && !trackingNo.trim().isEmpty()) {
            order.setTrackingNo(trackingNo.trim());
        }
        order.setPackedAt(LocalDateTime.now());
        order.setStatus("PACKED");
        orderMapper.updateById(order);
        return load(orderId);
    }

    @Transactional
    public ShipOrder ship(Long orderId) {
        return ship(orderId, null, null);
    }

    private void consumePackaging(ShipOrder order, Carton carton, int count) {
        List<Inventory> stock = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, order.getWarehouseCode())
                .eq(Inventory::getOwnerCode, carton.getOwnerCode())
                .eq(Inventory::getItemCode, carton.getItemCode())
                .eq(Inventory::getStatus, "AVAILABLE")
                .orderByAsc(Inventory::getExpiryDate, Inventory::getReceiveDate, Inventory::getId));
        BigDecimal need = BigDecimal.valueOf(count);
        for (Inventory inv : stock) {
            if (need.signum() <= 0) {
                break;
            }
            BigDecimal avail = inv.getQty().subtract(nz(inv.getAllocatedQty()));
            if (avail.signum() <= 0 || inv.getRefNo() != null && !inv.getRefNo().isEmpty()) {
                continue;
            }
            BigDecimal take = avail.min(need);
            inventoryService.deduct(inv.getId(), take, false, order.getCode(), "PACK_CONSUME");
            need = need.subtract(take);
        }
        if (need.signum() > 0) {
            throw new BizException("包材 " + carton.getItemCode() + " 库存不足，缺 " + need);
        }
    }

    @Transactional
    public ShipOrder ship(Long orderId, String carrier, String trackingNo) {
        return ship(orderId, carrier, trackingNo, null);
    }

    /** 序列号管理物料发运时必须扫描登记 SN，个数与应发数量一致 */
    @Transactional
    public ShipOrder ship(Long orderId, String carrier, String trackingNo, List<String> serialNos) {
        ShipOrder order = load(orderId);
        if (!"PICKED".equals(order.getStatus()) && !"PACKED".equals(order.getStatus())) {
            throw new BizException("拣货完成后才能发运");
        }
        Map<String, BigDecimal> snRequired = new LinkedHashMap<>();
        for (ShipOrderLine l : order.getLines()) {
            Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                    .eq(Item::getOwnerCode, order.getOwnerCode()).eq(Item::getCode, l.getItemCode()));
            if (item != null && Boolean.TRUE.equals(item.getSnControl()) && nz(l.getPickedQty()).signum() > 0) {
                snRequired.merge(l.getItemCode(), l.getPickedQty(), BigDecimal::add);
            }
        }
        if (!snRequired.isEmpty()) {
            serialService.ship(order.getOwnerCode(), order.getCode(), snRequired, serialNos == null ? new ArrayList<>() : serialNos);
        }
        if (carrier != null && !carrier.trim().isEmpty()) {
            order.setCarrier(carrier.trim());
        }
        if (trackingNo != null && !trackingNo.trim().isEmpty()) {
            order.setTrackingNo(trackingNo.trim());
        }
        List<Inventory> staged = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, order.getWarehouseCode())
                .eq(Inventory::getRefNo, order.getCode()));
        BigDecimal shipped = BigDecimal.ZERO;
        for (Inventory inv : staged) {
            shipped = shipped.add(inv.getQty());
            inventoryService.deduct(inv.getId(), inv.getQty(), true, order.getCode(), "SHIP");
        }
        for (ShipOrderLine l : load(orderId).getLines()) {
            l.setShippedQty(nz(l.getPickedQty()));
            lineMapper.updateById(l);
        }
        order.setShippedQty(shipped);
        order.setShippedAt(LocalDateTime.now());
        order.setStatus("SHIPPED");
        orderMapper.updateById(order);
        return load(orderId);
    }

    @Transactional
    public void cancel(Long orderId) {
        ShipOrder order = require(orderId);
        if ("ALLOCATED".equals(order.getStatus()) || "PART_ALLOCATED".equals(order.getStatus())) {
            deallocate(orderId);
            order = require(orderId);
        }
        if (!"NEW".equals(order.getStatus())) {
            throw new BizException("已开始拣货的出库单不能取消");
        }
        order.setStatus("CANCELLED");
        orderMapper.updateById(order);
    }

    // ------------------------------------------------------------------ helpers

    private void validate(ShipOrder order) {
        if (order.getWarehouseCode() == null || order.getOwnerCode() == null) {
            throw new BizException("仓库和货主不能为空");
        }
        if (order.getLines() == null || order.getLines().isEmpty()) {
            throw new BizException("出库单至少需要一行明细");
        }
        for (ShipOrderLine l : order.getLines()) {
            if (l.getItemCode() == null || l.getItemCode().isEmpty()) {
                throw new BizException("明细物料不能为空");
            }
            if (l.getOrderQty() == null || l.getOrderQty().signum() <= 0) {
                throw new BizException("明细数量必须大于0");
            }
            Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                    .eq(Item::getOwnerCode, order.getOwnerCode()).eq(Item::getCode, l.getItemCode()));
            if (item == null) {
                throw new BizException("货主 " + order.getOwnerCode() + " 下不存在物料 " + l.getItemCode());
            }
        }
    }

    private void saveLines(ShipOrder order) {
        int no = 1;
        for (ShipOrderLine l : order.getLines()) {
            l.setId(null);
            l.setOrderId(order.getId());
            l.setLineNo(no++);
            l.setAllocatedQty(BigDecimal.ZERO);
            l.setPickedQty(BigDecimal.ZERO);
            l.setShippedQty(BigDecimal.ZERO);
            lineMapper.insert(l);
        }
    }

    private static BigDecimal sum(List<ShipOrderLine> lines) {
        return lines.stream().map(ShipOrderLine::getOrderQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ShipOrder require(Long id) {
        ShipOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException("出库单不存在");
        }
        return o;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
