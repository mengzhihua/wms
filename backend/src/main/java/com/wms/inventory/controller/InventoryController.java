package com.wms.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.Csv;
import com.wms.common.R;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTxn;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryTxnMapper;
import com.wms.inventory.service.InventoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryMapper inventoryMapper;
    private final InventoryTxnMapper txnMapper;
    private final InventoryService inventoryService;

    @GetMapping("/page")
    public R<Page<Inventory>> page(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "20") long size,
                                   @RequestParam(required = false) String warehouseCode,
                                   @RequestParam(required = false) String locationCode,
                                   @RequestParam(required = false) String ownerCode,
                                   @RequestParam(required = false) String itemCode,
                                   @RequestParam(required = false) String lotNo,
                                   @RequestParam(required = false) String status) {
        QueryWrapper<Inventory> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(warehouseCode), "warehouse_code", warehouseCode)
                .like(StringUtils.isNotBlank(locationCode), "location_code", locationCode)
                .eq(StringUtils.isNotBlank(ownerCode), "owner_code", ownerCode)
                .like(StringUtils.isNotBlank(itemCode), "item_code", itemCode)
                .like(StringUtils.isNotBlank(lotNo), "lot_no", lotNo)
                .eq(StringUtils.isNotBlank(status), "status", status)
                .orderByAsc("location_code", "item_code", "lot_no");
        return R.ok(inventoryMapper.selectPage(new Page<>(current, size), qw));
    }

    /** 库存导出 CSV（与列表查询条件一致） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String warehouseCode,
                                         @RequestParam(required = false) String locationCode,
                                         @RequestParam(required = false) String ownerCode,
                                         @RequestParam(required = false) String itemCode,
                                         @RequestParam(required = false) String lotNo,
                                         @RequestParam(required = false) String status) {
        QueryWrapper<Inventory> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(warehouseCode), "warehouse_code", warehouseCode)
                .like(StringUtils.isNotBlank(locationCode), "location_code", locationCode)
                .eq(StringUtils.isNotBlank(ownerCode), "owner_code", ownerCode)
                .like(StringUtils.isNotBlank(itemCode), "item_code", itemCode)
                .like(StringUtils.isNotBlank(lotNo), "lot_no", lotNo)
                .eq(StringUtils.isNotBlank(status), "status", status)
                .orderByAsc("location_code", "item_code", "lot_no");
        String[] headers = {"warehouseCode", "locationCode", "ownerCode", "itemCode", "lotNo", "qty", "allocatedQty",
                "availableQty", "status", "receiveDate", "expiryDate", "refNo"};
        return Csv.download("inventory.csv", headers, inventoryMapper.selectList(qw), i -> new Object[]{i.getWarehouseCode(),
                i.getLocationCode(), i.getOwnerCode(), i.getItemCode(), i.getLotNo(), i.getQty(), i.getAllocatedQty(),
                i.getAvailableQty(), i.getStatus(), i.getReceiveDate(), i.getExpiryDate(), i.getRefNo()});
    }

    /** 库存流水导出 CSV */
    @GetMapping("/txn/export")
    public ResponseEntity<byte[]> exportTxn(@RequestParam(required = false) String txnType,
                                            @RequestParam(required = false) String itemCode,
                                            @RequestParam(required = false) String refNo,
                                            @RequestParam(defaultValue = "5000") int limit) {
        QueryWrapper<InventoryTxn> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(txnType), "txn_type", txnType)
                .like(StringUtils.isNotBlank(itemCode), "item_code", itemCode)
                .like(StringUtils.isNotBlank(refNo), "ref_no", refNo)
                .orderByDesc("id").last("LIMIT " + Math.max(1, Math.min(limit, 50000)));
        String[] headers = {"id", "createdAt", "txnType", "warehouseCode", "ownerCode", "itemCode", "lotNo", "fromLocation",
                "toLocation", "qty", "refNo", "operator", "remark"};
        return Csv.download("inventory_txn.csv", headers, txnMapper.selectList(qw), t -> new Object[]{t.getId(), t.getCreatedAt(),
                t.getTxnType(), t.getWarehouseCode(), t.getOwnerCode(), t.getItemCode(), t.getLotNo(), t.getFromLocation(),
                t.getToLocation(), t.getQty(), t.getRefNo(), t.getOperator(), t.getRemark()});
    }

    @GetMapping("/summary")
    public R<List<Map<String, Object>>> summary() {
        return R.ok(inventoryMapper.summaryByItem());
    }

    @GetMapping("/txn/page")
    public R<Page<InventoryTxn>> txnPage(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String txnType,
                                         @RequestParam(required = false) String itemCode,
                                         @RequestParam(required = false) String refNo) {
        QueryWrapper<InventoryTxn> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(txnType), "txn_type", txnType)
                .like(StringUtils.isNotBlank(itemCode), "item_code", itemCode)
                .like(StringUtils.isNotBlank(refNo), "ref_no", refNo)
                .orderByDesc("id");
        return R.ok(txnMapper.selectPage(new Page<>(current, size), qw));
    }

    @Data
    public static class MoveReq {
        private Long inventoryId;
        private BigDecimal qty;
        private String toLocation;
    }

    @PostMapping("/move")
    public R<Inventory> move(@RequestBody MoveReq req) {
        return R.ok(inventoryService.move(req.getInventoryId(), req.getQty(), req.getToLocation(), null, "MOVE", ""));
    }

    @Data
    public static class AdjustReq {
        private Long inventoryId;
        private BigDecimal newQty;
        private String reason;
    }

    @PostMapping("/adjust")
    public R<Inventory> adjust(@RequestBody AdjustReq req) {
        return R.ok(inventoryService.adjust(req.getInventoryId(), req.getNewQty(), req.getReason(), null));
    }

    @Data
    public static class FreezeReq {
        private Long inventoryId;
        private Boolean frozen;
        private String reason;
    }

    @PostMapping("/freeze")
    public R<Inventory> freeze(@RequestBody FreezeReq req) {
        return R.ok(inventoryService.setFrozen(req.getInventoryId(), Boolean.TRUE.equals(req.getFrozen()), req.getReason()));
    }
}
