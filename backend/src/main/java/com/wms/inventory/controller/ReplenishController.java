package com.wms.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.R;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.inventory.mapper.ReplenishTaskMapper;
import com.wms.inventory.service.ReplenishService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/replenish")
@RequiredArgsConstructor
public class ReplenishController {

    private final ReplenishTaskMapper mapper;
    private final ReplenishService service;

    @GetMapping("/page")
    public R<Page<ReplenishTask>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword) {
        QueryWrapper<ReplenishTask> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("code", keyword).or().like("item_code", keyword).or().like("to_location", keyword))
                .orderByDesc("id");
        return R.ok(mapper.selectPage(new Page<>(current, size), qw));
    }

    @Data
    public static class GenerateReq {
        private String warehouseCode;
        private String ownerCode;
    }

    @PostMapping("/generate")
    public R<List<ReplenishTask>> generate(@RequestBody GenerateReq req) {
        return R.ok(service.generate(req.getWarehouseCode(), req.getOwnerCode()));
    }

    @Data
    public static class CreateReq {
        private Long inventoryId;
        private BigDecimal qty;
        private String toLocation;
    }

    @PostMapping
    public R<ReplenishTask> create(@RequestBody CreateReq req) {
        return R.ok(service.create(req.getInventoryId(), req.getQty(), req.getToLocation()));
    }

    @Data
    public static class ConfirmReq {
        private String toLocation;
    }

    @PostMapping("/{id}/confirm")
    public R<ReplenishTask> confirm(@PathVariable Long id, @RequestBody(required = false) ConfirmReq req) {
        return R.ok(service.confirm(id, req == null ? null : req.getToLocation()));
    }

    @PostMapping("/{id}/cancel")
    public R<ReplenishTask> cancel(@PathVariable Long id) {
        return R.ok(service.cancel(id));
    }
}
