package com.wms.inbound.controller;

import com.wms.common.R;
import com.wms.inbound.entity.Asn;
import com.wms.inventory.service.VmiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inbound/vmi")
@RequiredArgsConstructor
public class VmiSuggestController {
    private final VmiService vmiService;

    @PostMapping("/suggest")
    public R<List<Asn>> suggest(@RequestParam String warehouseCode) {
        return R.ok(vmiService.suggest(warehouseCode));
    }
}
