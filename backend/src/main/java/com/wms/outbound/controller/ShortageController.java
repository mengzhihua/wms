package com.wms.outbound.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.R;
import com.wms.outbound.entity.Shortage;
import com.wms.outbound.mapper.ShortageMapper;
import com.wms.outbound.service.ShortageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/outbound/shortage")
@RequiredArgsConstructor
public class ShortageController {

    private final ShortageMapper mapper;
    private final ShortageService service;

    @Data
    public static class RegisterReq {
        private BigDecimal qty;
        private String reason;
    }

    @Data
    public static class CloseReq {
        private String remark;
    }

    @GetMapping("/page")
    public R<Page<Shortage>> page(@RequestParam(defaultValue = "1") long current,
                                  @RequestParam(defaultValue = "20") long size,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String keyword) {
        QueryWrapper<Shortage> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("order_code", keyword).or().like("item_code", keyword)
                        .or().like("location_code", keyword))
                .orderByDesc("id");
        return R.ok(mapper.selectPage(new Page<>(current, size), qw));
    }

    /** 普通拣货任务登记缺货 */
    @PostMapping("/task/{taskId}")
    public R<Shortage> register(@PathVariable Long taskId, @RequestBody RegisterReq req) {
        return R.ok(service.register(taskId, req.getQty(), req.getReason()));
    }

    /** 波次总拣任务登记缺货 */
    @PostMapping("/wave-task/{taskId}")
    public R<List<Shortage>> registerWave(@PathVariable Long taskId, @RequestBody RegisterReq req) {
        return R.ok(service.registerWave(taskId, req.getQty(), req.getReason()));
    }

    @PostMapping("/{id}/close")
    public R<Shortage> close(@PathVariable Long id, @RequestBody(required = false) CloseReq req) {
        return R.ok(service.close(id, req == null ? null : req.getRemark()));
    }
}
