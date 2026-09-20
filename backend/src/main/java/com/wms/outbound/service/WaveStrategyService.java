package com.wms.outbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Location;
import com.wms.basic.mapper.LocationMapper;
import com.wms.common.BizException;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.entity.Wave;
import com.wms.outbound.entity.WaveStrategy;
import com.wms.outbound.mapper.PickTaskMapper;
import com.wms.outbound.mapper.ShipOrderLineMapper;
import com.wms.outbound.mapper.ShipOrderMapper;
import com.wms.outbound.mapper.WaveStrategyMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 波次策略引擎: 取仓库内已分配且未入波的出库单, 按策略优先级依次匹配;
 * 每个策略内按分组键(SKU/货主/承运商)分组, 组内按订单数/SKU 品项数/总件数上限自动拆成多个波次。
 */
@Service
@RequiredArgsConstructor
public class WaveStrategyService {
    private final WaveStrategyMapper strategyMapper;
    private final ShipOrderMapper orderMapper;
    private final ShipOrderLineMapper lineMapper;
    private final PickTaskMapper pickMapper;
    private final LocationMapper locationMapper;
    private final WaveService waveService;

    /** 候选出库单画像: 品项/件数按订单明细统计, 储区按待拣任务来源库位统计 */
    @Data
    public static class OrderProfile {
        private ShipOrder order;
        private Set<String> items = new HashSet<>();
        private BigDecimal qty = BigDecimal.ZERO;
        private Set<String> zones = new HashSet<>();
        private boolean hasOpenTask;
    }

    /** 策略执行结果 */
    @Data
    public static class RunResult {
        private String strategyCode;
        private String strategyName;
        private int matchedOrders;
        private List<String> waveCodes = new ArrayList<>();
        private List<Long> waveIds = new ArrayList<>();
    }

    public List<WaveStrategy> enabledByPriority() {
        return strategyMapper.selectList(new LambdaQueryWrapper<WaveStrategy>()
                .eq(WaveStrategy::getEnabled, true).orderByAsc(WaveStrategy::getPriority).orderByAsc(WaveStrategy::getId));
    }

    public WaveStrategy require(Long id) {
        WaveStrategy s = strategyMapper.selectById(id);
        if (s == null) {
            throw new BizException("波次策略不存在: " + id);
        }
        return s;
    }

    @Transactional
    public WaveStrategy save(WaveStrategy req) {
        if (req.getCode() == null || req.getCode().trim().isEmpty()) {
            throw new BizException("策略编码不能为空");
        }
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BizException("策略名称不能为空");
        }
        if (req.getMaxOrders() != null && req.getMaxOrders() < 1) {
            throw new BizException("最大订单数至少为 1");
        }
        if (req.getMinOrders() != null && req.getMaxOrders() != null && req.getMinOrders() > req.getMaxOrders()) {
            throw new BizException("最小订单数不能大于最大订单数");
        }
        WaveStrategy dup = strategyMapper.selectOne(new LambdaQueryWrapper<WaveStrategy>().eq(WaveStrategy::getCode, req.getCode()));
        if (dup != null && !dup.getId().equals(req.getId())) {
            throw new BizException("策略编码已存在: " + req.getCode());
        }
        if (req.getPriority() == null) req.setPriority(100);
        if (req.getMinOrders() == null) req.setMinOrders(1);
        if (req.getMaxOrders() == null) req.setMaxOrders(100);
        if (req.getEnabled() == null) req.setEnabled(true);
        if (req.getPackStrategy() == null) req.setPackStrategy("ONE_ORDER_ONE_PACKAGE");
        if (req.getId() == null) {
            strategyMapper.insert(req);
        } else {
            strategyMapper.updateById(req);
        }
        return strategyMapper.selectById(req.getId());
    }

    /**
     * 执行策略成波。{@code strategyId} 为空则按优先级执行全部启用策略; {@code dryRun} 只预览匹配结果不建波。
     */
    @Transactional
    public List<RunResult> run(String warehouse, Long strategyId, boolean dryRun) {
        if (warehouse == null || warehouse.isEmpty()) {
            throw new BizException("仓库不能为空");
        }
        List<WaveStrategy> strategies = strategyId == null
                ? enabledByPriority() : new ArrayList<>(Collections.singletonList(require(strategyId)));
        if (strategies.isEmpty()) {
            throw new BizException("没有启用的波次策略");
        }
        List<OrderProfile> pool = candidates(warehouse);
        List<RunResult> results = new ArrayList<>();
        int hour = LocalTime.now().getHour();
        for (WaveStrategy s : strategies) {
            RunResult r = new RunResult();
            r.setStrategyCode(s.getCode());
            r.setStrategyName(s.getName());
            results.add(r);
            if (strategyId == null && s.getCutoffHour() != null && hour < s.getCutoffHour()) {
                continue;
            }
            List<OrderProfile> matched = pool.stream().filter(p -> accepts(s, p)).collect(Collectors.toList());
            Map<String, List<OrderProfile>> groups = matched.stream()
                    .collect(Collectors.groupingBy(p -> groupKey(s, p), LinkedHashMap::new, Collectors.toList()));
            Set<Long> used = new HashSet<>();
            for (List<OrderProfile> group : groups.values()) {
                for (List<OrderProfile> chunk : split(s, group)) {
                    if (chunk.size() < nz(s.getMinOrders(), 1)) {
                        continue;
                    }
                    List<Long> ids = chunk.stream().map(p -> p.getOrder().getId()).collect(Collectors.toList());
                    used.addAll(ids);
                    if (!dryRun) {
                        Wave w = waveService.create(warehouse, ids, "策略 " + s.getCode() + " 自动成波", s);
                        r.getWaveCodes().add(w.getCode());
                        r.getWaveIds().add(w.getId());
                    }
                }
            }
            r.setMatchedOrders(used.size());
            pool.removeIf(p -> used.contains(p.getOrder().getId()));
        }
        return results;
    }

    /** 已分配/部分分配、有 NEW 拣货任务且未入波的出库单 */
    public List<OrderProfile> candidates(String warehouse) {
        List<ShipOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ShipOrder>()
                .eq(ShipOrder::getWarehouseCode, warehouse)
                .in(ShipOrder::getStatus, "ALLOCATED", "PART_ALLOCATED")
                .orderByAsc(ShipOrder::getPriority).orderByAsc(ShipOrder::getId));
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, OrderProfile> byOrder = new LinkedHashMap<>();
        for (ShipOrder o : orders) {
            OrderProfile p = new OrderProfile();
            p.setOrder(o);
            byOrder.put(o.getId(), p);
        }
        Map<String, String> zoneOf = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                        .eq(Location::getWarehouseCode, warehouse))
                .stream().collect(Collectors.toMap(Location::getCode, l -> l.getZoneCode() == null ? "" : l.getZoneCode(), (a, b) -> a));
        List<PickTask> tasks = pickMapper.selectList(new LambdaQueryWrapper<PickTask>()
                .in(PickTask::getOrderId, byOrder.keySet()).eq(PickTask::getStatus, "NEW"));
        Set<Long> inWave = new HashSet<>();
        for (PickTask t : tasks) {
            if (t.getWaveId() != null) {
                inWave.add(t.getOrderId());
                continue;
            }
            OrderProfile p = byOrder.get(t.getOrderId());
            p.setHasOpenTask(true);
            p.getZones().add(zoneOf.getOrDefault(t.getFromLocation(), ""));
        }
        for (ShipOrderLine l : lineMapper.selectList(new LambdaQueryWrapper<ShipOrderLine>().in(ShipOrderLine::getOrderId, byOrder.keySet()))) {
            OrderProfile p = byOrder.get(l.getOrderId());
            p.getItems().add(l.getItemCode());
            p.setQty(p.getQty().add(l.getOrderQty() == null ? BigDecimal.ZERO : l.getOrderQty()));
        }
        return byOrder.values().stream()
                .filter(p -> !inWave.contains(p.getOrder().getId()) && p.isHasOpenTask())
                .collect(Collectors.toList());
    }

    static boolean accepts(WaveStrategy s, OrderProfile p) {
        int sku = p.getItems().size();
        if (nz(s.getMinSkuPerOrder(), 0) > 0 && sku < s.getMinSkuPerOrder()) return false;
        if (nz(s.getMaxSkuPerOrder(), 0) > 0 && sku > s.getMaxSkuPerOrder()) return false;
        if (nz(s.getMinQtyPerOrder()).signum() > 0 && p.getQty().compareTo(s.getMinQtyPerOrder()) < 0) return false;
        if (nz(s.getMaxQtyPerOrder()).signum() > 0 && p.getQty().compareTo(s.getMaxQtyPerOrder()) > 0) return false;
        if (s.getZoneCode() != null && !s.getZoneCode().isEmpty()) {
            if (p.getZones().size() != 1 || !p.getZones().contains(s.getZoneCode())) return false;
        }
        return true;
    }

    private static String groupKey(WaveStrategy s, OrderProfile p) {
        StringBuilder k = new StringBuilder();
        if (Boolean.TRUE.equals(s.getGroupByItem())) {
            k.append("I:").append(p.getItems().stream().sorted().collect(Collectors.joining(","))).append('|');
        }
        if (Boolean.TRUE.equals(s.getGroupByOwner())) {
            k.append("O:").append(p.getOrder().getOwnerCode()).append('|');
        }
        if (Boolean.TRUE.equals(s.getGroupByCarrier())) {
            k.append("C:").append(p.getOrder().getCarrier() == null ? "" : p.getOrder().getCarrier()).append('|');
        }
        return k.toString();
    }

    /** 按最大订单数 / 波次 SKU 品项数 / 波次总件数拆波 */
    public static List<List<OrderProfile>> split(WaveStrategy s, List<OrderProfile> group) {
        List<OrderProfile> sorted = new ArrayList<>(group);
        sorted.sort(Comparator.comparing((OrderProfile p) -> p.getOrder().getPriority() == null ? 5 : p.getOrder().getPriority())
                .thenComparing(p -> p.getOrder().getId()));
        int maxOrders = nz(s.getMaxOrders(), 100);
        int maxSku = nz(s.getMaxSkuItems(), 0);
        BigDecimal maxQty = nz(s.getMaxTotalQty());
        List<List<OrderProfile>> chunks = new ArrayList<>();
        List<OrderProfile> cur = new ArrayList<>();
        Set<String> curItems = new HashSet<>();
        BigDecimal curQty = BigDecimal.ZERO;
        for (OrderProfile p : sorted) {
            boolean oversized = (maxSku > 0 && p.getItems().size() > maxSku)
                    || (maxQty.signum() > 0 && p.getQty().compareTo(maxQty) > 0);
            if (oversized) {
                continue;
            }
            Set<String> merged = new HashSet<>(curItems);
            merged.addAll(p.getItems());
            boolean full = !cur.isEmpty() && (cur.size() >= maxOrders
                    || (maxSku > 0 && merged.size() > maxSku)
                    || (maxQty.signum() > 0 && curQty.add(p.getQty()).compareTo(maxQty) > 0));
            if (full) {
                chunks.add(cur);
                cur = new ArrayList<>();
                curItems = new HashSet<>();
                curQty = BigDecimal.ZERO;
            }
            cur.add(p);
            curItems.addAll(p.getItems());
            curQty = curQty.add(p.getQty());
        }
        if (!cur.isEmpty()) {
            chunks.add(cur);
        }
        return chunks;
    }

    private static int nz(Integer v, int def) {
        return v == null ? def : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
