package com.wms.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.R;
import com.wms.inventory.entity.CountPlan;
import com.wms.inventory.entity.CountTask;
import com.wms.inventory.entity.RecountTask;
import com.wms.inventory.entity.StockAdjust;
import com.wms.inventory.entity.StockAdjustLine;
import com.wms.inventory.mapper.CountPlanMapper;
import com.wms.inventory.mapper.CountTaskMapper;
import com.wms.inventory.mapper.StockAdjustMapper;
import com.wms.inventory.service.CountPlanService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/count-plan")
@RequiredArgsConstructor
public class CountPlanController {

    private final CountPlanMapper planMapper;
    private final CountTaskMapper taskMapper;
    private final StockAdjustMapper adjustMapper;
    private final CountPlanService service;

    @Data
    public static class ApproveReq {
        private Boolean pass;
        private String opinion;
    }

    @Data
    public static class QtyReq {
        private BigDecimal qty;
    }

    @Data
    public static class AssignReq {
        private String assignee;
    }

    // ---------------------------------------------------------------- plan

    @GetMapping("/page")
    public R<Page<CountPlan>> page(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "20") long size,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String keyword) {
        QueryWrapper<CountPlan> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .eq(StringUtils.isNotBlank(type), "type", type)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("code", keyword).or().like("name", keyword))
                .orderByDesc("id");
        return R.ok(planMapper.selectPage(new Page<>(current, size), qw));
    }

    @GetMapping("/{id}")
    public R<CountPlan> get(@PathVariable Long id) {
        return R.ok(service.require(id));
    }

    @PostMapping
    public R<CountPlan> create(@RequestBody CountPlan req) {
        return R.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public R<CountPlan> update(@PathVariable Long id, @RequestBody CountPlan req) {
        return R.ok(service.update(id, req));
    }

    @PostMapping("/{id}/submit")
    public R<CountPlan> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    public R<CountPlan> approve(@PathVariable Long id, @RequestBody ApproveReq req) {
        return R.ok(service.approve(id, !Boolean.FALSE.equals(req.getPass()), req.getOpinion()));
    }

    @PostMapping("/{id}/generate")
    public R<CountPlan> generate(@PathVariable Long id) {
        return R.ok(service.generateTasks(id));
    }

    @PostMapping("/{id}/complete")
    public R<CountPlan> complete(@PathVariable Long id) {
        return R.ok(service.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public R<CountPlan> cancel(@PathVariable Long id) {
        return R.ok(service.cancel(id));
    }

    @GetMapping("/{id}/report")
    public R<Map<String, Object>> report(@PathVariable Long id) {
        return R.ok(service.report(id));
    }

    // ---------------------------------------------------------------- tasks

    @GetMapping("/{id}/tasks")
    public R<List<CountTask>> tasks(@PathVariable Long id) {
        return R.ok(service.tasks(id));
    }

    @GetMapping("/task/page")
    public R<Page<CountTask>> taskPage(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String assignee,
                                       @RequestParam(required = false) String keyword) {
        QueryWrapper<CountTask> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .eq(StringUtils.isNotBlank(assignee), "assignee", assignee)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("plan_code", keyword).or().like("item_code", keyword)
                        .or().like("location_code", keyword))
                .orderByDesc("id");
        return R.ok(taskMapper.selectPage(new Page<>(current, size), qw));
    }

    @DeleteMapping("/task/{taskId}")
    public R<Void> deleteTask(@PathVariable Long taskId) {
        service.deleteTask(taskId);
        return R.ok(null);
    }

    @PostMapping("/task/{taskId}/claim")
    public R<CountTask> claim(@PathVariable Long taskId) {
        return R.ok(service.claim(taskId));
    }

    @PostMapping("/task/{taskId}/assign")
    public R<CountTask> assign(@PathVariable Long taskId, @RequestBody AssignReq req) {
        return R.ok(service.assign(taskId, req.getAssignee()));
    }

    @PostMapping("/task/{taskId}/count")
    public R<CountTask> count(@PathVariable Long taskId, @RequestBody QtyReq req) {
        return R.ok(service.submitCount(taskId, req.getQty()));
    }

    // ---------------------------------------------------------------- recount

    @GetMapping("/{id}/recounts")
    public R<List<RecountTask>> recounts(@PathVariable Long id) {
        return R.ok(service.recounts(id));
    }

    @PostMapping("/recount/{rid}/count")
    public R<RecountTask> recount(@PathVariable Long rid, @RequestBody QtyReq req) {
        return R.ok(service.submitRecount(rid, req.getQty()));
    }

    @PostMapping("/recount/{rid}/next-round")
    public R<RecountTask> nextRound(@PathVariable Long rid) {
        return R.ok(service.nextRound(rid));
    }

    @PostMapping("/recount/{rid}/confirm")
    public R<RecountTask> confirm(@PathVariable Long rid) {
        return R.ok(service.confirmRecount(rid));
    }

    // ---------------------------------------------------------------- adjust

    @PostMapping("/{id}/adjust")
    public R<StockAdjust> generateAdjust(@PathVariable Long id) {
        return R.ok(service.generateAdjust(id));
    }

    @GetMapping("/adjust/page")
    public R<Page<StockAdjust>> adjustPage(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword) {
        QueryWrapper<StockAdjust> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("code", keyword).or().like("plan_code", keyword))
                .orderByDesc("id");
        return R.ok(adjustMapper.selectPage(new Page<>(current, size), qw));
    }

    @GetMapping("/adjust/{aid}")
    public R<StockAdjust> adjust(@PathVariable Long aid) {
        return R.ok(service.requireAdjust(aid));
    }

    @GetMapping("/adjust/{aid}/lines")
    public R<List<StockAdjustLine>> adjustLines(@PathVariable Long aid) {
        return R.ok(service.adjustLines(aid));
    }

    @PostMapping("/adjust/{aid}/approve")
    public R<StockAdjust> approveAdjust(@PathVariable Long aid, @RequestBody ApproveReq req) {
        return R.ok(service.approveAdjust(aid, !Boolean.FALSE.equals(req.getPass()), req.getOpinion()));
    }
}
