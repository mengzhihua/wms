package com.wms.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.BizException;
import com.wms.common.R;
import com.wms.inventory.entity.Serial;
import com.wms.inventory.mapper.SerialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/serial")
@RequiredArgsConstructor
public class SerialController {
    private final SerialMapper serialMapper;

    @GetMapping("/page")
    public R<Page<Serial>> page(@RequestParam(defaultValue = "1") long current,
                                @RequestParam(defaultValue = "20") long size,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String itemCode,
                                @RequestParam(required = false) String keyword) {
        QueryWrapper<Serial> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .eq(StringUtils.isNotBlank(itemCode), "item_code", itemCode)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("serial_no", keyword).or().like("asn_code", keyword).or().like("order_code", keyword))
                .orderByDesc("id");
        return R.ok(serialMapper.selectPage(new Page<>(current, size), qw));
    }

    /** 单个 SN 追溯 */
    @GetMapping("/{serialNo}")
    public R<Serial> trace(@PathVariable String serialNo, @RequestParam(required = false) String ownerCode) {
        QueryWrapper<Serial> qw = new QueryWrapper<>();
        qw.eq("serial_no", serialNo).eq(StringUtils.isNotBlank(ownerCode), "owner_code", ownerCode).last("LIMIT 1");
        Serial s = serialMapper.selectOne(qw);
        if (s == null) {
            throw new BizException("序列号不存在: " + serialNo);
        }
        return R.ok(s);
    }
}
