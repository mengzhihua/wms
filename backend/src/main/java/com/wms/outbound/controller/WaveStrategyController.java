package com.wms.outbound.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.R;
import com.wms.outbound.entity.WaveStrategy;
import com.wms.outbound.mapper.WaveStrategyMapper;
import com.wms.outbound.service.WaveStrategyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outbound/wave-strategy")
@RequiredArgsConstructor
public class WaveStrategyController {

    private final WaveStrategyMapper mapper;
    private final WaveStrategyService service;

    @Data
    public static class RunReq {
        private String warehouseCode;
        private Long strategyId;
        private Boolean dryRun;
    }

    @GetMapping("/list")
    public R<List<WaveStrategy>> list() {
        return R.ok(mapper.selectList(new LambdaQueryWrapper<WaveStrategy>()
                .orderByAsc(WaveStrategy::getPriority).orderByAsc(WaveStrategy::getId)));
    }

    @GetMapping("/{id}")
    public R<WaveStrategy> get(@PathVariable Long id) {
        return R.ok(service.require(id));
    }

    @PostMapping
    public R<WaveStrategy> create(@RequestBody WaveStrategy req) {
        req.setId(null);
        return R.ok(service.save(req));
    }

    @PutMapping("/{id}")
    public R<WaveStrategy> update(@PathVariable Long id, @RequestBody WaveStrategy req) {
        service.require(id);
        req.setId(id);
        return R.ok(service.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.require(id);
        mapper.deleteById(id);
        return R.ok(null);
    }

    @PostMapping("/{id}/toggle")
    public R<WaveStrategy> toggle(@PathVariable Long id) {
        WaveStrategy s = service.require(id);
        s.setEnabled(!Boolean.TRUE.equals(s.getEnabled()));
        mapper.updateById(s);
        return R.ok(s);
    }

    /** 执行(或预览)策略成波 */
    @PostMapping("/run")
    public R<List<WaveStrategyService.RunResult>> run(@RequestBody RunReq req) {
        return R.ok(service.run(req.getWarehouseCode(), req.getStrategyId(), Boolean.TRUE.equals(req.getDryRun())));
    }
}
