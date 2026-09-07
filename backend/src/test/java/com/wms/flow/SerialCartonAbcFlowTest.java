package com.wms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Carton;
import com.wms.basic.entity.Item;
import com.wms.basic.mapper.ItemMapper;
import com.wms.common.BizException;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.service.AsnService;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTxn;
import com.wms.inventory.entity.Serial;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryTxnMapper;
import com.wms.inventory.mapper.SerialMapper;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.service.ShipOrderService;
import com.wms.report.ReportController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 序列号(SN)收货/发运、箱型推荐与包材扣减、ABC 分析与计件报表。 */
@SpringBootTest
@ActiveProfiles("test")
class SerialCartonAbcFlowTest {
    @Autowired
    AsnService asnService;
    @Autowired
    ShipOrderService orderService;
    @Autowired
    ReportController reportController;
    @Autowired
    SerialMapper serialMapper;
    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    InventoryTxnMapper txnMapper;
    @Autowired
    ItemMapper itemMapper;

    private static BigDecimal q(long v) {
        return BigDecimal.valueOf(v);
    }

    private Asn newAsn(String item, long qty) {
        Asn asn = new Asn();
        asn.setWarehouseCode("WH01");
        asn.setOwnerCode("OWN01");
        asn.setSupplierCode("SUP01");
        asn.setType("PURCHASE");
        AsnLine line = new AsnLine();
        line.setItemCode(item);
        line.setExpectedQty(q(qty));
        asn.setLines(Collections.singletonList(line));
        return asnService.create(asn);
    }

    private Asn receive(Asn asn, long qty, List<String> sns) {
        return receive(asn, qty, null, sns);
    }

    private Asn receive(Asn asn, long qty, String lot, List<String> sns) {
        AsnService.ReceiveLine r = new AsnService.ReceiveLine();
        r.setLineId(asn.getLines().get(0).getId());
        r.setQty(q(qty));
        r.setLotNo(lot);
        r.setSerialNos(sns);
        return asnService.receive(asn.getId(), Collections.singletonList(r));
    }

    private void putawayAll(Long asnId) {
        for (PutawayTask t : asnService.tasks(asnId)) {
            if ("NEW".equals(t.getStatus())) {
                asnService.putaway(t.getId(), t.getSuggestLocation());
            }
        }
    }

    private ShipOrder pickedOrder(String item, long qty) {
        ShipOrder o = new ShipOrder();
        o.setWarehouseCode("WH01");
        o.setOwnerCode("OWN01");
        o.setCustomerCode("CUS01");
        o.setType("SALES");
        ShipOrderLine l = new ShipOrderLine();
        l.setItemCode(item);
        l.setOrderQty(q(qty));
        o.setLines(Collections.singletonList(l));
        o = orderService.allocate(orderService.create(o).getId());
        for (PickTask t : orderService.tasks(o.getId())) {
            orderService.pick(t.getId(), t.getQty());
        }
        return orderService.load(o.getId());
    }

    private Serial sn(String no) {
        return serialMapper.selectOne(new LambdaQueryWrapper<Serial>().eq(Serial::getOwnerCode, "OWN01").eq(Serial::getSerialNo, no));
    }

    @Test
    void serialControlledItemRequiresSnOnReceiptAndShipment() {
        Asn a = newAsn("SKU005", 3);
        assertThrows(BizException.class, () -> receive(a, 3, null), "缺少 SN 应拒绝收货");
        assertThrows(BizException.class, () -> receive(a, 3, Arrays.asList("IMEI-1", "IMEI-2")), "SN 个数不足");
        assertThrows(BizException.class, () -> receive(a, 3, Arrays.asList("IMEI-1", "IMEI-1", "IMEI-2")), "SN 重复");

        Asn asn = receive(a, 3, Arrays.asList("IMEI-1", "IMEI-2", "IMEI-3"));
        assertEquals("PUTAWAY", asn.getStatus());
        assertEquals("IN_STOCK", sn("IMEI-1").getStatus());
        assertEquals(asn.getCode(), sn("IMEI-1").getAsnCode());
        assertEquals("RCV-01", sn("IMEI-1").getLocationCode());

        // 同一 SN 不能重复收货
        Asn again = newAsn("SKU005", 1);
        assertThrows(BizException.class, () -> receive(again, 1, Collections.singletonList("IMEI-2")));
        putawayAll(asn.getId());
        // 上架后 SN 跟随库存移动到存储位
        assertNotEquals("RCV-01", sn("IMEI-1").getLocationCode());
        assertEquals(sn("IMEI-1").getLocationCode(), sn("IMEI-3").getLocationCode());

        ShipOrder o = pickedOrder("SKU005", 2);
        Long orderId = o.getId();
        assertThrows(BizException.class, () -> orderService.ship(orderId), "发运未扫描 SN 应拒绝");
        assertThrows(BizException.class, () -> orderService.ship(orderId, null, null, Arrays.asList("IMEI-1", "IMEI-9")), "不在库 SN");
        assertThrows(BizException.class, () -> orderService.ship(orderId, null, null, Collections.singletonList("IMEI-1")), "SN 数量不足");

        o = orderService.ship(orderId, "SF", "SF001", Arrays.asList("IMEI-1", "IMEI-3"));
        assertEquals("SHIPPED", o.getStatus());
        assertEquals("SHIPPED", sn("IMEI-1").getStatus());
        assertEquals(o.getCode(), sn("IMEI-1").getOrderCode());
        assertEquals("IN_STOCK", sn("IMEI-2").getStatus());

        // 已发运 SN 不能再次发运；退货入库后同一 SN 回到在库
        ShipOrder o2 = pickedOrder("SKU005", 1);
        assertThrows(BizException.class, () -> orderService.ship(o2.getId(), null, null, Collections.singletonList("IMEI-1")));
        orderService.ship(o2.getId(), null, null, Collections.singletonList("IMEI-2"));

        Asn ret = new Asn();
        ret.setWarehouseCode("WH01");
        ret.setOwnerCode("OWN01");
        ret.setSupplierCode("SUP01");
        ret.setCustomerCode("CUS01");
        ret.setType("RETURN");
        AsnLine line = new AsnLine();
        line.setItemCode("SKU005");
        line.setExpectedQty(q(1));
        ret.setLines(Collections.singletonList(line));
        ret = receive(asnService.create(ret), 1, Collections.singletonList("IMEI-1"));
        assertEquals("QC", ret.getStatus());
        assertEquals("IN_STOCK", sn("IMEI-1").getStatus());
        assertEquals("QC-01", sn("IMEI-1").getLocationCode());
    }

    @Test
    void cartonSuggestionPicksSmallestFitAndPackConsumesPackagingStock() {
        // 包材入库 5 个；SKU003 体积 0.0001/重 0.03 -> 20 件体积 0.002 重 0.6，BOX-S(0.003, 5kg) 可装
        putawayAll(receive(newAsn("PKG-BOX-M", 5), 5, null).getId());
        putawayAll(receive(newAsn("SKU003", 60), 60, null).getId());

        ShipOrder small = pickedOrder("SKU003", 20);
        Map<String, Object> s = orderService.suggestCarton(small.getId());
        assertEquals("BOX-S", ((Carton) s.get("carton")).getCode());
        assertEquals(1, s.get("packageCount"));

        // SKU002 键盘 0.003m³/件，40 件 0.12m³ 超过最大箱 BOX-L(0.096)：推荐 BOX-L x2
        putawayAll(receive(newAsn("SKU002", 40), 40, "LOTSN01", null).getId());
        ShipOrder big = pickedOrder("SKU002", 40);
        Map<String, Object> b = orderService.suggestCarton(big.getId());
        assertEquals("BOX-L", ((Carton) b.get("carton")).getCode());
        assertEquals(2, b.get("packageCount"));

        // 用关联包材的 BOX-M 打包 3 箱：扣减 3 个 PKG-BOX-M，产生 PACK_CONSUME 流水
        ShipOrder packed = orderService.pack(small.getId(), 3, new BigDecimal("1.2"), "SF", "SF002", "BOX-M");
        assertEquals("PACKED", packed.getStatus());
        assertEquals("BOX-M", packed.getCartonCode());
        BigDecimal left = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getOwnerCode, "OWN01").eq(Inventory::getItemCode, "PKG-BOX-M"))
                .stream().map(Inventory::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, q(2).compareTo(left));
        List<InventoryTxn> txns = txnMapper.selectList(new LambdaQueryWrapper<InventoryTxn>()
                .eq(InventoryTxn::getTxnType, "PACK_CONSUME").eq(InventoryTxn::getRefNo, packed.getCode()));
        assertEquals(1, txns.size());
        assertEquals(0, q(3).compareTo(txns.get(0).getQty()));

        // 包材不足时拒绝
        ShipOrder another = pickedOrder("SKU003", 10);
        assertThrows(BizException.class, () -> orderService.pack(another.getId(), 3, null, null, null, "BOX-M"));
        assertThrows(BizException.class, () -> orderService.pack(another.getId(), 1, null, null, null, "NO-SUCH"));
        assertEquals("PACKED", orderService.pack(another.getId(), 1, null, null, null, "BOX-S").getStatus());
    }

    @Test
    void abcAnalysisRanksByShipQtyAndLaborGroupsByOperator() {
        putawayAll(receive(newAsn("SKU002", 100), 100, "LOTABC01", null).getId());
        putawayAll(receive(newAsn("SKU003", 100), 100, null).getId());
        orderService.ship(pickedOrder("SKU002", 90).getId());
        orderService.ship(pickedOrder("SKU003", 5).getId());

        List<Map<String, Object>> abc = reportController.abcRows(null, "OWN01", 30);
        assertEquals("SKU002", abc.get(0).get("itemCode"));
        assertEquals("A", abc.get(0).get("suggestedClass"));
        Map<String, Object> sku003 = abc.stream().filter(r -> "SKU003".equals(r.get("itemCode"))).findFirst().orElseThrow(AssertionError::new);
        assertEquals("C", sku003.get("suggestedClass"));
        // 未发运的物料也出现在结果中，建议 C
        assertTrue(abc.stream().anyMatch(r -> "SKU004".equals(r.get("itemCode")) && "C".equals(r.get("suggestedClass"))));

        List<Map<String, Object>> labor = reportController.labor(7, "system").getData();
        assertFalse(labor.isEmpty());
        Map<String, Object> today = labor.get(0);
        assertEquals("system", today.get("operator"));
        assertTrue(((Long) today.get("totalCount")) >= 4);
        assertNotNull(today.get("SHIP_count"));

        Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>().eq(Item::getOwnerCode, "OWN01").eq(Item::getCode, "SKU003"));
        assertEquals("B", item.getAbcClass());
    }
}
