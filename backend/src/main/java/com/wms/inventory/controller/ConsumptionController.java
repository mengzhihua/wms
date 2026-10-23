package com.wms.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.R;
import com.wms.inventory.entity.Consumption;
import com.wms.inventory.mapper.ConsumptionMapper;
import com.wms.inventory.service.ConsumptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/consumption")
@RequiredArgsConstructor
public class ConsumptionController {
    private final ConsumptionService consumptionService;
    private final ConsumptionMapper consumptionMapper;

    @GetMapping("/page")
    public R<Page<Consumption>> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(required = false) String warehouseCode) {
        return R.ok(consumptionMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<Consumption>()
                .eq(warehouseCode != null && !warehouseCode.isEmpty(), Consumption::getWarehouseCode, warehouseCode)
                .orderByDesc(Consumption::getId)));
    }

    @GetMapping("/{id}")
    public R<Consumption> get(@PathVariable Long id) {
        return R.ok(consumptionService.load(id));
    }

    @PostMapping
    public R<Consumption> create(@RequestBody Consumption req) {
        return R.ok(consumptionService.create(req));
    }

    @PostMapping("/{id}/post")
    public R<Consumption> post(@PathVariable Long id) {
        return R.ok(consumptionService.post(id));
    }
}
