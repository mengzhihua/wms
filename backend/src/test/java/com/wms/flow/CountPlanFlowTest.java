package com.wms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.basic.entity.Item;
import com.wms.basic.mapper.ItemMapper;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.service.AsnService;
import com.wms.inventory.entity.CountPlan;
import com.wms.inventory.entity.CountTask;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.RecountTask;
import com.wms.inventory.entity.StockAdjust;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.service.CountPlanService;
import com.wms.inventory.service.InventoryService;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.service.ShipOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 盘点计划闭环: 草稿→审批→生成任务锁库→领取→提交→复盘→确认→调整单审核过账→完成解锁→报告 */
@SpringBootTest
@ActiveProfiles("test")
class CountPlanFlowTest {
    @Autowired
    AsnService asnService;
    @Autowired
    CountPlanService planService;
    @Autowired
    InventoryService inventoryService;
    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    ShipOrderService orderService;

    @Autowired
    ItemMapper itemMapper;

    /** 本测试专用物料(无 Min/Max)，避免残留库存影响补货等其他用例 */
    @BeforeEach
    void ensureItems() {
        for (String code : new String[]{"TCP01", "TCP02", "TCP03"}) {
            if (itemMapper.selectCount(new LambdaQueryWrapper<Item>().eq(Item::getOwnerCode, "OWN01").eq(Item::getCode, code)) == 0) {
                Item i = new Item();
                i.setOwnerCode("OWN01");
                i.setCode(code);
                i.setName(code);
                i.setUnit("EA");
                i.setPackQty(BigDecimal.ONE);
                i.setLotControl(true);
                i.setAbcClass("A");
                i.setWeight(new BigDecimal("0.5"));
                i.setQcRequired(false);
                i.setSnControl(false);
                i.setStatus(1);
                itemMapper.insert(i);
            }
        }
    }

    private static BigDecimal q(long v) {
        return BigDecimal.valueOf(v);
    }

    private void receiveAndPutaway(String item, long qty) {
        Asn asn = new Asn();
        asn.setWarehouseCode("WH01");
        asn.setOwnerCode("OWN01");
        asn.setSupplierCode("SUP01");
        asn.setType("PURCHASE");
        AsnLine line = new AsnLine();
        line.setItemCode(item);
        line.setExpectedQty(q(qty));
        asn.setLines(Collections.singletonList(line));
        asn = asnService.create(asn);
        AsnService.ReceiveLine r = new AsnService.ReceiveLine();
        r.setLineId(asn.getLines().get(0).getId());
        r.setQty(q(qty));
        r.setLotNo("LOT-CP");
        asn = asnService.receive(asn.getId(), Collections.singletonList(r));
        for (PutawayTask t : asnService.tasks(asn.getId())) {
            if ("NEW".equals(t.getStatus())) {
                asnService.putaway(t.getId(), t.getSuggestLocation());
            }
        }
    }

    private CountPlan draft(String item, String type) {
        CountPlan p = new CountPlan();
        p.setWarehouseCode("WH01");
        p.setType(type);
        p.setScopeType("ITEM");
        p.setItemCodes(item);
        p.setName("测试盘点");
        return planService.create(p);
    }

    @Test
    void fullCycleWithRecountAndAdjustment() {
        receiveAndPutaway("TCP02", 40);
        CountPlan p = draft("TCP02", "CYCLE");
        assertEquals("DRAFT", p.getStatus());
        assertTrue(p.getCode().startsWith("CP"));

        assertThrows(BizException.class, () -> planService.generateTasks(p.getId()), "未审批不能生成任务");
        planService.submit(p.getId());
        assertEquals("PENDING", planService.require(p.getId()).getStatus());
        planService.approve(p.getId(), true, "同意");
        assertEquals("APPROVED", planService.require(p.getId()).getStatus());

        CountPlan exec = planService.generateTasks(p.getId());
        assertEquals("EXECUTING", exec.getStatus());
        List<CountTask> tasks = planService.tasks(p.getId());
        assertFalse(tasks.isEmpty());
        assertEquals(tasks.size(), exec.getTaskCount());

        // 锁库: 不可分配 / 移库 / 手工调整
        CountTask t0 = tasks.get(0);
        Inventory locked = inventoryMapper.selectById(t0.getInventoryId());
        assertTrue(locked.getCountLock());
        assertEquals(0, locked.getAvailableQty().signum());
        assertThrows(BizException.class, () -> inventoryService.move(t0.getInventoryId(), q(1), "A-01-02-01", "T", "MOVE"));
        assertThrows(BizException.class, () -> inventoryService.adjust(t0.getInventoryId(), q(1), "x", "T"));
        assertThrows(BizException.class, () -> inventoryService.deduct(t0.getInventoryId(), q(1), false, "T", "PICK"), "锁定库存不可扣减");
        ShipOrder o = new ShipOrder();
        o.setWarehouseCode("WH01");
        o.setOwnerCode("OWN01");
        o.setCustomerCode("CUS01");
        o.setType("SALES");
        ShipOrderLine l = new ShipOrderLine();
        l.setItemCode("TCP02");
        l.setOrderQty(q(1));
        o.setLines(Collections.singletonList(l));
        ShipOrder created = orderService.create(o);
        assertThrows(BizException.class, () -> orderService.allocate(created.getId()), "锁定库存不参与分配");
        orderService.cancel(created.getId());

        // 领取 + 指派 + 提交: 第一条盘少 2, 其余一致
        planService.claim(t0.getId());
        assertEquals("CLAIMED", planService.tasks(p.getId()).get(0).getStatus());
        planService.assign(t0.getId(), "zhangsan");
        for (CountTask t : tasks) {
            BigDecimal qty = t.getId().equals(t0.getId()) ? t.getSystemQty().subtract(q(2)) : t.getSystemQty();
            planService.submitCount(t.getId(), qty);
        }
        assertThrows(BizException.class, () -> planService.submitCount(t0.getId(), q(1)), "已完成任务不可重复录入");
        CountPlan afterCount = planService.require(p.getId());
        assertEquals(tasks.size(), afterCount.getDoneCount());
        assertEquals(1, afterCount.getDiffCount());

        // 复盘: 第一轮复盘仍差 2 → 再来一轮 → 复盘差 1 → 确认为不一致
        List<RecountTask> rc = planService.recounts(p.getId());
        assertEquals(1, rc.size());
        assertThrows(BizException.class, () -> planService.complete(p.getId()), "复盘未确认不能完成");
        RecountTask r1 = planService.submitRecount(rc.get(0).getId(), t0.getSystemQty().subtract(q(2)));
        assertEquals("RECOUNTED", r1.getStatus());
        RecountTask r2 = planService.nextRound(r1.getId());
        assertEquals(2, r2.getRoundNo());
        planService.submitRecount(r2.getId(), t0.getSystemQty().subtract(q(1)));
        RecountTask confirmed = planService.confirmRecount(r2.getId());
        assertEquals("MISMATCH", confirmed.getFinalResult());
        assertEquals(0, q(-1).compareTo(confirmed.getFinalDiff()));

        assertThrows(BizException.class, () -> planService.complete(p.getId()), "有差异但未生成调整单不能完成");
        StockAdjust rejected = planService.generateAdjust(p.getId());
        planService.approveAdjust(rejected.getId(), false, "no");
        assertEquals("REJECTED", planService.requireAdjust(rejected.getId()).getStatus());
        assertThrows(BizException.class, () -> planService.complete(p.getId()), "调整单被驳回不能完成");
        StockAdjust adj = planService.generateAdjust(p.getId());
        assertEquals("PENDING", adj.getStatus());
        assertEquals(1, adj.getLineCount());
        assertEquals(0, q(1).compareTo(adj.getLossQty()));
        assertThrows(BizException.class, () -> planService.complete(p.getId()), "调整单未审核不能完成");

        BigDecimal before = inventoryMapper.selectById(t0.getInventoryId()).getQty();
        planService.approveAdjust(adj.getId(), true, "ok");
        assertEquals("APPROVED", planService.requireAdjust(adj.getId()).getStatus());
        assertEquals(0, before.subtract(q(1)).compareTo(inventoryMapper.selectById(t0.getInventoryId()).getQty()));

        CountPlan done = planService.complete(p.getId());
        assertEquals("COMPLETED", done.getStatus());
        Inventory unlocked = inventoryMapper.selectById(t0.getInventoryId());
        assertFalse(Boolean.TRUE.equals(unlocked.getCountLock()));
        assertNull(unlocked.getCountPlanId());
        assertEquals(0, inventoryMapper.selectCount(new LambdaQueryWrapper<Inventory>().eq(Inventory::getCountPlanId, p.getId())));

        Map<String, Object> report = planService.report(p.getId());
        assertEquals(1L, report.get("finalMismatchCount"));
        assertEquals(2, report.get("maxRecountRound"));
        assertEquals(0, q(1).compareTo((BigDecimal) report.get("lossQty")));
        assertEquals(0, q(100).compareTo((BigDecimal) report.get("progressPercent")));
    }

    @Test
    void cancelReleasesLocksAndDeleteTaskWorksOnlyForPending() {
        receiveAndPutaway("TCP01", 10);
        CountPlan p = draft("TCP01", "CYCLE");
        planService.submit(p.getId());
        planService.approve(p.getId(), true, null);
        planService.generateTasks(p.getId());
        List<CountTask> tasks = planService.tasks(p.getId());
        int n = tasks.size();
        assertTrue(n >= 1);

        planService.deleteTask(tasks.get(0).getId());
        assertFalse(Boolean.TRUE.equals(inventoryMapper.selectById(tasks.get(0).getInventoryId()).getCountLock()));
        assertEquals(n - 1, planService.require(p.getId()).getTaskCount());

        if (n > 1) {
            planService.claim(tasks.get(1).getId());
            assertThrows(BizException.class, () -> planService.deleteTask(tasks.get(1).getId()));
        }
        planService.cancel(p.getId());
        assertEquals("CANCELLED", planService.require(p.getId()).getStatus());
        assertEquals(0, inventoryMapper.selectCount(new LambdaQueryWrapper<Inventory>().eq(Inventory::getCountLock, true)
                .eq(Inventory::getCountPlanId, p.getId())));
    }

    @Test
    void rejectedApprovalGoesBackToDraftAndRandomSampleLimitsTasks() {
        receiveAndPutaway("TCP03", 10);
        CountPlan p = draft("TCP03", "RANDOM");
        p.setSamplePercent(1);
        planService.update(p.getId(), p);
        planService.submit(p.getId());
        assertEquals("DRAFT", planService.approve(p.getId(), false, "范围不对").getStatus());
        planService.submit(p.getId());
        planService.approve(p.getId(), true, null);
        assertEquals(1, planService.generateTasks(p.getId()).getTaskCount());
        planService.cancel(p.getId());
    }

    /** 冻结期间同键入库产生的可用行, 解冻时并回一条 */
    @Test
    void unfreezeMergesIntoSameKeyAvailableRow() {
        Inventory frozen = inventoryService.add("WH01", "RCV-01", "OWN01", "TCP01", "LOT-UF", q(10), null, "", "RECEIVE", null);
        inventoryService.setFrozen(frozen.getId(), true, "test");
        Inventory twin = inventoryService.add("WH01", "RCV-01", "OWN01", "TCP01", "LOT-UF", q(5), null, "", "RECEIVE", null);
        assertNotEquals(frozen.getId(), twin.getId());
        Inventory merged = inventoryService.setFrozen(frozen.getId(), false, "test");
        assertEquals(twin.getId(), merged.getId());
        assertEquals(0, q(15).compareTo(merged.getQty()));
        assertNull(inventoryMapper.selectById(frozen.getId()));
        assertEquals(1, inventoryMapper.selectCount(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getLocationCode, "RCV-01").eq(Inventory::getItemCode, "TCP01").eq(Inventory::getLotNo, "LOT-UF")));
        inventoryService.deduct(merged.getId(), q(15), false, "T", "ADJUST");
    }
}
