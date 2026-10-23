package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TransferInTransitTest {
    @Autowired
    private ReplenishService replenishService;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private InventoryService inventoryService;

    @Test
    void crossWarehouseStaysInTransitUntilConfirm() {
        List<ReplenishTask> tasks = replenishService.transfer(
                "WH01", "WH02", "SKU003", "OWN01", new BigDecimal("5"));
        assertEquals(1, tasks.size());
        ReplenishTask task = tasks.get(0);
        assertEquals("IN_TRANSIT", task.getStatus());
        assertEquals("WH02", task.getWarehouseCode());
        assertEquals("P-01-01", task.getToLocation());

        Inventory staging = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, "WH02")
                .eq(Inventory::getLocationCode, "RCV-01")
                .eq(Inventory::getItemCode, "SKU003")
                .eq(Inventory::getRefNo, task.getCode()));
        assertTrue(staging != null && staging.getQty().compareTo(new BigDecimal("5")) == 0);

        ReplenishTask done = replenishService.confirm(task.getId(), null);
        assertEquals("DONE", done.getStatus());
        assertEquals("P-01-01", done.getToLocation());
        Inventory pick = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, "WH02")
                .eq(Inventory::getLocationCode, "P-01-01")
                .eq(Inventory::getItemCode, "SKU003")
                .eq(Inventory::getLotNo, staging.getLotNo())
                .eq(Inventory::getRefNo, ""));
        assertTrue(pick != null && pick.getQty().compareTo(new BigDecimal("5")) >= 0);
        List<InventoryService.Allocation> allocs = inventoryService.allocate(
                "WH02", "OWN01", "SKU003", staging.getLotNo(), new BigDecimal("5"), "ALLOC-XFER");
        assertEquals(1, allocs.size());
        assertEquals(0, new BigDecimal("5").compareTo(allocs.get(0).qty));
        assertEquals("P-01-01", allocs.get(0).inventory.getLocationCode());
    }
}
