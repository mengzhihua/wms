package com.wms.basic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Item;
import com.wms.basic.mapper.ItemMapper;
import com.wms.common.BaseCrudController;
import com.wms.common.BizException;
import com.wms.common.Csv;
import com.wms.common.R;
import com.wms.report.ReportController;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/basic/item")
public class ItemController extends BaseCrudController<Item, ItemMapper> {
    private static final String[] HEADERS = {"ownerCode", "code", "name", "spec", "unit", "packQty", "barcode", "category",
            "lotControl", "shelfLifeDays", "abcClass", "weight", "volume", "minStock", "maxStock", "qcRequired", "snControl", "status"};

    private final ReportController reportController;

    public ItemController(ReportController reportController) {
        super(Item.class);
        this.reportController = reportController;
    }

    /** 将 ABC 分析建议分类写回物料主数据 */
    @PostMapping("/abc-apply")
    @Transactional
    public R<Map<String, Object>> abcApply(@RequestBody Map<String, Object> req) {
        String owner = req.get("ownerCode") == null ? null : String.valueOf(req.get("ownerCode"));
        int days = req.get("days") == null ? 90 : Integer.parseInt(String.valueOf(req.get("days")));
        int updated = 0;
        for (Map<String, Object> row : reportController.abcRows(null, owner, days)) {
            Object id = row.get("itemId");
            String cls = (String) row.get("suggestedClass");
            if (id == null || cls.equals(row.get("currentClass"))) {
                continue;
            }
            Item item = mapper.selectById((Long) id);
            if (item != null) {
                item.setAbcClass(cls);
                mapper.updateById(item);
                updated++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updated", updated);
        return R.ok(out);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name", "barcode"};
    }

    /** 导出全部物料为 CSV（也可作为导入模板） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        List<Item> items = mapper.selectList(new LambdaQueryWrapper<Item>().orderByAsc(Item::getOwnerCode).orderByAsc(Item::getCode));
        return Csv.download("items.csv", HEADERS, items, i -> new Object[]{i.getOwnerCode(), i.getCode(), i.getName(), i.getSpec(),
                i.getUnit(), i.getPackQty(), i.getBarcode(), i.getCategory(), i.getLotControl(), i.getShelfLifeDays(), i.getAbcClass(),
                i.getWeight(), i.getVolume(), i.getMinStock(), i.getMaxStock(), i.getQcRequired(), i.getSnControl(), i.getStatus()});
    }

    /** CSV 导入物料：按 货主+编码 存在则更新、否则新增 */
    @PostMapping("/import")
    @Transactional
    public R<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        List<String[]> rows = Csv.read(file.getInputStream());
        if (rows.size() < 2) {
            throw new BizException("文件为空或缺少表头");
        }
        Map<String, Integer> idx = new HashMap<>();
        String[] header = rows.get(0);
        for (int i = 0; i < header.length; i++) {
            idx.put(header[i].trim(), i);
        }
        if (!idx.containsKey("ownerCode") || !idx.containsKey("code") || !idx.containsKey("name")) {
            throw new BizException("表头必须包含 ownerCode, code, name");
        }
        int inserted = 0, updated = 0;
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            String owner = cell(row, idx, "ownerCode");
            String code = cell(row, idx, "code");
            if (owner.isEmpty() || code.isEmpty()) {
                throw new BizException("第 " + (r + 1) + " 行货主或编码为空");
            }
            Item item = mapper.selectOne(new LambdaQueryWrapper<Item>().eq(Item::getOwnerCode, owner).eq(Item::getCode, code));
            boolean isNew = item == null;
            if (isNew) {
                item = new Item();
                item.setOwnerCode(owner);
                item.setCode(code);
            }
            item.setName(cell(row, idx, "name"));
            item.setSpec(cell(row, idx, "spec"));
            item.setUnit(orDefault(cell(row, idx, "unit"), "EA"));
            item.setPackQty(dec(cell(row, idx, "packQty")));
            item.setBarcode(cell(row, idx, "barcode"));
            item.setCategory(cell(row, idx, "category"));
            item.setLotControl(bool(cell(row, idx, "lotControl")));
            item.setShelfLifeDays(intOrNull(cell(row, idx, "shelfLifeDays")));
            item.setAbcClass(cell(row, idx, "abcClass"));
            item.setWeight(dec(cell(row, idx, "weight")));
            item.setVolume(dec(cell(row, idx, "volume")));
            item.setMinStock(dec(cell(row, idx, "minStock")));
            item.setMaxStock(dec(cell(row, idx, "maxStock")));
            item.setQcRequired(bool(cell(row, idx, "qcRequired")));
            item.setSnControl(bool(cell(row, idx, "snControl")));
            Integer st = intOrNull(cell(row, idx, "status"));
            item.setStatus(st == null ? 1 : st);
            if (isNew) {
                mapper.insert(item);
                inserted++;
            } else {
                mapper.updateById(item);
                updated++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", inserted);
        out.put("updated", updated);
        return R.ok(out);
    }

    private static String cell(String[] row, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name);
        return i == null || i >= row.length ? "" : row[i].trim();
    }

    private static String orDefault(String v, String d) {
        return v.isEmpty() ? d : v;
    }

    private static BigDecimal dec(String v) {
        return v.isEmpty() ? null : new BigDecimal(v);
    }

    private static Integer intOrNull(String v) {
        return v.isEmpty() ? null : Integer.valueOf(v);
    }

    private static Boolean bool(String v) {
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "是".equals(v) || "Y".equalsIgnoreCase(v);
    }
}
