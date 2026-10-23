package com.wms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Appointment;
import com.wms.basic.entity.CarrierCutoff;
import com.wms.basic.entity.Dock;
import com.wms.basic.entity.Item;
import com.wms.basic.entity.Location;
import com.wms.basic.entity.Lpn;
import com.wms.basic.entity.PickSlot;
import com.wms.basic.entity.VmiAgreement;
import com.wms.basic.entity.Warehouse;
import com.wms.basic.entity.Zone;
import com.wms.basic.mapper.AppointmentMapper;
import com.wms.basic.mapper.CarrierCutoffMapper;
import com.wms.basic.mapper.DockMapper;
import com.wms.basic.mapper.ItemMapper;
import com.wms.basic.mapper.LocationMapper;
import com.wms.basic.mapper.LpnMapper;
import com.wms.basic.mapper.PickSlotMapper;
import com.wms.basic.mapper.VmiAgreementMapper;
import com.wms.basic.mapper.WarehouseMapper;
import com.wms.basic.mapper.ZoneMapper;
import com.wms.common.BizException;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.service.AsnService;
import com.wms.inventory.entity.Consumption;
import com.wms.inventory.entity.ConsumptionLine;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTxn;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryTxnMapper;
import com.wms.inventory.service.ConsumptionService;
import com.wms.inventory.service.InventoryService;
import com.wms.inventory.service.ReplenishService;
import com.wms.inventory.service.VmiService;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.entity.WaveStrategy;
import com.wms.outbound.service.ShipOrderService;
import com.wms.outbound.service.WaveStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class WarehouseTypeFlowTest {
    @Autowired private WarehouseMapper warehouseMapper;
    @Autowired private ZoneMapper zoneMapper;
    @Autowired private LocationMapper locationMapper;
    @Autowired private ItemMapper itemMapper;
    @Autowired private AsnService asnService;
    @Autowired private ShipOrderService orderService;
    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryMapper inventoryMapper;
    @Autowired private InventoryTxnMapper txnMapper;
    @Autowired private ReplenishService replenishService;
    @Autowired private PickSlotMapper pickSlotMapper;
    @Autowired private CarrierCutoffMapper carrierCutoffMapper;
    @Autowired private WaveStrategyService waveStrategyService;
    @Autowired private LpnMapper lpnMapper;
    @Autowired private DockMapper dockMapper;
    @Autowired private AppointmentMapper appointmentMapper;
    @Autowired private VmiAgreementMapper vmiAgreementMapper;
    @Autowired private VmiService vmiService;
    @Autowired private ConsumptionService consumptionService;

    @Test
    void overweightPutawayIsRejectedAndPickFaceWinsSameExpiry() {
        String wh = "WH-CAP";
        warehouse(wh, "GENERAL");
        loc(wh, "STAGING_IN", "RCV-01", null, null, 0);
        loc(wh, "STAGING_OUT", "SHP-01", null, null, 0);
        loc(wh, "STORAGE", "STO-HEAVY", new BigDecimal("1"), null, 1);
        loc(wh, "STORAGE", "STO-OK", new BigDecimal("100"), null, 2);
        loc(wh, "PICKING", "P-LATE", null, null, 50);
        loc(wh, "STORAGE", "STO-EARLY", null, null, 1);
        item("IT-HEAVY", new BigDecimal("2"), null, "C", "EACH", new BigDecimal("1"));
        item("IT-PICK", null, null, "B", "EACH", null);

        Asn asn = receive(wh, "IT-HEAVY", 1, "L1", null);
        PutawayTask task = asnService.tasks(asn.getId()).get(0);
        assertThrows(BizException.class, () -> asnService.putaway(task.getId(), "STO-HEAVY"));
        asnService.putaway(task.getId(), "STO-OK");

        LocalDate expiry = LocalDate.of(2026, 12, 1);
        inventoryService.add(wh, "P-LATE", "OWN01", "IT-PICK", "L2", BigDecimal.ONE, expiry, "", "RECEIVE", null);
        inventoryService.add(wh, "STO-EARLY", "OWN01", "IT-PICK", "L2", BigDecimal.ONE, expiry, "", "RECEIVE", null);
        ShipOrder order = order(wh, "IT-PICK", 1, "SALES");
        assertEquals("SISQ", order.getOrderShape());
        ShipOrder allocated = orderService.allocate(order.getId());
        List<PickTask> tasks = orderService.tasks(allocated.getId());
        assertEquals(1, tasks.size());
        assertEquals("P-LATE", tasks.get(0).getFromLocation());
    }

    @Test
    void fmcgReplenishesAllocatedGapAndCarrierCutoffSplits() {
        String wh = "WH-FMCG";
        warehouse(wh, "FMCG");
        loc(wh, "STAGING_IN", "RCV-01", null, null, 0);
        loc(wh, "STAGING_OUT", "SHP-01", null, null, 0);
        loc(wh, "STORAGE", "STO-01", null, null, 20);
        loc(wh, "PICKING", "P-A", null, null, 1);
        item("IT-A", null, new BigDecimal("4"), "A", "EACH", new BigDecimal("1"));
        item("IT-B", null, null, "B", "EACH", null);
        PickSlot slot = new PickSlot();
        slot.setWarehouseCode(wh);
        slot.setOwnerCode("OWN01");
        slot.setItemCode("IT-A");
        slot.setLocationCode("P-A");
        slot.setMinQty(BigDecimal.ONE);
        slot.setMaxQty(new BigDecimal("20"));
        pickSlotMapper.insert(slot);

        inventoryService.add(wh, "STO-01", "OWN01", "IT-A", "L1", new BigDecimal("20"), null, "", "RECEIVE", null);
        orderService.allocate(order(wh, "IT-A", 8, "SALES").getId());
        List<ReplenishTask> tasks = replenishService.generate(wh, "OWN01", "IT-A");
        assertEquals(1, tasks.size());
        assertTrue(tasks.get(0).getQty().compareTo(new BigDecimal("8")) >= 0);
        assertEquals("P-A", tasks.get(0).getToLocation());
        assertEquals(0, tasks.get(0).getQty().remainder(new BigDecimal("4")).signum());

        inventoryService.add(wh, "STO-01", "OWN01", "IT-B", "L1", new BigDecimal("20"), null, "", "RECEIVE", null);
        ShipOrder early = twoLine(wh, "SF");
        ShipOrder late = twoLine(wh, "JD");
        orderService.allocate(early.getId());
        orderService.allocate(late.getId());
        CarrierCutoff sfCut = new CarrierCutoff();
        sfCut.setWarehouseCode(wh);
        sfCut.setCarrier("SF");
        sfCut.setCutoffHour(10);
        carrierCutoffMapper.insert(sfCut);
        CarrierCutoff jdCut = new CarrierCutoff();
        jdCut.setWarehouseCode(wh);
        jdCut.setCarrier("JD");
        jdCut.setCutoffHour(18);
        carrierCutoffMapper.insert(jdCut);
        WaveStrategy strategy = new WaveStrategy();
        strategy.setCode("CUT-FMCG");
        strategy.setName("承运商截单测试");
        strategy.setPriority(1);
        strategy.setMinOrders(1);
        strategy.setMaxOrders(10);
        strategy.setGroupByCarrier(true);
        strategy.setCutoffHour(12);
        strategy.setEnabled(true);
        strategy.setPackStrategy("ONE_ORDER_ONE_PACKAGE");
        waveStrategyService.save(strategy);
        List<WaveStrategyService.RunResult> results = waveStrategyService.run(wh, null, false, 11);
        WaveStrategyService.RunResult cut = results.stream().filter(r -> "CUT-FMCG".equals(r.getStrategyCode())).findFirst().orElse(null);
        assertTrue(cut != null && cut.getMatchedOrders() == 1);
        assertEquals(1, cut.getWaveIds().size());
    }

    @Test
    void lpnFollowsPutawayAndBulkyTakesWholePallet() {
        String wh = "WH-BULK";
        warehouse(wh, "BULKY");
        loc(wh, "STAGING_IN", "RCV-01", null, null, 0);
        loc(wh, "STAGING_OUT", "SHP-01", null, null, 0);
        loc(wh, "STORAGE", "FLOOR-1", null, 1, 5);
        loc(wh, "PICKING", "P-1", null, null, 1);
        item("IT-PALLET", new BigDecimal("10"), new BigDecimal("10"), "C", "PALLET", null);
        Asn asn = receive(wh, "IT-PALLET", 10, "L1", "PLT-100");
        Inventory staged = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, wh).eq(Inventory::getItemCode, "IT-PALLET")
                .eq(Inventory::getLocationCode, "RCV-01"));
        assertEquals("PLT-100", staged.getLpnCode());
        PutawayTask task = asnService.tasks(asn.getId()).get(0);
        asnService.putaway(task.getId(), "FLOOR-1");
        Lpn lpn = lpnMapper.selectOne(new LambdaQueryWrapper<Lpn>().eq(Lpn::getCode, "PLT-100"));
        assertEquals("FLOOR-1", lpn.getLocationCode());

        inventoryService.add(wh, "P-1", "OWN01", "IT-PALLET", "L1", new BigDecimal("4"),
                LocalDate.of(2026, 6, 1), "", "RECEIVE", null);
        ShipOrder allocated = orderService.allocate(order(wh, "IT-PALLET", 10, "SALES").getId());
        List<PickTask> picks = orderService.tasks(allocated.getId());
        assertEquals(1, picks.size());
        assertEquals("FLOOR-1", picks.get(0).getFromLocation());
        assertEquals(0, new BigDecimal("10").compareTo(picks.get(0).getQty()));
        assertThrows(BizException.class, () -> waveStrategyService.run(wh, null, true, 12));

        Dock dock = new Dock();
        dock.setWarehouseCode(wh);
        dock.setCode("D1");
        dock.setName("1号月台");
        dock.setStatus(1);
        dockMapper.insert(dock);
        Appointment appointment = new Appointment();
        appointment.setCode("AP-BULK-1");
        appointment.setWarehouseCode(wh);
        appointment.setDockCode("D1");
        appointment.setRefType("ASN");
        appointment.setRefCode(asn.getCode());
        appointment.setStatus("BOOKED");
        appointmentMapper.insert(appointment);
        appointment.setStatus("ARRIVED");
        appointmentMapper.updateById(appointment);
        assertEquals("ARRIVED", appointmentMapper.selectById(appointment.getId()).getStatus());
        assertEquals(asn.getCode(), appointment.getRefCode());
    }

    @Test
    void vmiConsumptionTransfersOwnershipAndRtvIgnoresCustomerStock() {
        String wh = "WH-VMI";
        warehouse(wh, "VMI");
        loc(wh, "STAGING_IN", "RCV-01", null, null, 0);
        loc(wh, "STAGING_OUT", "SHP-01", null, null, 0);
        loc(wh, "STORAGE", "STO-01", null, null, 1);
        agreement(wh, "IT-VMI", "CONSUMPTION", new BigDecimal("10"), new BigDecimal("20"));
        agreement(wh, "IT-SHIP", "SHIP", BigDecimal.ZERO, new BigDecimal("5"));
        item("IT-VMI", null, null, "C", "EACH", null);
        item("IT-SHIP", null, null, "C", "EACH", null);
        item("IT-OWN", null, null, "C", "EACH", null);

        Asn asn = receive(wh, "IT-VMI", 5, "L1", null);
        PutawayTask task = asnService.tasks(asn.getId()).get(0);
        asnService.putaway(task.getId(), "STO-01");
        Inventory vendor = find(wh, "STO-01", "IT-VMI");
        assertEquals("VENDOR", vendor.getOwnership());

        Consumption consumption = new Consumption();
        consumption.setWarehouseCode(wh);
        consumption.setOwnerCode("OWN01");
        consumption.setSupplierCode("SUP01");
        ConsumptionLine line = new ConsumptionLine();
        line.setItemCode("IT-VMI");
        line.setQty(new BigDecimal("2"));
        consumption.setLines(Collections.singletonList(line));
        consumption = consumptionService.post(consumptionService.create(consumption).getId());
        assertEquals("POSTED", consumption.getStatus());
        Inventory left = find(wh, "STO-01", "IT-VMI");
        assertEquals("VENDOR", left.getOwnership());
        assertEquals(0, new BigDecimal("3").compareTo(left.getQty()));
        assertTrue(txnMapper.selectCount(new LambdaQueryWrapper<InventoryTxn>()
                .eq(InventoryTxn::getRefNo, consumption.getCode())
                .eq(InventoryTxn::getTxnType, "OWNERSHIP_TRANSFER")) > 0);

        List<Asn> suggested = vmiService.suggest(wh);
        assertEquals(1, suggested.size());
        assertEquals("VMI_SUGGEST", suggested.get(0).getType());
        assertEquals(0, new BigDecimal("17").compareTo(suggested.get(0).getTotalQty()));

        inventoryService.add(wh, "STO-01", "OWN01", "IT-SHIP", "L1", new BigDecimal("2"), null, "", "RECEIVE", null, "", "VENDOR");
        ShipOrder sold = orderService.allocate(order(wh, "IT-SHIP", 2, "SALES").getId());
        assertEquals("ALLOCATED", sold.getStatus());

        inventoryService.add(wh, "STO-01", "OWN01", "IT-OWN", "L1", new BigDecimal("4"), null, "", "RECEIVE", null);
        assertThrows(BizException.class, () -> orderService.allocate(order(wh, "IT-OWN", 1, "RTV").getId()));
    }

    private Inventory find(String wh, String location, String item) {
        return inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, wh)
                .eq(Inventory::getLocationCode, location)
                .eq(Inventory::getItemCode, item)
                .gt(Inventory::getQty, 0)
                .last("LIMIT 1"));
    }

    private void agreement(String wh, String item, String point, BigDecimal reorder, BigDecimal max) {
        VmiAgreement agreement = new VmiAgreement();
        agreement.setWarehouseCode(wh);
        agreement.setOwnerCode("OWN01");
        agreement.setSupplierCode("SUP01");
        agreement.setItemCode(item);
        agreement.setMinQty(BigDecimal.ONE);
        agreement.setMaxQty(max);
        agreement.setReorderPoint(reorder);
        agreement.setLeadTimeDays(3);
        agreement.setTransferPoint(point);
        agreement.setStatus(1);
        vmiAgreementMapper.insert(agreement);
    }

    private ShipOrder twoLine(String wh, String carrier) {
        ShipOrder o = new ShipOrder();
        o.setWarehouseCode(wh);
        o.setOwnerCode("OWN01");
        o.setCustomerCode("CUS01");
        o.setType("SALES");
        o.setPriority(5);
        o.setCarrier(carrier);
        ShipOrderLine a = new ShipOrderLine();
        a.setItemCode("IT-A");
        a.setOrderQty(new BigDecimal("5"));
        ShipOrderLine b = new ShipOrderLine();
        b.setItemCode("IT-B");
        b.setOrderQty(new BigDecimal("5"));
        o.setLines(Arrays.asList(a, b));
        return orderService.create(o);
    }

    private ShipOrder order(String wh, String item, long qty, String type) {
        ShipOrder o = new ShipOrder();
        o.setWarehouseCode(wh);
        o.setOwnerCode("OWN01");
        o.setCustomerCode("CUS01");
        o.setType(type);
        o.setPriority(5);
        o.setDeliveryWindow("明天 14:00-16:00");
        o.setInstallRequired(true);
        ShipOrderLine line = new ShipOrderLine();
        line.setItemCode(item);
        line.setOrderQty(BigDecimal.valueOf(qty));
        o.setLines(Collections.singletonList(line));
        return orderService.create(o);
    }

    private Asn receive(String wh, String item, long qty, String lot, String lpn) {
        Asn asn = new Asn();
        asn.setWarehouseCode(wh);
        asn.setOwnerCode("OWN01");
        asn.setSupplierCode("SUP01");
        asn.setType("PURCHASE");
        AsnLine line = new AsnLine();
        line.setItemCode(item);
        line.setExpectedQty(BigDecimal.valueOf(qty));
        asn.setLines(Collections.singletonList(line));
        asn = asnService.create(asn);
        AsnService.ReceiveLine receipt = new AsnService.ReceiveLine();
        receipt.setLineId(asn.getLines().get(0).getId());
        receipt.setQty(BigDecimal.valueOf(qty));
        receipt.setLotNo(lot);
        receipt.setLpnCode(lpn);
        receipt.setLpnType("PALLET");
        asnService.receive(asn.getId(), Collections.singletonList(receipt));
        return asnService.load(asn.getId());
    }

    private void warehouse(String code, String profile) {
        Warehouse wh = new Warehouse();
        wh.setCode(code);
        wh.setName(code);
        wh.setStatus(1);
        wh.setProfile(profile);
        wh.setApprovalRequired(false);
        warehouseMapper.insert(wh);
        Zone zone = new Zone();
        zone.setWarehouseCode(code);
        zone.setCode("Z1");
        zone.setName("主库区");
        zone.setType("STORAGE");
        zone.setStatus(1);
        zoneMapper.insert(zone);
    }

    private void loc(String wh, String type, String code, BigDecimal maxWeight, Integer palletSlots, int pickSeq) {
        Location loc = new Location();
        loc.setWarehouseCode(wh);
        loc.setZoneCode("Z1");
        loc.setCode(code);
        loc.setType(type);
        loc.setStatus("AVAILABLE");
        loc.setMaxWeight(maxWeight);
        loc.setPalletSlots(palletSlots);
        loc.setPickSeq(pickSeq);
        loc.setMixSku(true);
        loc.setMixLot(true);
        loc.setHandling("STORAGE".equals(type) ? "FLOOR" : "BIN");
        locationMapper.insert(loc);
    }

    private void item(String code, BigDecimal weight, BigDecimal pack, String abc, String handling, BigDecimal min) {
        Item item = new Item();
        item.setOwnerCode("OWN01");
        item.setCode(code);
        item.setName(code);
        item.setUnit("EA");
        item.setStatus(1);
        item.setWeight(weight);
        item.setPackQty(pack);
        item.setAbcClass(abc);
        item.setHandlingClass(handling);
        item.setMinStock(min);
        item.setMaxStock(min);
        item.setLotControl(false);
        itemMapper.insert(item);
    }
}
