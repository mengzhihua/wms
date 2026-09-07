package com.wms.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.R;
import com.wms.system.entity.OpLog;
import com.wms.system.mapper.OpLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/oplog")
@RequiredArgsConstructor
public class OpLogController {
    private final OpLogMapper mapper;

    @GetMapping("/page")
    public R<Page<OpLog>> page(@RequestParam(defaultValue = "1") long current,
                               @RequestParam(defaultValue = "20") long size,
                               @RequestParam(required = false) String username,
                               @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<OpLog> qw = new LambdaQueryWrapper<OpLog>()
                .eq(StringUtils.isNotBlank(username), OpLog::getUsername, username)
                .like(StringUtils.isNotBlank(keyword), OpLog::getPath, keyword)
                .orderByDesc(OpLog::getId);
        return R.ok(mapper.selectPage(new Page<>(current, size), qw));
    }
}
