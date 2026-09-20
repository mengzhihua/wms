package com.wms.outbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Item;
import com.wms.basic.mapper.ItemMapper;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import com.wms.outbound.entity.Package;
import com.wms.outbound.entity.PackageLine;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.entity.Wave;
import com.wms.outbound.mapper.PackageLineMapper;
import com.wms.outbound.mapper.PackageMapper;
import com.wms.outbound.mapper.WaveMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 包裹对象: 出库单拣货完成后按打包策略生成包裹(SH+日期+序号), 每个包裹带明细;
 * 支持按波次批量建包、单据建包、手工分箱; 建包同时完成出库单复核打包(PICKED -> PACKED)。
 */
@Service
@RequiredArgsConstructor
public class PackageService {
    public static final String ONE_ORDER_ONE_PACKAGE = "ONE_ORDER_ONE_PACKAGE";
    public static final String SPLIT_BY_WEIGHT = "SPLIT_BY_WEIGHT";
    public static final String MANUAL = "MANUAL";
    private static final BigDecimal DEFAULT_MAX_WEIGHT = BigDecimal.valueOf(15);

    private final PackageMapper packageMapper;
    private final PackageLineMapper lineMapper;
    private final WaveMapper waveMapper;
    private final ItemMapper itemMapper;
    private final ShipOrderService orderService;
    private final CodeGenerator codeGenerator;

    @Data
    public static class ManualPackage {
        private String cartonCode;
        private BigDecimal weight;
        private List<PackageLine> lines;
    }

    // ------------------------------------------------------------------ query

    public Package load(Long id) {
        Package p = packageMapper.selectById(id);
        if (p == null) {
            throw new BizException("包裹不存在: " + id);
        }
        p.setLines(lineMapper.selectList(new LambdaQueryWrapper<PackageLine>().eq(PackageLine::getPackageId, id)));
        return p;
    }

    public List<Package> byOrder(Long orderId) {
        List<Package> list = packageMapper.selectList(new LambdaQueryWrapper<Package>()
                .eq(Package::getOrderId, orderId).ne(Package::getStatus, "CANCELLED").orderByAsc(Package::getSeqNo));
        list.forEach(p -> p.setLines(lineMapper.selectList(new LambdaQueryWrapper<PackageLine>().eq(PackageLine::getPackageId, p.getId()))));
        return list;
    }

    // ------------------------------------------------------------------ build

    /** 波次播种完成后, 按波次策略的打包策略为每张出库单建包 */
    @Transactional
    public List<Package> buildForWave(Long waveId, String packStrategy, BigDecimal maxWeight) {
        Wave wave = waveMapper.selectById(waveId);
        if (wave == null) {
            throw new BizException("波次不存在");
        }
        if (!"SOWED".equals(wave.getStatus())) {
            throw new BizException("播种完成后才能建包, 当前 " + wave.getStatus());
        }
        String strategy = packStrategy != null && !packStrategy.isEmpty() ? packStrategy
                : (wave.getPackStrategy() == null ? ONE_ORDER_ONE_PACKAGE : wave.getPackStrategy());
        List<Package> out = new ArrayList<>();
        for (ShipOrder o : orderService.loadWaveOrders(waveId)) {
            if ("PICKED".equals(o.getStatus())) {
                out.addAll(buildForOrder(o.getId(), strategy, maxWeight, null, null));
            }
        }
        if (out.isEmpty()) {
            throw new BizException("波次内没有待建包的出库单");
        }
        return out;
    }

    /** 单据建包: 一单一包 / 按重量拆包 */
    @Transactional
    public List<Package> buildForOrder(Long orderId, String strategy, BigDecimal maxWeight, String carrier, String cartonCode) {
        ShipOrder order = orderService.load(orderId);
        if (!"PICKED".equals(order.getStatus())) {
            throw new BizException("拣货完成后才能建包, 当前 " + order.getStatus());
        }
        if (!byOrder(orderId).isEmpty()) {
            throw new BizException("出库单已建包, 请先撤销");
        }
        String st = strategy == null || strategy.isEmpty() ? ONE_ORDER_ONE_PACKAGE : strategy;
        List<PackageLine> all = pickedLines(order);
        List<List<PackageLine>> groups = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        if (ONE_ORDER_ONE_PACKAGE.equals(st)) {
            groups.add(all);
            weights.add(weightOf(order, all));
        } else if (SPLIT_BY_WEIGHT.equals(st)) {
            splitByWeight(order, all, maxWeight == null || maxWeight.signum() <= 0 ? DEFAULT_MAX_WEIGHT : maxWeight, groups, weights);
        } else {
            throw new BizException("未知打包策略: " + st + ", 手工分箱请使用 manual");
        }
        return persist(order, st, groups, weights, carrier, cartonCode);
    }

    /** 手工分箱: 每箱明细数量之和必须等于各行实拣数量 */
    @Transactional
    public List<Package> manual(Long orderId, List<ManualPackage> boxes, String carrier) {
        ShipOrder order = orderService.load(orderId);
        if (!"PICKED".equals(order.getStatus())) {
            throw new BizException("拣货完成后才能建包, 当前 " + order.getStatus());
        }
        if (!byOrder(orderId).isEmpty()) {
            throw new BizException("出库单已建包, 请先撤销");
        }
        if (boxes == null || boxes.isEmpty()) {
            throw new BizException("至少一个包裹");
        }
        Map<String, BigDecimal> need = new HashMap<>();
        for (PackageLine l : pickedLines(order)) {
            need.merge(key(l.getItemCode(), l.getLotNo()), l.getQty(), BigDecimal::add);
        }
        Map<String, BigDecimal> got = new HashMap<>();
        List<List<PackageLine>> groups = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        for (ManualPackage b : boxes) {
            if (b.getLines() == null || b.getLines().isEmpty()) {
                throw new BizException("包裹明细不能为空");
            }
            for (PackageLine l : b.getLines()) {
                if (l.getQty() == null || l.getQty().signum() <= 0) {
                    throw new BizException("包裹明细数量必须大于0: " + l.getItemCode());
                }
                String k = key(l.getItemCode(), l.getLotNo());
                if (!need.containsKey(k)) {
                    throw new BizException("出库单未拣该商品/批次: " + l.getItemCode() + " " + (l.getLotNo() == null ? "" : l.getLotNo()));
                }
                got.merge(k, l.getQty(), BigDecimal::add);
            }
            groups.add(b.getLines());
            weights.add(b.getWeight() != null ? b.getWeight() : weightOf(order, b.getLines()));
        }
        for (Map.Entry<String, BigDecimal> e : need.entrySet()) {
            BigDecimal g = got.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (g.compareTo(e.getValue()) != 0) {
                throw new BizException("商品 " + e.getKey().replace("|", " ") + " 分箱数量 " + g + " ≠ 实拣 " + e.getValue());
            }
        }
        List<Package> out = persist(order, MANUAL, groups, weights, carrier, null);
        for (int i = 0; i < out.size(); i++) {
            String carton = boxes.get(i).getCartonCode();
            if (carton != null && !carton.isEmpty()) {
                out.get(i).setCartonCode(carton);
                packageMapper.updateById(out.get(i));
            }
        }
        return out;
    }

    /** 撤销建包: 出库单 PACKED -> PICKED */
    @Transactional
    public void unpack(Long orderId) {
        ShipOrder order = orderService.load(orderId);
        if (!"PACKED".equals(order.getStatus())) {
            throw new BizException("仅已打包未发运的出库单可撤销建包");
        }
        for (Package p : byOrder(orderId)) {
            p.setStatus("CANCELLED");
            packageMapper.updateById(p);
        }
        orderService.unpack(orderId);
    }

    @Transactional
    public Package updateTracking(Long packageId, String carrier, String trackingNo, BigDecimal weight) {
        Package p = load(packageId);
        if (!"NEW".equals(p.getStatus())) {
            throw new BizException("包裹状态 " + p.getStatus() + " 不可修改");
        }
        if (carrier != null) p.setCarrier(carrier);
        if (trackingNo != null) p.setTrackingNo(trackingNo);
        if (weight != null) p.setWeight(weight);
        packageMapper.updateById(p);
        return p;
    }

    // ------------------------------------------------------------------ internals

    private List<Package> persist(ShipOrder order, String type, List<List<PackageLine>> groups, List<BigDecimal> weights,
                                  String carrier, String cartonCode) {
        List<Package> out = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        for (int i = 0; i < groups.size(); i++) {
            Package p = new Package();
            p.setCode(codeGenerator.next("SH"));
            p.setOrderId(order.getId());
            p.setOrderCode(order.getCode());
            p.setWaveId(waveIdOf(order));
            p.setSeqNo(i + 1);
            p.setType(type);
            p.setCartonCode(cartonCode);
            p.setWeight(weights.get(i));
            p.setCarrier(carrier != null && !carrier.isEmpty() ? carrier : order.getCarrier());
            p.setStatus("NEW");
            packageMapper.insert(p);
            for (PackageLine l : groups.get(i)) {
                PackageLine pl = new PackageLine();
                pl.setPackageId(p.getId());
                pl.setItemCode(l.getItemCode());
                pl.setLotNo(l.getLotNo());
                pl.setQty(l.getQty());
                lineMapper.insert(pl);
            }
            gross = gross.add(weights.get(i));
            out.add(load(p.getId()));
        }
        orderService.pack(order.getId(), out.size(), gross, carrier, null, cartonCode);
        return out;
    }

    private Long waveIdOf(ShipOrder order) {
        return orderService.tasks(order.getId()).stream().map(t -> t.getWaveId()).filter(w -> w != null).findFirst().orElse(null);
    }

    private List<PackageLine> pickedLines(ShipOrder order) {
        List<PackageLine> out = new ArrayList<>();
        for (ShipOrderLine l : order.getLines()) {
            if (l.getPickedQty() != null && l.getPickedQty().signum() > 0) {
                PackageLine pl = new PackageLine();
                pl.setItemCode(l.getItemCode());
                pl.setLotNo(l.getLotNo());
                pl.setQty(l.getPickedQty());
                out.add(pl);
            }
        }
        if (out.isEmpty()) {
            throw new BizException("出库单没有实拣数量");
        }
        return out;
    }

    private BigDecimal unitWeight(ShipOrder order, String itemCode, Map<String, BigDecimal> cache) {
        return cache.computeIfAbsent(itemCode, c -> {
            Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                    .eq(Item::getOwnerCode, order.getOwnerCode()).eq(Item::getCode, c));
            return item == null || item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();
        });
    }

    private BigDecimal weightOf(ShipOrder order, List<PackageLine> lines) {
        Map<String, BigDecimal> cache = new HashMap<>();
        return lines.stream().map(l -> unitWeight(order, l.getItemCode(), cache).multiply(l.getQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 逐件装箱, 超过单箱重量上限开新箱; 单件超重的商品独立成箱 */
    private void splitByWeight(ShipOrder order, List<PackageLine> all, BigDecimal max,
                               List<List<PackageLine>> groups, List<BigDecimal> weights) {
        Map<String, BigDecimal> cache = new HashMap<>();
        List<PackageLine> cur = new ArrayList<>();
        BigDecimal curW = BigDecimal.ZERO;
        for (PackageLine l : all) {
            BigDecimal unit = unitWeight(order, l.getItemCode(), cache);
            BigDecimal remain = l.getQty();
            while (remain.signum() > 0) {
                BigDecimal room = max.subtract(curW);
                BigDecimal fit;
                if (unit.signum() == 0) {
                    fit = remain;
                } else {
                    fit = room.divide(unit, 0, BigDecimal.ROUND_FLOOR).min(remain);
                }
                if (fit.signum() <= 0) {
                    if (cur.isEmpty()) {
                        fit = BigDecimal.ONE;
                    } else {
                        groups.add(cur);
                        weights.add(curW);
                        cur = new ArrayList<>();
                        curW = BigDecimal.ZERO;
                        continue;
                    }
                }
                PackageLine pl = new PackageLine();
                pl.setItemCode(l.getItemCode());
                pl.setLotNo(l.getLotNo());
                pl.setQty(fit);
                cur.add(pl);
                curW = curW.add(unit.multiply(fit));
                remain = remain.subtract(fit);
            }
        }
        if (!cur.isEmpty()) {
            groups.add(cur);
            weights.add(curW);
        }
    }

    private static String key(String item, String lot) {
        return item + "|" + (lot == null ? "" : lot);
    }

    public List<Package> byWave(Long waveId) {
        return packageMapper.selectList(new LambdaQueryWrapper<Package>().eq(Package::getWaveId, waveId)
                .ne(Package::getStatus, "CANCELLED").orderByAsc(Package::getOrderId).orderByAsc(Package::getSeqNo))
                .stream().peek(p -> p.setLines(lineMapper.selectList(new LambdaQueryWrapper<PackageLine>()
                        .eq(PackageLine::getPackageId, p.getId())))).collect(Collectors.toList());
    }
}
