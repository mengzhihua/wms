package com.wms.outbound.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.R;
import com.wms.outbound.entity.Package;
import com.wms.outbound.mapper.PackageMapper;
import com.wms.outbound.service.PackageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/outbound/package")
@RequiredArgsConstructor
public class PackageController {

    private final PackageMapper mapper;
    private final PackageService service;

    @Data
    public static class BuildReq {
        private Long waveId;
        private Long orderId;
        /** ONE_ORDER_ONE_PACKAGE / SPLIT_BY_WEIGHT */
        private String strategy;
        private BigDecimal maxWeight;
        private String carrier;
        private String cartonCode;
    }

    @Data
    public static class ManualReq {
        private Long orderId;
        private String carrier;
        private List<PackageService.ManualPackage> boxes;
    }

    @Data
    public static class TrackingReq {
        private String carrier;
        private String trackingNo;
        private BigDecimal weight;
    }

    @GetMapping("/page")
    public R<Page<Package>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "20") long size,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) Long waveId,
                                 @RequestParam(required = false) String keyword) {
        QueryWrapper<Package> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .eq(waveId != null, "wave_id", waveId)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("code", keyword).or().like("order_code", keyword)
                        .or().like("tracking_no", keyword))
                .orderByDesc("id");
        return R.ok(mapper.selectPage(new Page<>(current, size), qw));
    }

    @GetMapping("/{id}")
    public R<Package> get(@PathVariable Long id) {
        return R.ok(service.load(id));
    }

    @GetMapping("/by-order/{orderId}")
    public R<List<Package>> byOrder(@PathVariable Long orderId) {
        return R.ok(service.byOrder(orderId));
    }

    @GetMapping("/by-wave/{waveId}")
    public R<List<Package>> byWave(@PathVariable Long waveId) {
        return R.ok(service.byWave(waveId));
    }

    /** 按波次或按单据自动建包 */
    @PostMapping("/build")
    public R<List<Package>> build(@RequestBody BuildReq req) {
        if (req.getWaveId() != null) {
            return R.ok(service.buildForWave(req.getWaveId(), req.getStrategy(), req.getMaxWeight()));
        }
        return R.ok(service.buildForOrder(req.getOrderId(), req.getStrategy(), req.getMaxWeight(), req.getCarrier(), req.getCartonCode()));
    }

    @PostMapping("/manual")
    public R<List<Package>> manual(@RequestBody ManualReq req) {
        return R.ok(service.manual(req.getOrderId(), req.getBoxes(), req.getCarrier()));
    }

    @PostMapping("/unpack/{orderId}")
    public R<Void> unpack(@PathVariable Long orderId) {
        service.unpack(orderId);
        return R.ok(null);
    }

    @PutMapping("/{id}/tracking")
    public R<Package> tracking(@PathVariable Long id, @RequestBody TrackingReq req) {
        return R.ok(service.updateTracking(id, req.getCarrier(), req.getTrackingNo(), req.getWeight()));
    }
}
