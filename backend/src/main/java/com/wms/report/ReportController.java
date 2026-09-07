package com.wms.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wms.basic.entity.Item;
import com.wms.basic.mapper.ItemMapper;
import com.wms.common.R;
import com.wms.inbound.entity.PutawayTask;
import com.wms.inbound.entity.QcTask;
import com.wms.inbound.mapper.PutawayTaskMapper;
import com.wms.inbound.mapper.QcTaskMapper;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.ReplenishTaskMapper;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.mapper.PickTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 库龄 / 效期预警 / 作业 KPI / ABC 分析 / 操作员计件报表 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private static final int[] AGE_BUCKETS = {30, 60, 90, 180};

    private final InventoryMapper inventoryMapper;
    private final ItemMapper itemMapper;
    private final PutawayTaskMapper putawayMapper;
    private final QcTaskMapper qcMapper;
    private final PickTaskMapper pickMapper;
    private final ReplenishTaskMapper replenishMapper;
    private final JdbcTemplate jdbc;

    /** 库龄：按物料汇总各库龄段数量，并返回明细（含库龄天数） */
    @GetMapping("/aging")
    public R<Map<String, Object>> aging(@RequestParam(required = false) String warehouseCode,
                                        @RequestParam(required = false) String ownerCode) {
        List<Inventory> stock = stock(warehouseCode, ownerCode);
        Map<String, String> names = itemNames(stock);
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> byItem = new LinkedHashMap<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Inventory inv : stock) {
            long age = inv.getReceiveDate() == null ? 0 : ChronoUnit.DAYS.between(inv.getReceiveDate(), today);
            String bucket = bucket(age);
            String key = inv.getOwnerCode() + "/" + inv.getItemCode();
            Map<String, Object> row = byItem.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ownerCode", inv.getOwnerCode());
                m.put("itemCode", inv.getItemCode());
                m.put("itemName", names.get(key));
                m.put("total", BigDecimal.ZERO);
                m.put("b0_30", BigDecimal.ZERO);
                m.put("b31_60", BigDecimal.ZERO);
                m.put("b61_90", BigDecimal.ZERO);
                m.put("b91_180", BigDecimal.ZERO);
                m.put("b180p", BigDecimal.ZERO);
                m.put("maxAge", 0L);
                return m;
            });
            row.put("total", ((BigDecimal) row.get("total")).add(inv.getQty()));
            row.put(bucket, ((BigDecimal) row.get(bucket)).add(inv.getQty()));
            row.put("maxAge", Math.max((Long) row.get("maxAge"), age));

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("ownerCode", inv.getOwnerCode());
            d.put("itemCode", inv.getItemCode());
            d.put("itemName", names.get(key));
            d.put("locationCode", inv.getLocationCode());
            d.put("lotNo", inv.getLotNo());
            d.put("qty", inv.getQty());
            d.put("receiveDate", inv.getReceiveDate());
            d.put("ageDays", age);
            d.put("status", inv.getStatus());
            detail.add(d);
        }
        detail.sort((a, b) -> Long.compare((Long) b.get("ageDays"), (Long) a.get("ageDays")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", new ArrayList<>(byItem.values()));
        out.put("detail", detail);
        return R.ok(out);
    }

    /** 效期预警：已过期 + N 天内到期的库存 */
    @GetMapping("/expiry")
    public R<List<Map<String, Object>>> expiry(@RequestParam(required = false) String warehouseCode,
                                               @RequestParam(required = false) String ownerCode,
                                               @RequestParam(defaultValue = "30") int days) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(days);
        List<Inventory> stock = stock(warehouseCode, ownerCode).stream()
                .filter(i -> i.getExpiryDate() != null && !i.getExpiryDate().isAfter(limit))
                .sorted((a, b) -> a.getExpiryDate().compareTo(b.getExpiryDate()))
                .collect(Collectors.toList());
        Map<String, String> names = itemNames(stock);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Inventory inv : stock) {
            long left = ChronoUnit.DAYS.between(today, inv.getExpiryDate());
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("inventoryId", inv.getId());
            d.put("ownerCode", inv.getOwnerCode());
            d.put("itemCode", inv.getItemCode());
            d.put("itemName", names.get(inv.getOwnerCode() + "/" + inv.getItemCode()));
            d.put("locationCode", inv.getLocationCode());
            d.put("lotNo", inv.getLotNo());
            d.put("qty", inv.getQty());
            d.put("expiryDate", inv.getExpiryDate());
            d.put("daysLeft", left);
            d.put("level", left < 0 ? "EXPIRED" : left <= 7 ? "URGENT" : "WARNING");
            d.put("status", inv.getStatus());
            out.add(d);
        }
        return R.ok(out);
    }

    /** 作业 KPI：近 N 天每日收货/发运量、各类未完成任务数 */
    @GetMapping("/kpi")
    public R<Map<String, Object>> kpi(@RequestParam(defaultValue = "14") int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT CAST(created_at AS DATE) AS d, txn_type, SUM(qty) AS q, COUNT(*) AS c FROM wms_inventory_txn "
                        + "WHERE created_at >= ? AND txn_type IN ('RECEIVE','SHIP','PUTAWAY','PICK','REPLENISH','QC_REJECT') "
                        + "GROUP BY CAST(created_at AS DATE), txn_type ORDER BY d", from);
        Map<String, Map<String, Object>> daily = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String d = LocalDate.now().minusDays(days - 1L - i).toString();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            daily.put(d, m);
        }
        for (Map<String, Object> r : rows) {
            String d = String.valueOf(r.get("d"));
            Map<String, Object> m = daily.get(d);
            if (m != null) {
                m.put(String.valueOf(r.get("txn_type")), r.get("q"));
                m.put(r.get("txn_type") + "_count", r.get("c"));
            }
        }
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("putaway", putawayMapper.selectCount(new LambdaQueryWrapper<PutawayTask>().eq(PutawayTask::getStatus, "NEW")));
        open.put("qc", qcMapper.selectCount(new LambdaQueryWrapper<QcTask>().eq(QcTask::getStatus, "NEW")));
        open.put("pick", pickMapper.selectCount(new LambdaQueryWrapper<PickTask>().eq(PickTask::getStatus, "NEW")));
        open.put("replenish", replenishMapper.selectCount(new LambdaQueryWrapper<ReplenishTask>().eq(ReplenishTask::getStatus, "NEW")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("daily", new ArrayList<>(daily.values()));
        out.put("openTasks", open);
        return R.ok(out);
    }

    /** ABC 分析：按近 N 天发运量累计占比划分 A(≤70%) / B(≤90%) / C */
    @GetMapping("/abc")
    public R<List<Map<String, Object>>> abc(@RequestParam(required = false) String warehouseCode,
                                            @RequestParam(required = false) String ownerCode,
                                            @RequestParam(defaultValue = "90") int days) {
        return R.ok(abcRows(warehouseCode, ownerCode, days));
    }

    public List<Map<String, Object>> abcRows(String warehouseCode, String ownerCode, int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        StringBuilder sql = new StringBuilder(
                "SELECT owner_code, item_code, SUM(qty) AS q, COUNT(*) AS c FROM wms_inventory_txn "
                        + "WHERE txn_type = 'SHIP' AND created_at >= ?");
        List<Object> args = new ArrayList<>();
        args.add(from);
        if (StringUtils.isNotBlank(warehouseCode)) {
            sql.append(" AND warehouse_code = ?");
            args.add(warehouseCode);
        }
        if (StringUtils.isNotBlank(ownerCode)) {
            sql.append(" AND owner_code = ?");
            args.add(ownerCode);
        }
        sql.append(" GROUP BY owner_code, item_code ORDER BY q DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        List<Item> items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(StringUtils.isNotBlank(ownerCode), Item::getOwnerCode, ownerCode));
        Map<String, Item> itemMap = items.stream()
                .collect(Collectors.toMap(i -> i.getOwnerCode() + "/" + i.getCode(), i -> i, (a, b) -> a));

        BigDecimal total = rows.stream().map(r -> toDecimal(r.get("q"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cum = BigDecimal.ZERO;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String key = r.get("owner_code") + "/" + r.get("item_code");
            BigDecimal q = toDecimal(r.get("q"));
            double before = total.signum() == 0 ? 1 : cum.divide(total, 6, BigDecimal.ROUND_HALF_UP).doubleValue();
            cum = cum.add(q);
            double pct = total.signum() == 0 ? 1 : cum.divide(total, 6, BigDecimal.ROUND_HALF_UP).doubleValue();
            Item item = itemMap.remove(key);
            out.add(abcRow(String.valueOf(r.get("owner_code")), String.valueOf(r.get("item_code")), item, q,
                    ((Number) r.get("c")).longValue(), pct, before < 0.7 ? "A" : before < 0.9 ? "B" : "C"));
        }
        for (Item item : itemMap.values()) {
            out.add(abcRow(item.getOwnerCode(), item.getCode(), item, BigDecimal.ZERO, 0L, 1, "C"));
        }
        return out;
    }

    private static Map<String, Object> abcRow(String owner, String code, Item item, BigDecimal q, long lines, double pct, String cls) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ownerCode", owner);
        m.put("itemCode", code);
        m.put("itemId", item == null ? null : item.getId());
        m.put("itemName", item == null ? null : item.getName());
        m.put("shipQty", q);
        m.put("shipLines", lines);
        m.put("cumPct", pct);
        m.put("currentClass", item == null ? null : item.getAbcClass());
        m.put("suggestedClass", cls);
        return m;
    }

    /** 计件效能：近 N 天按操作员 + 日期统计各作业节点数量/笔数 */
    @GetMapping("/labor")
    public R<List<Map<String, Object>>> labor(@RequestParam(defaultValue = "14") int days,
                                              @RequestParam(required = false) String operator) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        StringBuilder sql = new StringBuilder(
                "SELECT operator, CAST(created_at AS DATE) AS d, txn_type, SUM(qty) AS q, COUNT(*) AS c FROM wms_inventory_txn "
                        + "WHERE created_at >= ? AND txn_type IN ('RECEIVE','PUTAWAY','PICK','SHIP','REPLENISH','QC_REJECT')");
        List<Object> args = new ArrayList<>();
        args.add(from);
        if (StringUtils.isNotBlank(operator)) {
            sql.append(" AND operator = ?");
            args.add(operator);
        }
        sql.append(" GROUP BY operator, CAST(created_at AS DATE), txn_type ORDER BY d DESC, operator");
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(sql.toString(), args.toArray())) {
            String op = String.valueOf(r.get("operator"));
            String d = String.valueOf(r.get("d"));
            Map<String, Object> m = rows.computeIfAbsent(op + "@" + d, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("operator", op);
                x.put("date", d);
                x.put("totalCount", 0L);
                return x;
            });
            String type = String.valueOf(r.get("txn_type"));
            long c = ((Number) r.get("c")).longValue();
            m.put(type, r.get("q"));
            m.put(type + "_count", c);
            m.put("totalCount", (Long) m.get("totalCount") + c);
        }
        return R.ok(new ArrayList<>(rows.values()));
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString());
    }

    private List<Inventory> stock(String warehouse, String owner) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(StringUtils.isNotBlank(warehouse), Inventory::getWarehouseCode, warehouse)
                .eq(StringUtils.isNotBlank(owner), Inventory::getOwnerCode, owner)
                .gt(Inventory::getQty, 0));
    }

    private Map<String, String> itemNames(List<Inventory> stock) {
        if (stock.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<String> codes = stock.stream().map(Inventory::getItemCode).distinct().collect(Collectors.toList());
        return itemMapper.selectList(new LambdaQueryWrapper<Item>().in(Item::getCode, codes)).stream()
                .collect(Collectors.toMap(i -> i.getOwnerCode() + "/" + i.getCode(), Item::getName, (a, b) -> a));
    }

    private static String bucket(long age) {
        if (age <= AGE_BUCKETS[0]) return "b0_30";
        if (age <= AGE_BUCKETS[1]) return "b31_60";
        if (age <= AGE_BUCKETS[2]) return "b61_90";
        if (age <= AGE_BUCKETS[3]) return "b91_180";
        return "b180p";
    }
}
