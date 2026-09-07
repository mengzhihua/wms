package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.inventory.entity.Serial;
import com.wms.inventory.mapper.SerialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SerialService {
    private final SerialMapper serialMapper;

    private static Set<String> normalize(List<String> sns) {
        Set<String> out = new LinkedHashSet<>();
        if (sns == null) {
            return out;
        }
        for (String s : sns) {
            if (s != null && !s.trim().isEmpty()) {
                if (!out.add(s.trim())) {
                    throw new BizException("序列号重复: " + s.trim());
                }
            }
        }
        return out;
    }

    private static void checkCount(Set<String> sns, BigDecimal qty, String item) {
        if (qty.stripTrailingZeros().scale() > 0) {
            throw new BizException("序列号管理物料 " + item + " 数量必须为整数");
        }
        if (sns.size() != qty.intValue()) {
            throw new BizException("物料 " + item + " 需登记 " + qty.intValue() + " 个序列号，实际 " + sns.size());
        }
    }

    /** 收货登记：数量必须与 SN 个数一致；已在库的 SN 不能重复收货；已发运的 SN（退货）回到在库。 */
    @Transactional
    public void receive(String owner, String item, String lot, String location, String asnCode, BigDecimal qty, List<String> serialNos) {
        Set<String> sns = normalize(serialNos);
        checkCount(sns, qty, item);
        for (String sn : sns) {
            Serial s = serialMapper.selectOne(new LambdaQueryWrapper<Serial>()
                    .eq(Serial::getOwnerCode, owner).eq(Serial::getSerialNo, sn));
            if (s == null) {
                s = new Serial();
                s.setOwnerCode(owner);
                s.setSerialNo(sn);
            } else if ("IN_STOCK".equals(s.getStatus())) {
                throw new BizException("序列号 " + sn + " 已在库，不能重复收货");
            } else if (!item.equals(s.getItemCode())) {
                throw new BizException("序列号 " + sn + " 属于物料 " + s.getItemCode() + "，与 " + item + " 不一致");
            }
            s.setItemCode(item);
            s.setLotNo(lot == null ? "" : lot);
            s.setLocationCode(location);
            s.setAsnCode(asnCode);
            s.setOrderCode(null);
            s.setStatus("IN_STOCK");
            if (s.getId() == null) {
                serialMapper.insert(s);
            } else {
                serialMapper.updateById(s);
            }
        }
    }

    /**
     * 发运扫描：required 为各物料应发数量；serialNos 中的 SN 必须在库且属于该货主，
     * 按 SN 所属物料归集后数量必须与应发一致。
     */
    @Transactional
    public void ship(String owner, String orderCode, Map<String, BigDecimal> required, List<String> serialNos) {
        Set<String> sns = normalize(serialNos);
        Map<String, List<Serial>> byItem = new HashMap<>();
        for (String sn : sns) {
            Serial s = serialMapper.selectOne(new LambdaQueryWrapper<Serial>()
                    .eq(Serial::getOwnerCode, owner).eq(Serial::getSerialNo, sn));
            if (s == null || !"IN_STOCK".equals(s.getStatus())) {
                throw new BizException("序列号 " + sn + " 不在库");
            }
            if (!required.containsKey(s.getItemCode())) {
                throw new BizException("序列号 " + sn + " 的物料 " + s.getItemCode() + " 不在本单发运范围");
            }
            byItem.computeIfAbsent(s.getItemCode(), k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, BigDecimal> e : required.entrySet()) {
            List<Serial> list = byItem.getOrDefault(e.getKey(), new ArrayList<>());
            Set<String> got = new LinkedHashSet<>();
            list.forEach(s -> got.add(s.getSerialNo()));
            checkCount(got, e.getValue(), e.getKey());
            for (Serial s : list) {
                s.setStatus("SHIPPED");
                s.setOrderCode(orderCode);
                s.setLocationCode(null);
                serialMapper.updateById(s);
            }
        }
    }
}
