package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Item;
import com.wms.basic.entity.Location;
import com.wms.basic.mapper.ItemMapper;
import com.wms.basic.mapper.LocationMapper;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.ReplenishTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 补货策略(Min/Max)：拣货位(PICKING)现有量 + 在途补货 < 物料 min_stock 时，
 * 从存储位(STORAGE)按 FEFO 生成补货任务，补到 max_stock（未设置时补到 2 倍 min_stock）。
 */
@Service
@RequiredArgsConstructor
public class ReplenishService {

    private final ReplenishTaskMapper taskMapper;
    private final InventoryMapper inventoryMapper;
    private final LocationMapper locationMapper;
    private final ItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final CodeGenerator codeGenerator;

    /** 扫描仓库(可选货主)下所有设置了安全库存的物料，生成补货任务 */
    @Transactional
    public List<ReplenishTask> generate(String warehouse, String owner) {
        if (warehouse == null || warehouse.isEmpty()) {
            throw new BizException("请选择仓库");
        }
        Map<String, Location> locs = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                        .eq(Location::getWarehouseCode, warehouse))
                .stream().collect(Collectors.toMap(Location::getCode, l -> l, (a, b) -> a));
        List<Item> items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(owner != null && !owner.isEmpty(), Item::getOwnerCode, owner)
                .isNotNull(Item::getMinStock).gt(Item::getMinStock, 0).eq(Item::getStatus, 1));
        List<ReplenishTask> created = new ArrayList<>();
        for (Item item : items) {
            List<Inventory> all = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                    .eq(Inventory::getWarehouseCode, warehouse)
                    .eq(Inventory::getOwnerCode, item.getOwnerCode())
                    .eq(Inventory::getItemCode, item.getCode())
                    .eq(Inventory::getStatus, InventoryService.AVAILABLE)
                    .gt(Inventory::getQty, 0));
            BigDecimal pickQty = all.stream().filter(i -> isType(locs, i.getLocationCode(), "PICKING"))
                    .map(Inventory::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal inTransit = taskMapper.selectList(new LambdaQueryWrapper<ReplenishTask>()
                            .eq(ReplenishTask::getWarehouseCode, warehouse)
                            .eq(ReplenishTask::getOwnerCode, item.getOwnerCode())
                            .eq(ReplenishTask::getItemCode, item.getCode())
                            .eq(ReplenishTask::getStatus, "NEW"))
                    .stream().map(ReplenishTask::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (pickQty.add(inTransit).compareTo(item.getMinStock()) >= 0) {
                continue;
            }
            BigDecimal target = item.getMaxStock() != null && item.getMaxStock().compareTo(item.getMinStock()) > 0
                    ? item.getMaxStock() : item.getMinStock().multiply(BigDecimal.valueOf(2));
            BigDecimal need = target.subtract(pickQty).subtract(inTransit);

            String toLoc = pickLocationFor(warehouse, item, all, locs);
            if (toLoc == null) {
                continue;
            }
            List<Inventory> sources = all.stream()
                    .filter(i -> isType(locs, i.getLocationCode(), "STORAGE") && i.getAvailableQty().signum() > 0)
                    .sorted(Comparator.comparing(Inventory::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Inventory::getReceiveDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Inventory::getId))
                    .collect(Collectors.toList());
            for (Inventory src : sources) {
                if (need.signum() <= 0) {
                    break;
                }
                BigDecimal q = src.getAvailableQty().min(need);
                created.add(insertTask(src, q, toLoc, "MIN/MAX 自动补货"));
                need = need.subtract(q);
            }
        }
        return created;
    }

    /** 手工补货：指定源库存与目标拣货位 */
    @Transactional
    public ReplenishTask create(Long inventoryId, BigDecimal qty, String toLocation) {
        Inventory src = inventoryService.requireInventory(inventoryId);
        if (qty == null || qty.signum() <= 0) {
            throw new BizException("补货数量必须大于0");
        }
        if (src.getAvailableQty().compareTo(qty) < 0) {
            throw new BizException("可用库存不足，可用 " + src.getAvailableQty());
        }
        Location to = inventoryService.requireLocation(src.getWarehouseCode(), toLocation);
        if (!"PICKING".equals(to.getType())) {
            throw new BizException("补货目标必须是拣货类型库位");
        }
        return insertTask(src, qty, toLocation, null);
    }

    @Transactional
    public ReplenishTask confirm(Long taskId, String toLocation) {
        ReplenishTask t = require(taskId);
        if (!"NEW".equals(t.getStatus())) {
            throw new BizException("任务已处理");
        }
        String target = toLocation != null && !toLocation.isEmpty() ? toLocation : t.getToLocation();
        Location loc = inventoryService.requireLocation(t.getWarehouseCode(), target);
        if (!"PICKING".equals(loc.getType())) {
            throw new BizException("补货目标必须是拣货类型库位");
        }
        inventoryService.move(t.getInventoryId(), t.getQty(), target, t.getCode(), "REPLENISH");
        t.setToLocation(target);
        t.setStatus("DONE");
        taskMapper.updateById(t);
        return t;
    }

    @Transactional
    public ReplenishTask cancel(Long taskId) {
        ReplenishTask t = require(taskId);
        if (!"NEW".equals(t.getStatus())) {
            throw new BizException("任务已处理");
        }
        t.setStatus("CANCELLED");
        taskMapper.updateById(t);
        return t;
    }

    // ------------------------------------------------------------------ helpers

    /** 已存放该物料的拣货位优先；否则取第一个空的可用拣货位 */
    private String pickLocationFor(String warehouse, Item item, List<Inventory> itemStock, Map<String, Location> locs) {
        for (Inventory i : itemStock) {
            if (isType(locs, i.getLocationCode(), "PICKING")) {
                return i.getLocationCode();
            }
        }
        List<String> occupied = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getWarehouseCode, warehouse).gt(Inventory::getQty, 0))
                .stream().map(Inventory::getLocationCode).distinct().collect(Collectors.toList());
        List<String> promised = taskMapper.selectList(new LambdaQueryWrapper<ReplenishTask>()
                        .eq(ReplenishTask::getWarehouseCode, warehouse).eq(ReplenishTask::getStatus, "NEW"))
                .stream().map(ReplenishTask::getToLocation).collect(Collectors.toList());
        return locs.values().stream()
                .filter(l -> "PICKING".equals(l.getType()) && "AVAILABLE".equals(l.getStatus()))
                .filter(l -> !occupied.contains(l.getCode()) && !promised.contains(l.getCode()))
                .sorted(Comparator.comparing(Location::getPickSeq, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Location::getCode))
                .map(Location::getCode).findFirst().orElse(null);
    }

    private static boolean isType(Map<String, Location> locs, String code, String type) {
        Location l = locs.get(code);
        return l != null && type.equals(l.getType());
    }

    private ReplenishTask insertTask(Inventory src, BigDecimal qty, String toLoc, String remark) {
        ReplenishTask t = new ReplenishTask();
        t.setCode(codeGenerator.next("RP"));
        t.setWarehouseCode(src.getWarehouseCode());
        t.setOwnerCode(src.getOwnerCode());
        t.setItemCode(src.getItemCode());
        t.setLotNo(src.getLotNo());
        t.setInventoryId(src.getId());
        t.setFromLocation(src.getLocationCode());
        t.setToLocation(toLoc);
        t.setQty(qty);
        t.setStatus("NEW");
        t.setRemark(remark);
        taskMapper.insert(t);
        return t;
    }

    private ReplenishTask require(Long id) {
        ReplenishTask t = taskMapper.selectById(id);
        if (t == null) {
            throw new BizException("补货任务不存在");
        }
        return t;
    }
}
