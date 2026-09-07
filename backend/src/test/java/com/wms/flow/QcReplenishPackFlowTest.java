package com.wms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.entity.QcTask;
import com.wms.inbound.service.AsnService;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTxn;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryTxnMapper;
import com.wms.inventory.service.ReplenishService;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.service.ShipOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 收货质检放行/拒收、退货入库、Min/Max 补货、复核打包发运、数据库单号序列。 */
@SpringBootTest
@ActiveProfiles("test")
class QcReplenishPackFlowTest {
    @Autowired
    AsnService asnService;
    @Autowired
    ShipOrderService orderService;
    @Autowired
    ReplenishService replenishService;
    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    InventoryTxnMapper txnMapper;
    @Autowired
    CodeGenerator codeGenerator;

    private static BigDecimal q(long v) {
        return BigDecimal.valueOf(v);
    }

    private Asn newAsn(String type, String item, long qty, String customer) {
        Asn asn = new Asn();
        asn.setWarehouseCode("WH01");
        asn.setOwnerCode("OWN01");
        asn.setSupplierCode("SUP01");
        asn.setCustomerCode(customer);
        asn.setType(type);
        AsnLine line = new AsnLine();
        line.setItemCode(item);
        line.setExpectedQty(q(qty));
        asn.setLines(Collections.singletonList(line));
        return asnService.create(asn);
    }

    private Asn receive(Asn asn, long qty, String lot) {
        AsnService.ReceiveLine r = new AsnService.ReceiveLine();
        r.setLineId(asn.getLines().get(0).getId());
        r.setQty(q(qty));
        r.setLotNo(lot);
        return asnService.receive(asn.getId(), Collections.singletonList(r));
    }

    private void putawayAll(Long asnId) {
        for (PutawayTask t : asnService.tasks(asnId)) {
            if ("NEW".equals(t.getStatus())) {
                asnService.putaway(t.getId(), t.getSuggestLocation());
            }
        }
    }

    @Test
    void qcRequiredItemIsHeldFrozenUntilInspectedThenPartiallyRejected() {
        Asn asn = receive(newAsn("PURCHASE", "SKU004", 10, null), 10, null);
        assertEquals("QC", asn.getStatus());
        assertEquals(0, q(10).compareTo(asn.getQcQty()));
        assertTrue(asnService.tasks(asn.getId()).isEmpty(), "质检前不应生成上架任务");

        List<QcTask> qc = asnService.qcTasks(asn.getId(), "NEW");
        assertEquals(1, qc.size());
        Inventory held = inventoryMapper.selectById(qc.get(0).getInventoryId());
        assertEquals("QC-01", held.getLocationCode());
        assertEquals("FROZEN", held.getStatus());

        // 合格+拒收必须等于质检数量；拒收必须填原因
        Long taskId = qc.get(0).getId();
        assertThrows(BizException.class, () -> asnService.inspect(taskId, q(5), q(2), "破损"));
        assertThrows(BizException.class, () -> asnService.inspect(taskId, q(8), q(2), null));

        QcTask done = asnService.inspect(taskId, q(8), q(2), "外包装破损");
        assertEquals("DONE", done.getStatus());
        assertEquals("system", done.getInspector());

        asn = asnService.load(asn.getId());
        assertEquals("PUTAWAY", asn.getStatus());
        assertEquals(0, q(0).compareTo(asn.getQcQty()));
        assertEquals(0, q(2).compareTo(asn.getRejectedQty()));
        assertEquals(0, q(2).compareTo(asn.getLines().get(0).getRejectedQty()));

        List<PutawayTask> pa = asnService.tasks(asn.getId());
        assertEquals(1, pa.size());
        assertEquals(0, q(8).compareTo(pa.get(0).getQty()));
        assertEquals("QC-01", pa.get(0).getFromLocation());

        putawayAll(asn.getId());
        asn = asnService.load(asn.getId());
        assertEquals("CLOSED", asn.getStatus());
        assertEquals(0, q(8).compareTo(asn.getPutawayQty()));
        assertNull(inventoryMapper.selectById(held.getId()), "质检位库存应被全部移走/扣减");

        List<InventoryTxn> rejects = txnMapper.selectList(new LambdaQueryWrapper<InventoryTxn>()
                .eq(InventoryTxn::getRefNo, asn.getCode()).eq(InventoryTxn::getTxnType, "QC_REJECT"));
        assertEquals(1, rejects.size());
        assertEquals("system", rejects.get(0).getOperator());
    }

    @Test
    void returnAsnRequiresCustomerAndAlwaysGoesThroughQc() {
        assertThrows(BizException.class, () -> newAsn("RETURN", "SKU003", 5, null));

        Asn ret = receive(newAsn("RETURN", "SKU003", 5, "CUS01"), 5, null);
        assertEquals("QC", ret.getStatus());
        assertEquals("CUS01", ret.getCustomerCode());
        List<QcTask> qc = asnService.qcTasks(ret.getId(), "NEW");
        assertEquals(1, qc.size());

        asnService.inspect(qc.get(0).getId(), q(0), q(5), "客户退回全部报废");
        ret = asnService.load(ret.getId());
        assertEquals("CLOSED", ret.getStatus());
        assertEquals(0, q(5).compareTo(ret.getRejectedQty()));
        assertTrue(asnService.tasks(ret.getId()).isEmpty());
    }

    @Test
    void minMaxReplenishmentMovesStorageStockToPickFace() {
        Asn asn = receive(newAsn("PURCHASE", "SKU001", 100, null), 100, "LOTRP01");
        putawayAll(asn.getId());

        List<ReplenishTask> created = replenishService.generate("WH01", "OWN01").stream()
                .filter(t -> "SKU001".equals(t.getItemCode())).collect(Collectors.toList());
        assertFalse(created.isEmpty(), "拣货位低于安全库存应生成补货任务");
        BigDecimal planned = created.stream().map(ReplenishTask::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(planned.signum() > 0 && planned.compareTo(q(2000)) <= 0);
        assertTrue(created.get(0).getToLocation().startsWith("P-"), "目标应为拣货位");

        // 重复生成不重复下发（在途量计入）
        long dup = replenishService.generate("WH01", "OWN01").stream().filter(t -> "SKU001".equals(t.getItemCode())).count();
        assertEquals(0, dup);

        ReplenishTask done = replenishService.confirm(created.get(0).getId(), null);
        assertEquals("DONE", done.getStatus());
        Inventory atPick = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getLocationCode, done.getToLocation()).eq(Inventory::getItemCode, "SKU001")
                .eq(Inventory::getLotNo, "LOTRP01"));
        assertNotNull(atPick);
        assertTrue(atPick.getQty().compareTo(done.getQty()) >= 0);
        assertThrows(BizException.class, () -> replenishService.confirm(done.getId(), null));
    }

    @Test
    void packBeforeShipRecordsCartonAndTracking() {
        Asn asn = receive(newAsn("PURCHASE", "SKU003", 40, null), 40, null);
        putawayAll(asn.getId());

        ShipOrder o = new ShipOrder();
        o.setWarehouseCode("WH01");
        o.setOwnerCode("OWN01");
        o.setCustomerCode("CUS01");
        o.setType("SALES");
        ShipOrderLine l = new ShipOrderLine();
        l.setItemCode("SKU003");
        l.setOrderQty(q(7));
        o.setLines(Collections.singletonList(l));
        o = orderService.allocate(orderService.create(o).getId());
        assertEquals("ALLOCATED", o.getStatus());
        Long orderId = o.getId();
        assertThrows(BizException.class, () -> orderService.pack(orderId, 1, null, null, null));

        for (PickTask t : orderService.tasks(orderId)) {
            orderService.pick(t.getId(), t.getQty());
        }
        assertEquals("PICKED", orderService.load(orderId).getStatus());
        assertThrows(BizException.class, () -> orderService.pack(orderId, 0, null, null, null));

        o = orderService.pack(orderId, 2, new BigDecimal("3.5"), "SF", "SF123456789");
        assertEquals("PACKED", o.getStatus());
        assertEquals(2, o.getPackageCount());
        assertNotNull(o.getPackedAt());

        o = orderService.ship(orderId);
        assertEquals("SHIPPED", o.getStatus());
        assertEquals("SF123456789", o.getTrackingNo());
        assertEquals("SF", o.getCarrier());
        assertNotNull(o.getShippedAt());
        assertEquals(0, q(7).compareTo(o.getShippedQty()));
    }

    @Test
    void codeGeneratorIsSequentialPerPrefixAndDay() {
        String a = codeGenerator.next("TST");
        String b = codeGenerator.next("TST");
        assertTrue(a.matches("TST\\d{8}-\\d{4}"), a);
        assertNotEquals(a, b);
        int na = Integer.parseInt(a.substring(a.length() - 4));
        int nb = Integer.parseInt(b.substring(b.length() - 4));
        assertEquals(na + 1, nb);
    }
}
