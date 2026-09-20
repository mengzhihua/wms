package com.wms.outbound.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.outbound.entity.PickTask;
import com.wms.outbound.entity.Shortage;
import com.wms.outbound.entity.Wave;
import com.wms.outbound.entity.WavePickTask;
import com.wms.outbound.mapper.PickTaskMapper;
import com.wms.outbound.mapper.ShortageMapper;
import com.wms.outbound.mapper.WavePickTaskMapper;
import com.wms.system.auth.CurrentUser;
import com.wms.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 缺货登记: 拣货时登记缺货数量(缺货 + 实拣 = 计划), 任务按实拣完成, 缺口释放预留并留痕 */
@Service
@RequiredArgsConstructor
public class ShortageService {
    private final ShortageMapper shortageMapper;
    private final PickTaskMapper pickMapper;
    private final WavePickTaskMapper wavePickMapper;
    private final ShipOrderService orderService;
    private final WaveService waveService;

    /** 普通拣货任务登记缺货 */
    @Transactional
    public Shortage register(Long taskId, BigDecimal shortQty, String reason) {
        PickTask task = pickMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("拣货任务不存在");
        }
        if (!"NEW".equals(task.getStatus())) {
            throw new BizException("任务已处理");
        }
        if (task.getWaveId() != null) {
            throw new BizException("任务已加入波次，请在波次总拣中登记缺货");
        }
        if (shortQty == null || shortQty.signum() <= 0 || shortQty.compareTo(task.getQty()) > 0) {
            throw new BizException("缺货数量必须在 1 到 " + task.getQty() + " 之间");
        }
        orderService.pick(taskId, task.getQty().subtract(shortQty), true);
        return insert(pickMapper.selectById(taskId), shortQty, reason);
    }

    /** 波次总拣登记缺货: 按优先级分摊到各出库单后逐单留痕 */
    @Transactional
    public List<Shortage> registerWave(Long wavePickTaskId, BigDecimal shortQty, String reason) {
        WavePickTask wt = wavePickMapper.selectById(wavePickTaskId);
        if (wt == null) {
            throw new BizException("总拣任务不存在");
        }
        if (!"NEW".equals(wt.getStatus())) {
            throw new BizException("任务已处理");
        }
        if (shortQty == null || shortQty.signum() <= 0 || shortQty.compareTo(wt.getQty()) > 0) {
            throw new BizException("缺货数量必须在 1 到 " + wt.getQty() + " 之间");
        }
        Wave wave = waveService.pick(wavePickTaskId, wt.getQty().subtract(shortQty));
        List<Shortage> out = new ArrayList<>();
        for (PickTask t : pickMapper.selectList(new LambdaQueryWrapper<PickTask>()
                .eq(PickTask::getWaveId, wave.getId()).eq(PickTask::getInventoryId, wt.getInventoryId())
                .eq(PickTask::getStatus, "DONE").gt(PickTask::getShortQty, 0))) {
            if (shortageMapper.selectCount(new LambdaQueryWrapper<Shortage>().eq(Shortage::getTaskId, t.getId())) == 0) {
                out.add(insert(t, t.getShortQty(), reason));
            }
        }
        return out;
    }

    private Shortage insert(PickTask t, BigDecimal qty, String reason) {
        Shortage s = new Shortage();
        s.setTaskId(t.getId());
        s.setOrderId(t.getOrderId());
        s.setOrderCode(t.getOrderCode());
        s.setWaveId(t.getWaveId());
        s.setWarehouseCode(t.getWarehouseCode());
        s.setOwnerCode(t.getOwnerCode());
        s.setItemCode(t.getItemCode());
        s.setLotNo(t.getLotNo());
        s.setLocationCode(t.getFromLocation());
        s.setInventoryId(t.getInventoryId());
        s.setQty(qty);
        s.setReason(reason);
        User u = CurrentUser.get();
        s.setOperator(u == null ? "system" : u.getUsername());
        s.setStatus("OPEN");
        shortageMapper.insert(s);
        return s;
    }

    @Transactional
    public Shortage close(Long id, String remark) {
        Shortage s = shortageMapper.selectById(id);
        if (s == null) {
            throw new BizException("缺货记录不存在");
        }
        if (!"OPEN".equals(s.getStatus())) {
            throw new BizException("缺货记录已处理");
        }
        s.setStatus("CLOSED");
        if (remark != null && !remark.isEmpty()) {
            s.setReason((s.getReason() == null ? "" : s.getReason() + " | ") + remark);
        }
        shortageMapper.updateById(s);
        return s;
    }
}
