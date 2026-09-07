package com.wms.flow;

import com.wms.common.BizException;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.service.AsnService;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.entity.SowTask;
import com.wms.outbound.entity.Wave;
import com.wms.outbound.service.ShipOrderService;
import com.wms.outbound.service.WaveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 服务层业务闭环：收货上架 -> 两单波次总拣/播种/发运；越库收货直发。基于内存 H2 与演示主数据。 */
@SpringBootTest
@ActiveProfiles("test")
class WaveAndCrossDockFlowTest {
    @Autowired
    AsnService asnService;
    @Autowired
    ShipOrderService orderService;
    @Autowired
    WaveService waveService;

    private static BigDecimal q(long v) {
        return BigDecimal.valueOf(v);
    }

    private Asn receiveAndPutaway(String item, long qty, String lot) {
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
        r.setLotNo(lot);
        asn = asnService.receive(asn.getId(), Collections.singletonList(r));
        for (PutawayTask t : asnService.tasks(asn.getId())) {
            if ("NEW".equals(t.getStatus())) {
                asnService.putaway(t.getId(), t.getSuggestLocation());
            }
        }
        return asnService.load(asn.getId());
    }

    private ShipOrder order(String item, long qty, int priority) {
        ShipOrder o = new ShipOrder();
        o.setWarehouseCode("WH01");
        o.setOwnerCode("OWN01");
        o.setCustomerCode("CUS01");
        o.setType("SALES");
        o.setPriority(priority);
        ShipOrderLine l = new ShipOrderLine();
        l.setItemCode(item);
        l.setOrderQty(q(qty));
        o.setLines(Collections.singletonList(l));
        return orderService.create(o);
    }

    @Test
    void twoOrdersMergedIntoWaveThenSowedAndShipped() {
        Asn asn = receiveAndPutaway("SKU003", 100, null);
        assertEquals("CLOSED", asn.getStatus());

        ShipOrder o1 = orderService.allocate(order("SKU003", 10, 1).getId());
        ShipOrder o2 = orderService.allocate(order("SKU003", 15, 5).getId());
        assertEquals("ALLOCATED", o1.getStatus());
        assertEquals("ALLOCATED", o2.getStatus());

        Wave wave = waveService.create("WH01", Arrays.asList(o1.getId(), o2.getId()), null);
        assertEquals("NEW", wave.getStatus());
        assertEquals(2, wave.getOrderCount());
        assertEquals(0, q(25).compareTo(wave.getTotalQty()));
        assertEquals(1, wave.getPickTasks().size(), "同一库存的两单应合并为一个总拣任务");

        // 加入波次后不允许普通拣货 / 取消分配
        List<PickTask> tasks = orderService.tasks(o1.getId());
        assertThrows(BizException.class, () -> orderService.pick(tasks.get(0).getId(), q(10)));
        assertThrows(BizException.class, () -> orderService.deallocate(o1.getId()));

        // 少拣 20/25：按优先级 o1 拿满 10，o2 得 10，缺口 5 释放
        wave = waveService.pick(wave.getPickTasks().get(0).getId(), q(20));
        assertEquals("SOWING", wave.getStatus());
        assertEquals(2, wave.getSowTasks().size());
        SowTask s1 = wave.getSowTasks().stream().filter(s -> s.getOrderId().equals(o1.getId())).findFirst().get();
        SowTask s2 = wave.getSowTasks().stream().filter(s -> s.getOrderId().equals(o2.getId())).findFirst().get();
        assertEquals(0, q(10).compareTo(s1.getQty()));
        assertEquals(0, q(10).compareTo(s2.getQty()));
        assertEquals(1, s1.getSlotNo());
        assertEquals(2, s2.getSlotNo());
        assertEquals(0, q(10).compareTo(orderService.load(o2.getId()).getPickedQty()));

        wave = waveService.sow(s1.getId(), null);
        assertEquals("SOWING", wave.getStatus());
        wave = waveService.sow(s2.getId(), q(4));
        wave = waveService.sow(s2.getId(), q(6));
        assertEquals("SOWED", wave.getStatus());

        wave = waveService.ship(wave.getId());
        assertEquals("SHIPPED", wave.getStatus());
        assertEquals("SHIPPED", orderService.load(o1.getId()).getStatus());
        assertEquals("SHIPPED", orderService.load(o2.getId()).getStatus());
        assertEquals(0, q(10).compareTo(orderService.load(o2.getId()).getShippedQty()));
    }

    @Test
    void cancelledWaveReturnsTasksToNormalPicking() {
        receiveAndPutaway("SKU003", 30, null);
        ShipOrder o = orderService.allocate(order("SKU003", 5, 5).getId());
        Wave wave = waveService.create("WH01", Collections.singletonList(o.getId()), null);
        waveService.cancel(wave.getId());
        assertEquals("CANCELLED", waveService.load(wave.getId()).getStatus());
        PickTask t = orderService.tasks(o.getId()).get(0);
        assertNull(t.getWaveId());
        assertEquals("DONE", orderService.pick(t.getId(), q(5)).getStatus());
        assertEquals("SHIPPED", orderService.ship(o.getId()).getStatus());
    }

    @Test
    void crossDockReceiptFlowsStraightToOutboundStaging() {
        ShipOrder target = order("SKU002", 40, 5);

        Asn asn = new Asn();
        asn.setWarehouseCode("WH01");
        asn.setOwnerCode("OWN01");
        asn.setSupplierCode("SUP01");
        asn.setType("PURCHASE");
        asn.setCrossDockOrderCode(target.getCode());
        AsnLine line = new AsnLine();
        line.setItemCode("SKU002");
        line.setExpectedQty(q(50));
        asn.setLines(Collections.singletonList(line));
        asn = asnService.create(asn);

        AsnService.ReceiveLine r = new AsnService.ReceiveLine();
        r.setLineId(asn.getLines().get(0).getId());
        r.setQty(q(50));
        r.setLotNo("LOTXD01");
        asn = asnService.receive(asn.getId(), Collections.singletonList(r));

        assertEquals(0, q(40).compareTo(asn.getCrossDockQty()));
        List<PutawayTask> tasks = asnService.tasks(asn.getId());
        assertEquals(1, tasks.size());
        assertEquals(0, q(10).compareTo(tasks.get(0).getQty()), "超出订单需求的 10 件应正常上架");

        ShipOrder picked = orderService.load(target.getId());
        assertEquals("PICKED", picked.getStatus());
        assertEquals(0, q(40).compareTo(picked.getPickedQty()));
        assertEquals("SHIPPED", orderService.ship(target.getId()).getStatus());
    }

    @Test
    void crossDockTargetMustBeOpenSameWarehouseOwner() {
        Asn asn = new Asn();
        asn.setWarehouseCode("WH01");
        asn.setOwnerCode("OWN01");
        asn.setSupplierCode("SUP01");
        asn.setType("PURCHASE");
        asn.setCrossDockOrderCode("SO-NOT-EXISTS");
        AsnLine line = new AsnLine();
        line.setItemCode("SKU003");
        line.setExpectedQty(q(1));
        asn.setLines(Collections.singletonList(line));
        assertThrows(BizException.class, () -> asnService.create(asn));
    }
}
