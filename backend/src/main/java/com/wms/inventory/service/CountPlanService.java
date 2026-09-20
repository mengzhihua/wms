package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wms.basic.entity.Item;
import com.wms.basic.entity.Location;
import com.wms.basic.mapper.ItemMapper;
import com.wms.basic.mapper.LocationMapper;
import com.wms.common.BizException;
import com.wms.common.CodeGenerator;
import com.wms.inventory.entity.CountPlan;
import com.wms.inventory.entity.CountTask;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTxn;
import com.wms.inventory.entity.RecountTask;
import com.wms.inventory.entity.StockAdjust;
import com.wms.inventory.entity.StockAdjustLine;
import com.wms.inventory.mapper.CountPlanMapper;
import com.wms.inventory.mapper.CountTaskMapper;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryTxnMapper;
import com.wms.inventory.mapper.RecountTaskMapper;
import com.wms.inventory.mapper.StockAdjustLineMapper;
import com.wms.inventory.mapper.StockAdjustMapper;
import com.wms.system.auth.CurrentUser;
import com.wms.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 盘点计划闭环: 计划(草稿→提交→审批) → 生成任务并锁库 → 领取/指派 → 提交结果 → 差异复盘(多轮) → 最终确认
 * → 生成库存调整单 → 审核过账 → 完成解锁 → 盘点报告。
 */
@Service
@RequiredArgsConstructor
public class CountPlanService {
    private final CountPlanMapper planMapper;
    private final CountTaskMapper taskMapper;
    private final RecountTaskMapper recountMapper;
    private final StockAdjustMapper adjustMapper;
    private final StockAdjustLineMapper adjustLineMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryTxnMapper txnMapper;
    private final LocationMapper locationMapper;
    private final ItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final CodeGenerator codeGenerator;

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("CYCLE", "MOVEMENT", "RANDOM", "ABNORMAL"));

    // ------------------------------------------------------------------ plan

    public CountPlan require(Long id) {
        CountPlan p = planMapper.selectById(id);
        if (p == null) {
            throw new BizException("盘点计划不存在: " + id);
        }
        return p;
    }

    @Transactional
    public CountPlan create(CountPlan req) {
        if (req.getWarehouseCode() == null || req.getWarehouseCode().isEmpty()) {
            throw new BizException("仓库不能为空");
        }
        if (req.getType() == null || !TYPES.contains(req.getType())) {
            throw new BizException("盘点类型无效, 支持 CYCLE/MOVEMENT/RANDOM/ABNORMAL");
        }
        CountPlan p = new CountPlan();
        p.setCode(codeGenerator.next("CP"));
        p.setName(req.getName() == null || req.getName().isEmpty() ? p.getCode() : req.getName());
        p.setWarehouseCode(req.getWarehouseCode());
        p.setType(req.getType());
        p.setScopeType(req.getScopeType() == null ? "WAREHOUSE" : req.getScopeType());
        p.setZoneCode(req.getZoneCode());
        p.setItemCodes(req.getItemCodes());
        p.setAbcClasses(req.getAbcClasses());
        p.setSinceDate(req.getSinceDate());
        p.setSamplePercent(req.getSamplePercent());
        p.setRemark(req.getRemark());
        p.setStatus("DRAFT");
        p.setTaskCount(0);
        p.setDoneCount(0);
        p.setDiffCount(0);
        p.setCreatedBy(operator());
        planMapper.insert(p);
        return p;
    }

    @Transactional
    public CountPlan update(Long id, CountPlan req) {
        CountPlan p = require(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BizException("仅草稿状态可修改");
        }
        if (req.getType() != null && !TYPES.contains(req.getType())) {
            throw new BizException("盘点类型无效");
        }
        if (req.getName() != null) p.setName(req.getName());
        if (req.getType() != null) p.setType(req.getType());
        if (req.getScopeType() != null) p.setScopeType(req.getScopeType());
        p.setZoneCode(req.getZoneCode());
        p.setItemCodes(req.getItemCodes());
        p.setAbcClasses(req.getAbcClasses());
        p.setSinceDate(req.getSinceDate());
        p.setSamplePercent(req.getSamplePercent());
        p.setRemark(req.getRemark());
        planMapper.updateById(p);
        return p;
    }

    /** 草稿提交审批: 校验范围至少能匹配到库存 */
    @Transactional
    public CountPlan submit(Long id) {
        CountPlan p = require(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BizException("仅草稿状态可提交审批");
        }
        if (scopeInventory(p).isEmpty()) {
            throw new BizException("盘点范围内没有库存, 请调整范围");
        }
        p.setStatus("PENDING");
        planMapper.updateById(p);
        return p;
    }

    @Transactional
    public CountPlan approve(Long id, boolean pass, String opinion) {
        CountPlan p = require(id);
        if (!"PENDING".equals(p.getStatus())) {
            throw new BizException("仅待审批状态可审批");
        }
        p.setApprover(operator());
        p.setApproveOpinion(opinion);
        p.setStatus(pass ? "APPROVED" : "DRAFT");
        planMapper.updateById(p);
        return p;
    }

    /** 审批通过后生成盘点任务: 一条库存一条任务, 并锁定库存 */
    @Transactional
    public CountPlan generateTasks(Long id) {
        CountPlan p = require(id);
        if (!"APPROVED".equals(p.getStatus())) {
            throw new BizException("仅审批通过的计划可生成任务");
        }
        List<Inventory> scope = scopeInventory(p);
        if (scope.isEmpty()) {
            throw new BizException("盘点范围内没有库存");
        }
        for (Inventory inv : scope) {
            if (Boolean.TRUE.equals(inv.getCountLock())) {
                throw new BizException("库存已被其他盘点计划锁定: 库位 " + inv.getLocationCode() + " 物料 " + inv.getItemCode());
            }
            CountTask t = new CountTask();
            t.setPlanId(p.getId());
            t.setPlanCode(p.getCode());
            t.setInventoryId(inv.getId());
            t.setWarehouseCode(inv.getWarehouseCode());
            t.setLocationCode(inv.getLocationCode());
            t.setOwnerCode(inv.getOwnerCode());
            t.setItemCode(inv.getItemCode());
            t.setLotNo(inv.getLotNo());
            t.setSystemQty(inv.getQty());
            t.setStatus("PENDING");
            taskMapper.insert(t);
            lock(inv.getId(), p.getId(), true);
        }
        p.setTaskCount(scope.size());
        p.setDoneCount(0);
        p.setDiffCount(0);
        p.setStatus("EXECUTING");
        planMapper.updateById(p);
        return p;
    }

    /** 按计划类型与范围筛选库存 */
    List<Inventory> scopeInventory(CountPlan p) {
        LambdaQueryWrapper<Inventory> qw = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, p.getWarehouseCode()).gt(Inventory::getQty, 0);
        if ("ZONE".equals(p.getScopeType()) || (p.getZoneCode() != null && !p.getZoneCode().isEmpty())) {
            if (p.getZoneCode() == null || p.getZoneCode().isEmpty()) {
                throw new BizException("按库区盘点需指定库区");
            }
            List<String> locs = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                            .eq(Location::getWarehouseCode, p.getWarehouseCode()).eq(Location::getZoneCode, p.getZoneCode()))
                    .stream().map(Location::getCode).collect(Collectors.toList());
            if (locs.isEmpty()) {
                return Collections.emptyList();
            }
            qw.in(Inventory::getLocationCode, locs);
        }
        if ("ITEM".equals(p.getScopeType()) || (p.getItemCodes() != null && !p.getItemCodes().trim().isEmpty())) {
            List<String> items = splitCodes(p.getItemCodes());
            if (items.isEmpty()) {
                throw new BizException("按物料盘点需指定物料编码(逗号分隔)");
            }
            qw.in(Inventory::getItemCode, items);
        }
        List<Inventory> list = inventoryMapper.selectList(qw);

        if ("CYCLE".equals(p.getType()) && p.getAbcClasses() != null && !p.getAbcClasses().trim().isEmpty()) {
            Set<String> classes = new HashSet<>(splitCodes(p.getAbcClasses().toUpperCase()));
            Set<String> itemCodes = list.stream().map(Inventory::getItemCode).collect(Collectors.toSet());
            Set<String> hit = itemMapper.selectList(new LambdaQueryWrapper<Item>().in(Item::getCode, itemCodes))
                    .stream().filter(i -> i.getAbcClass() != null && classes.contains(i.getAbcClass().toUpperCase()))
                    .map(i -> i.getOwnerCode() + "|" + i.getCode()).collect(Collectors.toSet());
            list.removeIf(i -> !hit.contains(i.getOwnerCode() + "|" + i.getItemCode()));
        }
        if ("MOVEMENT".equals(p.getType()) || "ABNORMAL".equals(p.getType())) {
            LocalDate since = p.getSinceDate() == null ? LocalDate.now().minusDays(7) : p.getSinceDate();
            LambdaQueryWrapper<InventoryTxn> tq = new LambdaQueryWrapper<InventoryTxn>()
                    .eq(InventoryTxn::getWarehouseCode, p.getWarehouseCode())
                    .ge(InventoryTxn::getCreatedAt, since.atStartOfDay());
            if ("ABNORMAL".equals(p.getType())) {
                tq.in(InventoryTxn::getTxnType, "ADJUST", "FREEZE", "UNFREEZE");
            }
            Set<String> touched = new HashSet<>();
            for (InventoryTxn t : txnMapper.selectList(tq)) {
                touched.add(key(t.getOwnerCode(), t.getItemCode(), t.getLotNo(), t.getFromLocation()));
                touched.add(key(t.getOwnerCode(), t.getItemCode(), t.getLotNo(), t.getToLocation()));
            }
            list.removeIf(i -> !touched.contains(key(i.getOwnerCode(), i.getItemCode(), i.getLotNo(), i.getLocationCode())));
        }
        if ("RANDOM".equals(p.getType())) {
            int pct = p.getSamplePercent() == null ? 20 : Math.max(1, Math.min(100, p.getSamplePercent()));
            int n = Math.max(1, (int) Math.ceil(list.size() * pct / 100.0));
            if (n < list.size()) {
                Collections.shuffle(list, new Random(p.getId() == null ? System.nanoTime() : p.getId()));
                list = new ArrayList<>(list.subList(0, n));
            }
        }
        list.sort((a, b) -> a.getLocationCode().compareTo(b.getLocationCode()));
        return list;
    }

    private static String key(String owner, String item, String lot, String loc) {
        return owner + "|" + item + "|" + (lot == null ? "" : lot) + "|" + loc;
    }

    private static List<String> splitCodes(String s) {
        if (s == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(s.split("[,，;\\s]+")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    private void lock(Long inventoryId, Long planId, boolean lock) {
        inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                .eq(Inventory::getId, inventoryId)
                .set(Inventory::getCountLock, lock)
                .set(Inventory::getCountPlanId, lock ? planId : null));
    }

    private void unlockAll(Long planId) {
        inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                .eq(Inventory::getCountPlanId, planId)
                .set(Inventory::getCountLock, false)
                .set(Inventory::getCountPlanId, null));
    }

    @Transactional
    public CountPlan cancel(Long id) {
        CountPlan p = require(id);
        if ("COMPLETED".equals(p.getStatus()) || "CANCELLED".equals(p.getStatus())) {
            throw new BizException("计划已" + p.getStatus());
        }
        if (adjustMapper.selectCount(new LambdaQueryWrapper<StockAdjust>()
                .eq(StockAdjust::getPlanId, id).eq(StockAdjust::getStatus, "APPROVED")) > 0) {
            throw new BizException("调整单已过账, 不能取消计划");
        }
        taskMapper.update(null, new LambdaUpdateWrapper<CountTask>().eq(CountTask::getPlanId, id)
                .ne(CountTask::getStatus, "DONE").set(CountTask::getStatus, "CANCELLED"));
        adjustMapper.update(null, new LambdaUpdateWrapper<StockAdjust>().eq(StockAdjust::getPlanId, id)
                .eq(StockAdjust::getStatus, "PENDING").set(StockAdjust::getStatus, "REJECTED")
                .set(StockAdjust::getApproveOpinion, "计划取消"));
        unlockAll(id);
        p.setStatus("CANCELLED");
        planMapper.updateById(p);
        return p;
    }

    /** 完成计划: 所有任务完成、复盘确认、调整单审核后解锁库存 */
    @Transactional
    public CountPlan complete(Long id) {
        CountPlan p = require(id);
        if (!"EXECUTING".equals(p.getStatus())) {
            throw new BizException("仅执行中的计划可完成");
        }
        if (taskMapper.selectCount(new LambdaQueryWrapper<CountTask>().eq(CountTask::getPlanId, id)
                .in(CountTask::getStatus, "PENDING", "CLAIMED")) > 0) {
            throw new BizException("仍有盘点任务未完成");
        }
        if (recountMapper.selectCount(new LambdaQueryWrapper<RecountTask>().eq(RecountTask::getPlanId, id)
                .ne(RecountTask::getStatus, "CONFIRMED")) > 0) {
            throw new BizException("仍有复盘任务未确认");
        }
        long mismatch = recountMapper.selectCount(new LambdaQueryWrapper<RecountTask>().eq(RecountTask::getPlanId, id)
                .eq(RecountTask::getFinalResult, "MISMATCH"));
        if (adjustMapper.selectCount(new LambdaQueryWrapper<StockAdjust>().eq(StockAdjust::getPlanId, id)
                .eq(StockAdjust::getStatus, "PENDING")) > 0) {
            throw new BizException("调整单尚未审核");
        }
        long approved = adjustMapper.selectCount(new LambdaQueryWrapper<StockAdjust>().eq(StockAdjust::getPlanId, id)
                .eq(StockAdjust::getStatus, "APPROVED"));
        if (mismatch > 0 && approved == 0) {
            throw new BizException("存在差异但调整单尚未审核通过(已驳回请重新生成调整单)");
        }
        unlockAll(id);
        p.setStatus("COMPLETED");
        planMapper.updateById(p);
        return p;
    }

    // ------------------------------------------------------------------ tasks

    public List<CountTask> tasks(Long planId) {
        return taskMapper.selectList(new LambdaQueryWrapper<CountTask>().eq(CountTask::getPlanId, planId)
                .orderByAsc(CountTask::getLocationCode).orderByAsc(CountTask::getId));
    }

    private CountTask requireTask(Long taskId) {
        CountTask t = taskMapper.selectById(taskId);
        if (t == null) {
            throw new BizException("盘点任务不存在: " + taskId);
        }
        return t;
    }

    /** 删除未领取的任务并释放锁 */
    @Transactional
    public void deleteTask(Long taskId) {
        CountTask t = requireTask(taskId);
        if (!"PENDING".equals(t.getStatus())) {
            throw new BizException("仅待领取任务可删除");
        }
        taskMapper.deleteById(taskId);
        lock(t.getInventoryId(), null, false);
        CountPlan p = require(t.getPlanId());
        p.setTaskCount(Math.max(0, nz(p.getTaskCount()) - 1));
        planMapper.updateById(p);
    }

    @Transactional
    public CountTask claim(Long taskId) {
        CountTask t = requireTask(taskId);
        String me = operator();
        if ("CLAIMED".equals(t.getStatus()) && t.getAssignee() != null && !t.getAssignee().equals(me)) {
            throw new BizException("任务已由 " + t.getAssignee() + " 领取, 请通过指派改派");
        }
        return assign(taskId, me);
    }

    @Transactional
    public CountTask assign(Long taskId, String assignee) {
        CountTask t = requireTask(taskId);
        if (!"PENDING".equals(t.getStatus()) && !"CLAIMED".equals(t.getStatus())) {
            throw new BizException("任务状态 " + t.getStatus() + " 不可领取/指派");
        }
        if (assignee == null || assignee.isEmpty()) {
            throw new BizException("盘点人不能为空");
        }
        t.setAssignee(assignee);
        t.setStatus("CLAIMED");
        taskMapper.updateById(t);
        return t;
    }

    /** 提交盘点结果; 有差异自动生成第一轮复盘任务 */
    @Transactional
    public CountTask submitCount(Long taskId, BigDecimal qty) {
        CountTask t = requireTask(taskId);
        if (!"PENDING".equals(t.getStatus()) && !"CLAIMED".equals(t.getStatus())) {
            throw new BizException("任务状态 " + t.getStatus() + " 不可录入");
        }
        if (qty == null || qty.signum() < 0) {
            throw new BizException("盘点数量不能为负");
        }
        if (t.getAssignee() == null) {
            t.setAssignee(operator());
        }
        t.setCountQty(qty);
        t.setDiffQty(qty.subtract(t.getSystemQty()));
        t.setCountedAt(LocalDateTime.now());
        t.setStatus("DONE");
        taskMapper.updateById(t);

        if (t.getDiffQty().signum() != 0) {
            RecountTask r = new RecountTask();
            r.setPlanId(t.getPlanId());
            r.setRoundNo(1);
            r.setCountTaskId(t.getId());
            r.setInventoryId(t.getInventoryId());
            r.setLocationCode(t.getLocationCode());
            r.setOwnerCode(t.getOwnerCode());
            r.setItemCode(t.getItemCode());
            r.setLotNo(t.getLotNo());
            r.setSystemQty(t.getSystemQty());
            r.setFirstQty(qty);
            r.setStatus("PENDING");
            recountMapper.insert(r);
        }
        refreshPlanStats(t.getPlanId());
        return t;
    }

    private void refreshPlanStats(Long planId) {
        CountPlan p = require(planId);
        p.setDoneCount((int) (long) taskMapper.selectCount(new LambdaQueryWrapper<CountTask>()
                .eq(CountTask::getPlanId, planId).eq(CountTask::getStatus, "DONE")));
        p.setDiffCount((int) (long) taskMapper.selectCount(new LambdaQueryWrapper<CountTask>()
                .eq(CountTask::getPlanId, planId).eq(CountTask::getStatus, "DONE").ne(CountTask::getDiffQty, 0)));
        planMapper.updateById(p);
    }

    // ------------------------------------------------------------------ recount

    public List<RecountTask> recounts(Long planId) {
        return recountMapper.selectList(new LambdaQueryWrapper<RecountTask>().eq(RecountTask::getPlanId, planId)
                .orderByAsc(RecountTask::getCountTaskId).orderByAsc(RecountTask::getRoundNo));
    }

    private RecountTask requireRecount(Long id) {
        RecountTask r = recountMapper.selectById(id);
        if (r == null) {
            throw new BizException("复盘任务不存在: " + id);
        }
        return r;
    }

    /** 录入复盘数量 */
    @Transactional
    public RecountTask submitRecount(Long id, BigDecimal qty) {
        RecountTask r = requireRecount(id);
        if (!"PENDING".equals(r.getStatus())) {
            throw new BizException("复盘任务状态 " + r.getStatus() + " 不可录入");
        }
        if (qty == null || qty.signum() < 0) {
            throw new BizException("复盘数量不能为负");
        }
        r.setRecountQty(qty);
        r.setRecountDiff(qty.subtract(r.getSystemQty()));
        r.setAssignee(operator());
        r.setStatus("RECOUNTED");
        recountMapper.updateById(r);
        return r;
    }

    /** 再复盘一轮: 复制为下一轮任务 */
    @Transactional
    public RecountTask nextRound(Long id) {
        RecountTask r = requireRecount(id);
        if (!"RECOUNTED".equals(r.getStatus())) {
            throw new BizException("仅已复盘的任务可发起下一轮");
        }
        r.setStatus("CONFIRMED");
        r.setFinalResult("RECOUNT");
        recountMapper.updateById(r);
        RecountTask n = new RecountTask();
        n.setPlanId(r.getPlanId());
        n.setRoundNo(r.getRoundNo() + 1);
        n.setCountTaskId(r.getCountTaskId());
        n.setInventoryId(r.getInventoryId());
        n.setLocationCode(r.getLocationCode());
        n.setOwnerCode(r.getOwnerCode());
        n.setItemCode(r.getItemCode());
        n.setLotNo(r.getLotNo());
        n.setSystemQty(r.getSystemQty());
        n.setFirstQty(r.getRecountQty());
        n.setStatus("PENDING");
        recountMapper.insert(n);
        return n;
    }

    /** 最终确认: 以复盘数量为准, 判定一致/不一致 */
    @Transactional
    public RecountTask confirmRecount(Long id) {
        RecountTask r = requireRecount(id);
        if (!"RECOUNTED".equals(r.getStatus())) {
            throw new BizException("仅已复盘的任务可确认");
        }
        r.setFinalQty(r.getRecountQty());
        r.setFinalDiff(r.getRecountDiff());
        r.setFinalResult(r.getRecountDiff().signum() == 0 ? "MATCH" : "MISMATCH");
        r.setStatus("CONFIRMED");
        recountMapper.updateById(r);
        return r;
    }

    // ------------------------------------------------------------------ adjust

    /** 所有复盘确认后, 按最终差异生成库存调整单(待审核) */
    @Transactional
    public StockAdjust generateAdjust(Long planId) {
        CountPlan p = require(planId);
        if (!"EXECUTING".equals(p.getStatus())) {
            throw new BizException("仅执行中的计划可生成调整单");
        }
        if (taskMapper.selectCount(new LambdaQueryWrapper<CountTask>().eq(CountTask::getPlanId, planId)
                .in(CountTask::getStatus, "PENDING", "CLAIMED")) > 0) {
            throw new BizException("仍有盘点任务未完成");
        }
        if (recountMapper.selectCount(new LambdaQueryWrapper<RecountTask>().eq(RecountTask::getPlanId, planId)
                .ne(RecountTask::getStatus, "CONFIRMED")) > 0) {
            throw new BizException("仍有复盘任务未确认");
        }
        if (adjustMapper.selectCount(new LambdaQueryWrapper<StockAdjust>().eq(StockAdjust::getPlanId, planId)
                .ne(StockAdjust::getStatus, "REJECTED")) > 0) {
            throw new BizException("该计划已生成调整单");
        }
        List<RecountTask> mismatches = recountMapper.selectList(new LambdaQueryWrapper<RecountTask>()
                .eq(RecountTask::getPlanId, planId).eq(RecountTask::getFinalResult, "MISMATCH"));
        if (mismatches.isEmpty()) {
            throw new BizException("无最终差异, 无需调整单");
        }
        StockAdjust adj = new StockAdjust();
        adj.setCode(codeGenerator.next("ADJ"));
        adj.setPlanId(planId);
        adj.setPlanCode(p.getCode());
        adj.setWarehouseCode(p.getWarehouseCode());
        adj.setSource("COUNT");
        adj.setStatus("PENDING");
        adj.setLineCount(mismatches.size());
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        adjustMapper.insert(adj);
        for (RecountTask r : mismatches) {
            StockAdjustLine l = new StockAdjustLine();
            l.setAdjustId(adj.getId());
            l.setInventoryId(r.getInventoryId());
            l.setLocationCode(r.getLocationCode());
            l.setOwnerCode(r.getOwnerCode());
            l.setItemCode(r.getItemCode());
            l.setLotNo(r.getLotNo());
            l.setFromQty(r.getSystemQty());
            l.setToQty(r.getFinalQty());
            l.setDiffQty(r.getFinalDiff());
            l.setReason(r.getFinalDiff().signum() > 0 ? "盘盈" : "盘亏");
            adjustLineMapper.insert(l);
            if (r.getFinalDiff().signum() > 0) {
                gain = gain.add(r.getFinalDiff());
            } else {
                loss = loss.add(r.getFinalDiff().abs());
            }
        }
        adj.setGainQty(gain);
        adj.setLossQty(loss);
        adjustMapper.updateById(adj);
        return adj;
    }

    public StockAdjust requireAdjust(Long id) {
        StockAdjust a = adjustMapper.selectById(id);
        if (a == null) {
            throw new BizException("调整单不存在: " + id);
        }
        return a;
    }

    public List<StockAdjustLine> adjustLines(Long adjustId) {
        return adjustLineMapper.selectList(new LambdaQueryWrapper<StockAdjustLine>()
                .eq(StockAdjustLine::getAdjustId, adjustId).orderByAsc(StockAdjustLine::getId));
    }

    /** 审核调整单: 通过则逐行过账库存 */
    @Transactional
    public StockAdjust approveAdjust(Long id, boolean pass, String opinion) {
        StockAdjust adj = requireAdjust(id);
        if (!"PENDING".equals(adj.getStatus())) {
            throw new BizException("调整单状态 " + adj.getStatus() + " 不可审核");
        }
        adj.setApprover(operator());
        adj.setApproveOpinion(opinion);
        if (!pass) {
            adj.setStatus("REJECTED");
            adjustMapper.updateById(adj);
            return adj;
        }
        for (StockAdjustLine l : adjustLines(id)) {
            Inventory inv = inventoryMapper.selectById(l.getInventoryId());
            if (inv == null) {
                throw new BizException("库存记录已不存在: " + l.getLocationCode() + " " + l.getItemCode());
            }
            if (inv.getQty().compareTo(l.getFromQty()) != 0) {
                throw new BizException("库存已变动(现有 " + inv.getQty() + " ≠ 账面 " + l.getFromQty() + "), 请重新盘点: "
                        + l.getLocationCode() + " " + l.getItemCode());
            }
            lock(inv.getId(), null, false);
            inventoryService.adjust(inv.getId(), l.getToQty(), "盘点" + l.getReason(), adj.getCode());
            if (l.getToQty().signum() > 0) {
                lock(inv.getId(), adj.getPlanId(), true);
            }
        }
        adj.setStatus("APPROVED");
        adjustMapper.updateById(adj);
        return adj;
    }

    // ------------------------------------------------------------------ report

    public Map<String, Object> report(Long planId) {
        CountPlan p = require(planId);
        List<CountTask> tasks = tasks(planId);
        List<RecountTask> recounts = recounts(planId);
        long done = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        long firstDiff = tasks.stream().filter(t -> "DONE".equals(t.getStatus()) && t.getDiffQty() != null && t.getDiffQty().signum() != 0).count();
        long finalMismatch = recounts.stream().filter(r -> "MISMATCH".equals(r.getFinalResult())).count();
        BigDecimal systemQty = tasks.stream().map(CountTask::getSystemQty).filter(q -> q != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (RecountTask r : recounts) {
            if ("MISMATCH".equals(r.getFinalResult()) && r.getFinalDiff() != null) {
                if (r.getFinalDiff().signum() > 0) gain = gain.add(r.getFinalDiff());
                else loss = loss.add(r.getFinalDiff().abs());
            }
        }
        int maxRound = recounts.stream().mapToInt(r -> r.getRoundNo() == null ? 0 : r.getRoundNo()).max().orElse(0);
        List<StockAdjust> adjusts = adjustMapper.selectList(new LambdaQueryWrapper<StockAdjust>().eq(StockAdjust::getPlanId, planId));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", p);
        m.put("taskCount", tasks.size());
        m.put("doneCount", done);
        m.put("progressPercent", pct(done, tasks.size()));
        m.put("firstDiffCount", firstDiff);
        m.put("finalMismatchCount", finalMismatch);
        m.put("accuracyPercent", tasks.isEmpty() ? BigDecimal.ZERO : pct(tasks.size() - finalMismatch, tasks.size()));
        m.put("systemQty", systemQty);
        m.put("gainQty", gain);
        m.put("lossQty", loss);
        m.put("maxRecountRound", maxRound);
        m.put("adjusts", adjusts);
        m.put("mismatches", recounts.stream().filter(r -> "MISMATCH".equals(r.getFinalResult())).collect(Collectors.toList()));
        return m;
    }

    private static BigDecimal pct(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }

    private static String operator() {
        User u = CurrentUser.get();
        return u == null ? "system" : u.getUsername();
    }
}
