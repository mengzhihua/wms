package com.wms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.basic.entity.Item;
import com.wms.basic.mapper.ItemMapper;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.service.AsnService;
import com.wms.outbound.entity.Package;
import com.wms.outbound.entity.PackageLine;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.entity.Shortage;
import com.wms.outbound.entity.SowTask;
import com.wms.outbound.entity.Wave;
import com.wms.outbound.entity.WaveStrategy;
import com.wms.outbound.service.PackageService;
import com.wms.outbound.service.ShipOrderService;
import com.wms.outbound.service.ShortageService;
import com.wms.outbound.service.WaveService;
import com.wms.outbound.service.WaveStrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 波次策略自动成波/拆波、缺货登记、包裹建包闭环 */
@SpringBootTest
@ActiveProfiles("test")
class WaveStrategyShortagePackageFlowTest {
    @Autowired
    AsnService asnService;
    @Autowired
    ShipOrderService orderService;
    @Autowired
    WaveService waveService;
    @Autowired
    WaveStrategyService strategyService;
    @Autowired
    ShortageService shortageService;
    @Autowired
    PackageService packageService;

    @Autowired
    ItemMapper itemMapper;

    /** 本测试专用物料(无 Min/Max)，避免残留库存影响补货等其他用例 */
    @BeforeEach
    void ensureItems() {
        for (String code : new String[]{"TWS01", "TWS02", "TWS03"}) {
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
        r.setLotNo("LOT-" + item);
        asn = asnService.receive(asn.getId(), Collections.singletonList(r));
        for (PutawayTask t : asnService.tasks(asn.getId())) {
            if ("NEW".equals(t.getStatus())) {
                asnService.putaway(t.getId(), t.getSuggestLocation());
            }
        }
    }

    private ShipOrder order(int priority, String... itemQty) {
        ShipOrder o = new ShipOrder();
        o.setWarehouseCode("WH01");
        o.setOwnerCode("OWN01");
        o.setCustomerCode("CUS01");
        o.setType("SALES");
        o.setPriority(priority);
        List<ShipOrderLine> lines = new ArrayList<>();
        for (String s : itemQty) {
            String[] p = s.split(":");
            ShipOrderLine l = new ShipOrderLine();
            l.setItemCode(p[0]);
            l.setOrderQty(new BigDecimal(p[1]));
            lines.add(l);
        }
        o.setLines(lines);
        return orderService.allocate(orderService.create(o).getId());
    }

    private Long waveOf(Long orderId) {
        return orderService.tasks(orderId).stream().map(PickTask::getWaveId).filter(w -> w != null).findFirst().orElse(null);
    }

    @Test
    void strategyGroupsSingleItemOrdersAndSplitsByMaxOrders() {
        receiveAndPutaway("TWS01", 500);
        WaveStrategy s = new WaveStrategy();
        s.setCode("T_SIW_" + System.nanoTime());
        s.setName("测试一品波次");
        s.setPriority(1);
        s.setMaxOrders(2);
        s.setMaxSkuPerOrder(1);
        s.setGroupByItem(true);
        s.setPackStrategy("SPLIT_BY_WEIGHT");
        s.setMaxPackageWeight(q(5));
        s = strategyService.save(s);
        assertTrue(s.getEnabled());
        assertThrows(BizException.class, () -> {
            WaveStrategy dup = new WaveStrategy();
            dup.setCode("SISQ");
            dup.setName("重复编码");
            strategyService.save(dup);
        });

        strategyService.run("WH01", s.getId(), false); // 清掉演示数据/其他用例遗留的候选单
        ShipOrder a = order(1, "TWS01:3");
        ShipOrder b = order(2, "TWS01:4");
        ShipOrder c = order(3, "TWS01:5");
        ShipOrder multi = order(1, "TWS01:1", "TWS02:1");

        List<WaveStrategyService.RunResult> preview = strategyService.run("WH01", s.getId(), true);
        assertEquals(1, preview.size());
        assertTrue(preview.get(0).getWaveCodes().isEmpty(), "预览不建波");
        assertNull(waveOf(a.getId()));

        List<WaveStrategyService.RunResult> res = strategyService.run("WH01", s.getId(), false);
        assertTrue(res.get(0).getMatchedOrders() >= 3);
        Long wa = waveOf(a.getId());
        Long wb = waveOf(b.getId());
        Long wc = waveOf(c.getId());
        assertNotNull(wa);
        assertEquals(wa, wb, "优先级前两单同波");
        assertNotEquals(wa, wc, "超过 maxOrders=2 自动拆到下一波");
        assertNull(waveOf(multi.getId()), "多品订单不匹配单品策略");
        Wave wave = waveService.load(wa);
        assertEquals(s.getCode(), wave.getStrategyCode());
        assertEquals("SPLIT_BY_WEIGHT", wave.getPackStrategy());

        // 再跑一次无可匹配订单, 不重复成波
        List<WaveStrategyService.RunResult> again = strategyService.run("WH01", s.getId(), false);
        assertEquals(0, again.get(0).getMatchedOrders());
        assertEquals(wa, waveOf(a.getId()));

        // 波次总拣登记缺货 2 件: 分摊到低优先级的 b, 并生成缺货记录
        Wave w = waveService.load(wa);
        List<Shortage> shorts = shortageService.registerWave(w.getPickTasks().get(0).getId(), q(2), "库位空缺");
        assertEquals(1, shorts.size());
        assertEquals(b.getId(), shorts.get(0).getOrderId());
        assertEquals(0, q(2).compareTo(shorts.get(0).getQty()));
        assertEquals("OPEN", shorts.get(0).getStatus());
        assertEquals(0, q(2).compareTo(orderService.load(b.getId()).getPickedQty()));
        assertEquals(0, q(3).compareTo(orderService.load(a.getId()).getPickedQty()));

        w = waveService.load(wa);
        for (SowTask st : w.getSowTasks()) {
            w = waveService.sow(st.getId(), null);
        }
        assertEquals("SOWED", w.getStatus());

        // 按波次建包: TWS01 单重 0.5，策略 maxPackageWeight=5 拆箱
        List<Package> pkgs = packageService.buildForWave(wa, null, s.getMaxPackageWeight());
        assertFalse(pkgs.isEmpty());
        Set<Long> pkgOrders = pkgs.stream().map(Package::getOrderId).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList(a.getId(), b.getId())), pkgOrders);
        assertTrue(pkgs.stream().allMatch(p -> p.getCode().startsWith("SH")));
        assertEquals("PACKED", orderService.load(a.getId()).getStatus());
        BigDecimal aQty = pkgs.stream().filter(p -> p.getOrderId().equals(a.getId()))
                .flatMap(p -> p.getLines().stream()).map(PackageLine::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, q(3).compareTo(aQty), "包裹明细数量之和 = 实拣");
        assertThrows(BizException.class, () -> packageService.buildForWave(wa, null, null), "已建包不可重复建包");

        Wave shipped = waveService.ship(wa);
        assertEquals("SHIPPED", shipped.getStatus());
        assertTrue(packageService.byOrder(a.getId()).stream().allMatch(p -> "SHIPPED".equals(p.getStatus())));

        shortageService.close(shorts.get(0).getId(), "已补货");
        assertThrows(BizException.class, () -> shortageService.close(shorts.get(0).getId(), null));
    }

    @Test
    void shortageOnNormalPickTaskAndManualPackaging() {
        receiveAndPutaway("TWS02", 100);
        ShipOrder o = order(5, "TWS02:10");
        PickTask t = orderService.tasks(o.getId()).get(0);
        assertThrows(BizException.class, () -> shortageService.register(t.getId(), q(11), "超计划"));
        assertThrows(BizException.class, () -> shortageService.register(t.getId(), q(0), "零"));
        Shortage s = shortageService.register(t.getId(), q(4), "破损");
        assertEquals(0, q(4).compareTo(s.getQty()));
        PickTask done = orderService.tasks(o.getId()).get(0);
        assertEquals("DONE", done.getStatus());
        assertEquals(0, q(6).compareTo(done.getPickedQty()));
        assertEquals(0, q(4).compareTo(done.getShortQty()));
        assertEquals("PICKED", orderService.load(o.getId()).getStatus());

        // 手工分箱: 数量必须等于实拣 6
        PackageService.ManualPackage b1 = new PackageService.ManualPackage();
        PackageLine l1 = new PackageLine();
        l1.setItemCode("TWS02");
        l1.setQty(q(2));
        b1.setLines(Collections.singletonList(l1));
        PackageService.ManualPackage b2 = new PackageService.ManualPackage();
        PackageLine l2 = new PackageLine();
        l2.setItemCode("TWS02");
        l2.setQty(q(3));
        b2.setLines(Collections.singletonList(l2));
        assertThrows(BizException.class, () -> packageService.manual(o.getId(), Arrays.asList(b1, b2), null), "2+3 != 6");
        l2.setQty(q(4));
        List<Package> pkgs = packageService.manual(o.getId(), Arrays.asList(b1, b2), "SF");
        assertEquals(2, pkgs.size());
        assertEquals("MANUAL", pkgs.get(0).getType());
        assertEquals(Integer.valueOf(2), pkgs.get(1).getSeqNo());
        ShipOrder packed = orderService.load(o.getId());
        assertEquals("PACKED", packed.getStatus());
        assertEquals(Integer.valueOf(2), packed.getPackageCount());
        assertEquals("SF", packed.getCarrier());

        packageService.unpack(o.getId());
        assertEquals("PICKED", orderService.load(o.getId()).getStatus());
        assertTrue(packageService.byOrder(o.getId()).isEmpty());

        List<Package> one = packageService.buildForOrder(o.getId(), null, null, null, null);
        assertEquals(1, one.size());
        assertEquals("ONE_ORDER_ONE_PACKAGE", one.get(0).getType());
        packageService.updateTracking(one.get(0).getId(), "YTO", "YT123", q(2));
        assertEquals("YT123", packageService.load(one.get(0).getId()).getTrackingNo());
        assertEquals("SHIPPED", orderService.ship(o.getId()).getStatus());
        assertEquals("SHIPPED", packageService.load(one.get(0).getId()).getStatus());
    }
}
