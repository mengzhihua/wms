package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wms.basic.entity.Item;
import com.wms.basic.entity.Location;
import com.wms.basic.entity.Lpn;
import com.wms.basic.entity.PickSlot;
import com.wms.basic.entity.VmiAgreement;
import com.wms.basic.mapper.ItemMapper;
import com.wms.basic.mapper.LocationMapper;
import com.wms.basic.mapper.LpnMapper;
import com.wms.basic.mapper.PickSlotMapper;
import com.wms.basic.mapper.VmiAgreementMapper;
import com.wms.basic.service.WarehouseProfileService;
import com.wms.common.BizException;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTxn;
import com.wms.inventory.entity.Serial;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryTxnMapper;
import com.wms.inventory.mapper.SerialMapper;
import com.wms.system.auth.CurrentUser;
import com.wms.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core stock engine. Every stock change goes through here and writes a transaction record.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String FROZEN = "FROZEN";
    public static final String DAMAGE = "DAMAGE";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String VENDOR = "VENDOR";

    private final InventoryMapper inventoryMapper;
    private final InventoryTxnMapper txnMapper;
    private final LocationMapper locationMapper;
    private final SerialMapper serialMapper;
    private final ItemMapper itemMapper;
    private final LpnMapper lpnMapper;
    private final PickSlotMapper pickSlotMapper;
    private final VmiAgreementMapper vmiAgreementMapper;
    private final WarehouseProfileService profiles;

    // ------------------------------------------------------------------ basic movements

    /** Put stock into a location, merging with an identical record (same location/owner/item/lot/ref/status). */
    @Transactional
    public Inventory add(String warehouse, String location, String owner, String item, String lot,
                         BigDecimal qty, LocalDate expiry, String refNo, String txnType, BigDecimal reserved) {
        return add(warehouse, location, owner, item, lot, qty, expiry, refNo, txnType, reserved, "", CUSTOMER);
    }

    /** 带容器和物权入库。lpn 空表示散货，ownership 空按货主库存。 */
    @Transactional
    public Inventory add(String warehouse, String location, String owner, String item, String lot,
                         BigDecimal qty, LocalDate expiry, String refNo, String txnType, BigDecimal reserved,
                         String lpnCode, String ownership) {
        Inventory inv = upsert(warehouse, location, owner, item, lot, qty, expiry, refNo, reserved, lpnCode, ownership, AVAILABLE);
        txn(txnType, inv, null, location, qty, refNo, null);
        if (lpnCode != null && !lpnCode.isEmpty()) {
            relocateLpn(lpnCode, warehouse, location);
        }
        return inv;
    }

    private Inventory upsert(String warehouse, String location, String owner, String item, String lot,
                             BigDecimal qty, LocalDate expiry, String refNo, BigDecimal reserved) {
        return upsert(warehouse, location, owner, item, lot, qty, expiry, refNo, reserved, "", CUSTOMER, AVAILABLE);
    }

    private Inventory upsert(String warehouse, String location, String owner, String item, String lot,
                             BigDecimal qty, LocalDate expiry, String refNo, BigDecimal reserved,
                             String lpnCode, String ownership, String status) {
        requirePositive(qty);
        Location loc = lockLocation(warehouse, location);
        if ("DISABLED".equals(loc.getStatus())) {
            throw new BizException("库位 " + location + " 已禁用");
        }
        String lotNo = lot == null ? "" : lot;
        String lpn = lpnCode == null ? "" : lpnCode;
        String own = ownership == null || ownership.isEmpty() ? CUSTOMER : ownership;
        String st = status == null || status.isEmpty() ? AVAILABLE : status;
        String ref = refNo == null ? "" : refNo;
        Inventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouse)
                .eq(Inventory::getLocationCode, location)
                .eq(Inventory::getOwnerCode, owner)
                .eq(Inventory::getItemCode, item)
                .eq(Inventory::getLotNo, lotNo)
                .eq(Inventory::getRefNo, ref)
                .eq(Inventory::getStatus, st)
                .eq(Inventory::getLpnCode, lpn)
                .eq(Inventory::getOwnership, own)
                .eq(expiry != null, Inventory::getExpiryDate, expiry)
                .isNull(expiry == null, Inventory::getExpiryDate)
                .last("LIMIT 1"));
        if (inv != null) {
            inv = tryLockInventory(inv.getId());
            if (inv != null && !st.equals(inv.getStatus())) {
                inv = null;
            }
        }
        if (inv == null) {
            inv = new Inventory();
            inv.setWarehouseCode(warehouse);
            inv.setLocationCode(location);
            inv.setOwnerCode(owner);
            inv.setItemCode(item);
            inv.setLotNo(lotNo);
            inv.setQty(qty);
            inv.setAllocatedQty(reserved == null ? BigDecimal.ZERO : reserved);
            inv.setStatus(st);
            inv.setReceiveDate(LocalDate.now());
            inv.setExpiryDate(expiry);
            inv.setRefNo(ref);
            inv.setLpnCode(lpn);
            inv.setOwnership(own);
            inventoryMapper.insert(inv);
        } else {
            requireNotCountLocked(inv);
            inv.setQty(inv.getQty().add(qty));
            if (reserved != null) {
                inv.setAllocatedQty(nz(inv.getAllocatedQty()).add(reserved));
            }
            inventoryMapper.updateById(inv);
        }
        return inv;
    }

    /** Remove stock from a record; deletes it when it reaches zero. */
    @Transactional
    public void deduct(Long inventoryId, BigDecimal qty, boolean releaseAllocation, String refNo, String txnType) {
        requirePositive(qty);
        Inventory inv = lockInventory(inventoryId);
        requireNotCountLocked(inv);
        if (inv.getQty().compareTo(qty) < 0) {
            throw new BizException("库存不足: 库位 " + inv.getLocationCode() + " 物料 " + inv.getItemCode()
                    + " 现有 " + inv.getQty() + ", 需要 " + qty);
        }
        inv.setQty(inv.getQty().subtract(qty));
        if (releaseAllocation) {
            inv.setAllocatedQty(nz(inv.getAllocatedQty()).subtract(qty).max(BigDecimal.ZERO));
        }
        if (inv.getAllocatedQty().compareTo(inv.getQty()) > 0) {
            throw new BizException("扣减数量超过未分配库存");
        }
        txn(txnType, inv, inv.getLocationCode(), null, qty, refNo, null);
        saveOrRemove(inv);
    }

    /** 移库 */
    @Transactional
    public Inventory move(Long inventoryId, BigDecimal qty, String toLocation, String refNo, String txnType) {
        return move(inventoryId, qty, toLocation, refNo, txnType, null);
    }

    /** Move stock; {@code targetRefNo} overrides the reservation reference on the destination record (null = keep). */
    @Transactional
    public Inventory move(Long inventoryId, BigDecimal qty, String toLocation, String refNo, String txnType, String targetRefNo) {
        requirePositive(qty);
        Inventory src = lockInventory(inventoryId);
        if (FROZEN.equals(src.getStatus())) {
            throw new BizException("冻结库存不可移动");
        }
        requireNotCountLocked(src);
        BigDecimal movable = DAMAGE.equals(src.getStatus())
                ? nz(src.getQty()).subtract(nz(src.getAllocatedQty())) : src.getAvailableQty();
        if (movable.compareTo(qty) < 0) {
            throw new BizException("可用库存不足，可用 " + movable);
        }
        if (toLocation.equals(src.getLocationCode())) {
            throw new BizException("目标库位与源库位相同");
        }
        Location target = lockLocation(src.getWarehouseCode(), toLocation);
        checkMixRules(target, src);
        boolean fullLpn = src.getQty().compareTo(qty) == 0 && src.getLpnCode() != null && !src.getLpnCode().isEmpty();
        String destLpn = fullLpn ? src.getLpnCode() : "";
        assertFits(target, src.getOwnerCode(), src.getItemCode(), qty, destLpn);

        src.setQty(src.getQty().subtract(qty));
        saveOrRemove(src);
        Inventory dst = upsert(src.getWarehouseCode(), toLocation, src.getOwnerCode(), src.getItemCode(), src.getLotNo(),
                qty, src.getExpiryDate(), targetRefNo == null ? src.getRefNo() : targetRefNo, null,
                destLpn, src.getOwnership(), src.getStatus());
        if (fullLpn) {
            relocateLpn(destLpn, src.getWarehouseCode(), toLocation);
        }
        if (src.getReceiveDate() != null
                && (dst.getReceiveDate() == null || src.getReceiveDate().isBefore(dst.getReceiveDate()))) {
            dst.setReceiveDate(src.getReceiveDate());
            inventoryMapper.updateById(dst);
        }
        txn(txnType, dst, src.getLocationCode(), toLocation, qty, refNo, null);
        relocateSerials(src, toLocation, qty);
        return dst;
    }

    /** 库存移动后同步序列号所在库位；未逐一扫描的移库按登记顺序取前 qty 个 */
    private void relocateSerials(Inventory src, String toLocation, BigDecimal qty) {
        List<Serial> serials = serialMapper.selectList(new LambdaQueryWrapper<Serial>()
                .eq(Serial::getOwnerCode, src.getOwnerCode())
                .eq(Serial::getItemCode, src.getItemCode())
                .eq(Serial::getLocationCode, src.getLocationCode())
                .eq(Serial::getStatus, "IN_STOCK")
                .and(w -> {
                    if (src.getLotNo() == null || src.getLotNo().isEmpty()) {
                        w.isNull(Serial::getLotNo).or().eq(Serial::getLotNo, "");
                    } else {
                        w.eq(Serial::getLotNo, src.getLotNo());
                    }
                })
                .orderByAsc(Serial::getId)
                .last("limit " + qty.intValue()));
        for (Serial s : serials) {
            s.setLocationCode(toLocation);
            serialMapper.updateById(s);
        }
    }

    /** 库存调整 (盘盈/盘亏/损益) */
    @Transactional
    public Inventory adjust(Long inventoryId, BigDecimal newQty, String reason, String refNo) {
        Inventory inv = lockInventory(inventoryId);
        requireNotCountLocked(inv);
        if (newQty == null || newQty.signum() < 0) {
            throw new BizException("调整后数量不能为负");
        }
        if (newQty.compareTo(nz(inv.getAllocatedQty())) < 0) {
            throw new BizException("调整后数量不能小于已分配数量 " + inv.getAllocatedQty());
        }
        BigDecimal diff = newQty.subtract(inv.getQty());
        if (diff.signum() == 0) {
            return inv;
        }
        inv.setQty(newQty);
        txn("ADJUST", inv, inv.getLocationCode(), inv.getLocationCode(), diff, refNo, reason);
        saveOrRemove(inv);
        return inv;
    }

    private void requireNotCountLocked(Inventory inv) {
        if (Boolean.TRUE.equals(inv.getCountLock())) {
            throw new BizException("库存已被盘点计划锁定: 库位 " + inv.getLocationCode() + " 物料 " + inv.getItemCode());
        }
    }

    /** 冻结 / 解冻 */
    @Transactional
    public Inventory setFrozen(Long inventoryId, boolean frozen, String reason) {
        Inventory inv = lockInventory(inventoryId);
        if (frozen && nz(inv.getAllocatedQty()).signum() > 0) {
            throw new BizException("存在已分配数量，不能冻结");
        }
        String target = frozen ? FROZEN : AVAILABLE;
        if (target.equals(inv.getStatus())) {
            return inv;
        }
        if (!frozen && !Boolean.TRUE.equals(inv.getCountLock())) {
            lockLocation(inv.getWarehouseCode(), inv.getLocationCode());
            Inventory twin = findMergeTarget(inv);
            if (twin != null) {
                twin.setQty(twin.getQty().add(inv.getQty()));
                twin.setAllocatedQty(nz(twin.getAllocatedQty()).add(nz(inv.getAllocatedQty())));
                if (inv.getReceiveDate() != null
                        && (twin.getReceiveDate() == null || inv.getReceiveDate().isBefore(twin.getReceiveDate()))) {
                    twin.setReceiveDate(inv.getReceiveDate());
                }
                inventoryMapper.updateById(twin);
                inventoryMapper.deleteById(inv.getId());
                txn("UNFREEZE", twin, inv.getLocationCode(), inv.getLocationCode(), inv.getQty(), null, reason);
                return twin;
            }
        }
        inv.setStatus(target);
        inventoryMapper.updateById(inv);
        txn(frozen ? "FREEZE" : "UNFREEZE", inv, inv.getLocationCode(), inv.getLocationCode(), inv.getQty(), null, reason);
        return inv;
    }

    /** 解冻时查找同合并键(库位/货主/物料/批次/效期/引用号)的另一条可用未锁定记录, 需已持有库位行锁 */
    private Inventory findMergeTarget(Inventory inv) {
        return inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .ne(Inventory::getId, inv.getId())
                .eq(Inventory::getWarehouseCode, inv.getWarehouseCode())
                .eq(Inventory::getLocationCode, inv.getLocationCode())
                .eq(Inventory::getOwnerCode, inv.getOwnerCode())
                .eq(Inventory::getItemCode, inv.getItemCode())
                .eq(Inventory::getLotNo, inv.getLotNo() == null ? "" : inv.getLotNo())
                .eq(Inventory::getRefNo, inv.getRefNo() == null ? "" : inv.getRefNo())
                .eq(Inventory::getStatus, AVAILABLE)
                .and(w -> w.isNull(Inventory::getCountLock).or().eq(Inventory::getCountLock, false))
                .eq(inv.getExpiryDate() != null, Inventory::getExpiryDate, inv.getExpiryDate())
                .isNull(inv.getExpiryDate() == null, Inventory::getExpiryDate)
                .last("LIMIT 1 FOR UPDATE"));
    }

    // ------------------------------------------------------------------ allocation

    public static class Allocation {
        public Inventory inventory;
        public BigDecimal qty;
    }

    /**
     * 分配：只从存储位和拣货位、引用号为空的可用库存出。
     * 顺序为效期、收货日、拣货位优先、拣货顺序。大件整托在数量够一整托时排在最前。
     * 销售单默认只分货主库存；VMI 且协议为发运转移时可以分寄售库存。退供只分寄售库存。
     */
    @Transactional
    public List<Allocation> allocate(String warehouse, String owner, String item, String lot, BigDecimal qty, String refNo) {
        return allocate(warehouse, owner, item, lot, qty, refNo, null);
    }

    @Transactional
    public List<Allocation> allocate(String warehouse, String owner, String item, String lot, BigDecimal qty,
                                     String refNo, String orderType) {
        LambdaQueryWrapper<Inventory> qw = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouse)
                .eq(Inventory::getOwnerCode, owner)
                .eq(Inventory::getItemCode, item)
                .eq(Inventory::getStatus, AVAILABLE)
                .eq(Inventory::getRefNo, "");
        if (lot != null && !lot.isEmpty()) {
            qw.eq(Inventory::getLotNo, lot);
        }
        List<Inventory> candidates = inventoryMapper.selectList(qw);
        String ownMode = ownershipMode(warehouse, owner, item, orderType);
        candidates.removeIf(c -> !ownershipAllowed(c, ownMode));
        List<Location> locs = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseCode, warehouse).in(Location::getType, "STORAGE", "PICKING")
                .eq(Location::getStatus, "AVAILABLE"));
        List<String> pickable = new ArrayList<>();
        locs.forEach(l -> pickable.add(l.getCode()));
        candidates.removeIf(c -> !pickable.contains(c.getLocationCode()) || c.getAvailableQty().signum() <= 0);
        Item master = findItem(owner, item);
        BigDecimal pack = master == null || master.getPackQty() == null ? BigDecimal.ZERO : master.getPackQty();
        boolean wholePallet = profiles.isBulky(warehouse) && master != null && "PALLET".equals(master.getHandlingClass())
                && pack.signum() > 0 && qty.compareTo(pack) >= 0;
        candidates.sort(Comparator
                .comparing((Inventory i) -> wholePallet && i.getAvailableQty().compareTo(pack) >= 0 ? 0 : 1)
                .thenComparing(i -> i.getExpiryDate() == null ? LocalDate.MAX : i.getExpiryDate())
                .thenComparing(i -> i.getReceiveDate() == null ? LocalDate.MIN : i.getReceiveDate())
                .thenComparing(i -> locationRank(locs, i.getLocationCode()))
                .thenComparing(i -> pickSeq(locs, i.getLocationCode()))
                .thenComparing(Inventory::getId));

        List<Allocation> result = new ArrayList<>();
        BigDecimal remain = qty;
        for (Inventory inv : candidates) {
            if (remain.signum() <= 0) {
                break;
            }
            BigDecimal take = inv.getAvailableQty().min(remain);
            int updated = inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                    .eq(Inventory::getId, inv.getId())
                    .eq(Inventory::getStatus, AVAILABLE)
                    .and(w -> w.isNull(Inventory::getCountLock).or().eq(Inventory::getCountLock, false))
                    .apply("qty - allocated_qty >= {0}", take)
                    .setSql("allocated_qty = allocated_qty + " + take.toPlainString()));
            if (updated == 0) {
                continue;
            }
            inv = inventoryMapper.selectById(inv.getId());
            txn("ALLOCATE", inv, inv.getLocationCode(), null, take, refNo, null);
            Allocation a = new Allocation();
            a.inventory = inv;
            a.qty = take;
            result.add(a);
            remain = remain.subtract(take);
        }
        return result;
    }

    @Transactional
    public void release(Long inventoryId, BigDecimal qty, String refNo) {
        Inventory inv = tryLockInventory(inventoryId);
        if (inv == null) {
            return;
        }
        inv.setAllocatedQty(nz(inv.getAllocatedQty()).subtract(qty).max(BigDecimal.ZERO));
        inventoryMapper.updateById(inv);
        txn("RELEASE", inv, inv.getLocationCode(), null, qty, refNo, null);
    }

    // ------------------------------------------------------------------ putaway strategy

    /**
     * Putaway strategy: 1) storage location already holding same item+lot (if mixing allowed there is no need),
     * 2) first empty STORAGE location by pick sequence. Falls back to null when nothing fits.
     */
    public String suggestPutawayLocation(String warehouse, String owner, String item, String lot) {
        return suggestPutawayLocation(warehouse, owner, item, lot, Collections.emptyList());
    }

    /**
     * @param reserved pseudo-inventory for locations already promised to pending putaway tasks,
     *                 treated as occupied so parallel receipts do not all get the same empty bin
     */
    public String suggestPutawayLocation(String warehouse, String owner, String item, String lot, List<Inventory> reserved) {
        return suggestPutawayLocation(warehouse, owner, item, lot, reserved, null);
    }

    /** qty 用于承重、容积和快消拣货位上限。放不下的库位不推荐。 */
    public String suggestPutawayLocation(String warehouse, String owner, String item, String lot,
                                          List<Inventory> reserved, BigDecimal qty) {
        Item master = findItem(owner, item);
        String slot = fmcgPickFace(warehouse, owner, item, master, qty);
        if (slot != null) {
            return slot;
        }
        List<Inventory> same = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouse)
                .eq(Inventory::getOwnerCode, owner)
                .eq(Inventory::getItemCode, item)
                .eq(Inventory::getLotNo, lot == null ? "" : lot)
                .eq(Inventory::getStatus, AVAILABLE));
        List<Location> storage = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseCode, warehouse)
                .eq(Location::getType, "STORAGE")
                .eq(Location::getStatus, "AVAILABLE")
                .orderByAsc(Location::getPickSeq).orderByAsc(Location::getCode));
        for (Inventory inv : same) {
            Location held = storage.stream().filter(l -> l.getCode().equals(inv.getLocationCode())).findFirst().orElse(null);
            if (held != null && fits(held, owner, item, qty, "")) {
                return held.getCode();
            }
        }
        List<Inventory> occupied = new ArrayList<>(inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouse).gt(Inventory::getQty, 0)));
        occupied.addAll(reserved);
        List<Location> candidates = new ArrayList<>();
        for (Location l : storage) {
            if (!mixOk(l, occupied, owner, item)) {
                continue;
            }
            if (!fits(l, owner, item, qty, "")) {
                continue;
            }
            candidates.add(l);
        }
        boolean pallet = profiles.isBulky(warehouse) && master != null && "PALLET".equals(master.getHandlingClass());
        candidates.sort(Comparator
                .comparing((Location l) -> occupied.stream().anyMatch(i -> l.getCode().equals(i.getLocationCode())) ? 1 : 0)
                .thenComparing(l -> pallet && l.getPalletSlots() != null && l.getPalletSlots() > 0 ? 0 : 1)
                .thenComparing(l -> abcRank(l, master))
                .thenComparing(l -> l.getPickSeq() == null ? Integer.MAX_VALUE : l.getPickSeq())
                .thenComparing(Location::getCode));
        return candidates.isEmpty() ? null : candidates.get(0).getCode();
    }

    // ------------------------------------------------------------------ helpers

    public Location requireLocation(String warehouse, String code) {
        Location loc = locationMapper.selectOne(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseCode, warehouse).eq(Location::getCode, code));
        if (loc == null) {
            throw new BizException("库位不存在: " + code);
        }
        return loc;
    }

    /** 行锁库位, 使同库位的库存合并/新建串行, 避免并发首次入库产生重复库存行 */
    private Location lockLocation(String warehouse, String code) {
        Location loc = locationMapper.selectOne(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseCode, warehouse).eq(Location::getCode, code).last("FOR UPDATE"));
        if (loc == null) {
            throw new BizException("库位不存在: " + code);
        }
        return loc;
    }

    public Location requireLocationByType(String warehouse, String type) {
        Location loc = locationMapper.selectOne(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseCode, warehouse).eq(Location::getType, type)
                .eq(Location::getStatus, "AVAILABLE").orderByAsc(Location::getId).last("LIMIT 1"));
        if (loc == null) {
            throw new BizException("仓库 " + warehouse + " 缺少 " + type + " 类型库位，请先在基础数据中维护");
        }
        return loc;
    }

    public Inventory requireInventory(Long id) {
        Inventory inv = inventoryMapper.selectById(id);
        if (inv == null) {
            throw new BizException("库存记录不存在: " + id);
        }
        return inv;
    }

    /** 当前事务内行锁读取(SELECT ... FOR UPDATE), 供扣减/移库/调整/盘点加锁等写操作串行化 */
    @Transactional
    public Inventory lockInventory(Long id) {
        Inventory inv = tryLockInventory(id);
        if (inv == null) {
            throw new BizException("库存记录不存在: " + id);
        }
        return inv;
    }

    /** 行锁读取, 记录已不存在(被并发扣减删除)时返回 null */
    @Transactional
    public Inventory tryLockInventory(Long id) {
        return inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getId, id).last("FOR UPDATE"));
    }

    private void checkMixRules(Location target, Inventory src) {
        if ("DISABLED".equals(target.getStatus()) || "FROZEN".equals(target.getStatus())) {
            throw new BizException("目标库位状态为 " + target.getStatus() + "，不可入库");
        }
        if (!"STORAGE".equals(target.getType()) && !"PICKING".equals(target.getType())) {
            return;
        }
        List<Inventory> existing = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, target.getWarehouseCode())
                .eq(Inventory::getLocationCode, target.getCode()).gt(Inventory::getQty, 0));
        for (Inventory e : existing) {
            boolean sameItem = e.getItemCode().equals(src.getItemCode()) && e.getOwnerCode().equals(src.getOwnerCode());
            if (!sameItem && !Boolean.TRUE.equals(target.getMixSku())) {
                throw new BizException("库位 " + target.getCode() + " 不允许混放不同物料");
            }
            if (sameItem && !e.getLotNo().equals(src.getLotNo()) && !Boolean.TRUE.equals(target.getMixLot())) {
                throw new BizException("库位 " + target.getCode() + " 不允许混放不同批次");
            }
        }
    }

    private void saveOrRemove(Inventory inv) {
        if (inv.getQty().signum() == 0 && nz(inv.getAllocatedQty()).signum() == 0) {
            inventoryMapper.deleteById(inv.getId());
        } else {
            inventoryMapper.updateById(inv);
        }
    }

    private void txn(String type, Inventory inv, String from, String to, BigDecimal qty, String refNo, String remark) {
        InventoryTxn t = new InventoryTxn();
        t.setTxnType(type);
        t.setWarehouseCode(inv.getWarehouseCode());
        t.setOwnerCode(inv.getOwnerCode());
        t.setItemCode(inv.getItemCode());
        t.setLotNo(inv.getLotNo());
        t.setFromLocation(from);
        t.setToLocation(to);
        t.setQty(qty);
        t.setRefNo(refNo);
        User u = CurrentUser.get();
        t.setOperator(u != null ? u.getUsername() : "system");
        t.setRemark(remark);
        txnMapper.insert(t);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void requirePositive(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new BizException("数量必须大于0");
        }
    }

    /** 寄售消耗：先记物权转移，再按数量扣减。剩余仍属供应商。 */
    @Transactional
    public void issueVendor(Long inventoryId, BigDecimal qty, String refNo) {
        requirePositive(qty);
        Inventory inv = lockInventory(inventoryId);
        String own = inv.getOwnership() == null || inv.getOwnership().isEmpty() ? CUSTOMER : inv.getOwnership();
        if (!VENDOR.equals(own)) {
            throw new BizException("消耗只能扣供应商物权库存");
        }
        if (inv.getAvailableQty().compareTo(qty) < 0) {
            throw new BizException("寄售可用库存不足");
        }
        txn("OWNERSHIP_TRANSFER", inv, inv.getLocationCode(), inv.getLocationCode(), qty, refNo, "VENDOR -> CUSTOMER");
        deduct(inventoryId, qty, false, refNo, "CONSUME");
    }

    /** 破损库存不可分配。状态保持 DAMAGE，可用量计算已经排除。 */
    @Transactional
    public Inventory markDamage(Long inventoryId, String reason) {
        Inventory inv = lockInventory(inventoryId);
        requireNotCountLocked(inv);
        if (DAMAGE.equals(inv.getStatus())) {
            return inv;
        }
        if (!AVAILABLE.equals(inv.getStatus())) {
            throw new BizException("只有可用库存可以转破损");
        }
        inv.setStatus(DAMAGE);
        inventoryMapper.updateById(inv);
        txn("DAMAGE", inv, inv.getLocationCode(), inv.getLocationCode(), inv.getQty(), null, reason);
        return inv;
    }

    /** 物权转移。数量不变，单独记 OWNERSHIP_TRANSFER。目标行已存在时合并。 */
    @Transactional
    public Inventory transferOwnership(Long inventoryId, String ownership, String refNo) {
        Inventory inv = lockInventory(inventoryId);
        String targetOwn = ownership == null || ownership.isEmpty() ? CUSTOMER : ownership;
        String current = inv.getOwnership() == null || inv.getOwnership().isEmpty() ? CUSTOMER : inv.getOwnership();
        if (targetOwn.equals(current)) {
            return inv;
        }
        txn("OWNERSHIP_TRANSFER", inv, inv.getLocationCode(), inv.getLocationCode(), inv.getQty(), refNo,
                current + " -> " + targetOwn);
        Inventory other = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, inv.getWarehouseCode())
                .eq(Inventory::getLocationCode, inv.getLocationCode())
                .eq(Inventory::getOwnerCode, inv.getOwnerCode())
                .eq(Inventory::getItemCode, inv.getItemCode())
                .eq(Inventory::getLotNo, inv.getLotNo() == null ? "" : inv.getLotNo())
                .eq(Inventory::getRefNo, inv.getRefNo() == null ? "" : inv.getRefNo())
                .eq(Inventory::getStatus, inv.getStatus())
                .eq(Inventory::getLpnCode, inv.getLpnCode() == null ? "" : inv.getLpnCode())
                .eq(Inventory::getOwnership, targetOwn)
                .eq(inv.getExpiryDate() != null, Inventory::getExpiryDate, inv.getExpiryDate())
                .isNull(inv.getExpiryDate() == null, Inventory::getExpiryDate)
                .ne(Inventory::getId, inv.getId())
                .last("LIMIT 1"));
        if (other == null) {
            inv.setOwnership(targetOwn);
            inventoryMapper.updateById(inv);
            return inv;
        }
        other = lockInventory(other.getId());
        other.setQty(other.getQty().add(inv.getQty()));
        other.setAllocatedQty(nz(other.getAllocatedQty()).add(nz(inv.getAllocatedQty())));
        inventoryMapper.updateById(other);
        inventoryMapper.deleteById(inv.getId());
        return other;
    }

    public void assertFits(Location loc, String owner, String itemCode, BigDecimal qty, String incomingLpn) {
        if (loc == null || qty == null || qty.signum() <= 0) {
            return;
        }
        Item item = findItem(owner, itemCode);
        if (item == null) {
            return;
        }
        List<Inventory> here = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, loc.getWarehouseCode())
                .eq(Inventory::getLocationCode, loc.getCode())
                .gt(Inventory::getQty, 0));
        if (loc.getMaxWeight() != null && loc.getMaxWeight().signum() > 0) {
            BigDecimal used = measure(here, true);
            BigDecimal unit = item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();
            if (used.add(unit.multiply(qty)).compareTo(loc.getMaxWeight()) > 0) {
                throw new BizException("库位 " + loc.getCode() + " 超承重，上限 " + loc.getMaxWeight() + " kg");
            }
        }
        if (loc.getMaxVolume() != null && loc.getMaxVolume().signum() > 0) {
            BigDecimal used = measure(here, false);
            BigDecimal unit = item.getVolume() == null ? BigDecimal.ZERO : item.getVolume();
            if (used.add(unit.multiply(qty)).compareTo(loc.getMaxVolume()) > 0) {
                throw new BizException("库位 " + loc.getCode() + " 超容积，上限 " + loc.getMaxVolume() + " m³");
            }
        }
        if (loc.getPalletSlots() != null && loc.getPalletSlots() > 0) {
            long pallets = here.stream().map(Inventory::getLpnCode).filter(s -> s != null && !s.isEmpty()).distinct().count();
            boolean incoming = incomingLpn != null && !incomingLpn.isEmpty();
            boolean already = incoming && here.stream().anyMatch(i -> incomingLpn.equals(i.getLpnCode()));
            int extra = 0;
            if (incoming && !already) {
                extra = 1;
            } else if (!incoming && "PALLET".equals(item.getHandlingClass())) {
                extra = 1;
            }
            if (pallets + extra > loc.getPalletSlots()) {
                throw new BizException("库位 " + loc.getCode() + " 托盘位不足，上限 " + loc.getPalletSlots());
            }
        }
    }

    public void placeLpn(String code, String type, String warehouse, String location, String owner) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        String lpnCode = code.trim();
        Lpn lpn = lpnMapper.selectOne(new LambdaQueryWrapper<Lpn>().eq(Lpn::getCode, lpnCode).last("LIMIT 1"));
        if (lpn == null) {
            lpn = new Lpn();
            lpn.setCode(lpnCode);
            lpn.setType(type == null || type.isEmpty() ? "PALLET" : type);
            lpn.setWarehouseCode(warehouse);
            lpn.setLocationCode(location);
            lpn.setOwnerCode(owner);
            lpn.setStatus("OPEN");
            lpnMapper.insert(lpn);
            return;
        }
        lpn.setWarehouseCode(warehouse);
        lpn.setLocationCode(location);
        if (owner != null) {
            lpn.setOwnerCode(owner);
        }
        lpnMapper.updateById(lpn);
    }

    private void relocateLpn(String code, String warehouse, String location) {
        Lpn lpn = lpnMapper.selectOne(new LambdaQueryWrapper<Lpn>().eq(Lpn::getCode, code).last("LIMIT 1"));
        if (lpn == null) {
            return;
        }
        lpn.setWarehouseCode(warehouse);
        lpn.setLocationCode(location);
        lpnMapper.updateById(lpn);
    }

    private String fmcgPickFace(String warehouse, String owner, String item, Item master, BigDecimal qty) {
        if (!profiles.isFmcg(warehouse) || master == null || !"A".equals(master.getAbcClass())) {
            return null;
        }
        PickSlot slot = pickSlotMapper.selectOne(new LambdaQueryWrapper<PickSlot>()
                .eq(PickSlot::getWarehouseCode, warehouse)
                .eq(PickSlot::getOwnerCode, owner)
                .eq(PickSlot::getItemCode, item)
                .last("LIMIT 1"));
        if (slot == null) {
            return null;
        }
        Location pick = locationMapper.selectOne(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseCode, warehouse)
                .eq(Location::getCode, slot.getLocationCode())
                .last("LIMIT 1"));
        if (pick == null || !"PICKING".equals(pick.getType()) || !"AVAILABLE".equals(pick.getStatus())) {
            return null;
        }
        BigDecimal onHand = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getWarehouseCode, warehouse)
                        .eq(Inventory::getLocationCode, pick.getCode())
                        .eq(Inventory::getOwnerCode, owner)
                        .eq(Inventory::getItemCode, item)
                        .gt(Inventory::getQty, 0))
                .stream().map(Inventory::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cap = slot.getMaxQty() != null ? slot.getMaxQty()
                : (master.getMaxStock() != null ? master.getMaxStock() : null);
        BigDecimal incoming = qty == null ? BigDecimal.ZERO : qty;
        if (cap != null && onHand.add(incoming).compareTo(cap) > 0) {
            return null;
        }
        if (!fits(pick, owner, item, qty, "")) {
            return null;
        }
        return pick.getCode();
    }

    private boolean fits(Location loc, String owner, String item, BigDecimal qty, String lpn) {
        try {
            assertFits(loc, owner, item, qty, lpn);
            return true;
        } catch (BizException ex) {
            return false;
        }
    }

    private static boolean mixOk(Location l, List<Inventory> occupied, String owner, String item) {
        List<Inventory> here = occupied.stream().filter(i -> l.getCode().equals(i.getLocationCode())).collect(Collectors.toList());
        if (here.isEmpty()) {
            return true;
        }
        boolean sameOnly = here.stream().allMatch(i -> item.equals(i.getItemCode()) && owner.equals(i.getOwnerCode()));
        if (sameOnly && Boolean.TRUE.equals(l.getMixLot())) {
            return true;
        }
        return !sameOnly && Boolean.TRUE.equals(l.getMixSku());
    }

    private static int abcRank(Location loc, Item item) {
        if (item == null || item.getAbcClass() == null || loc.getAbcClass() == null) {
            return 1;
        }
        return item.getAbcClass().equals(loc.getAbcClass()) ? 0 : 1;
    }

    private BigDecimal measure(List<Inventory> rows, boolean weight) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Inventory row : rows) {
            Item item = findItem(row.getOwnerCode(), row.getItemCode());
            if (item == null) {
                continue;
            }
            BigDecimal unit = weight ? item.getWeight() : item.getVolume();
            if (unit != null) {
                sum = sum.add(unit.multiply(row.getQty()));
            }
        }
        return sum;
    }

    private Item findItem(String owner, String code) {
        if (owner == null || code == null) {
            return null;
        }
        return itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                .eq(Item::getOwnerCode, owner).eq(Item::getCode, code).last("LIMIT 1"));
    }

    private String ownershipMode(String warehouse, String owner, String item, String orderType) {
        if ("RTV".equals(orderType)) {
            return "VENDOR_ONLY";
        }
        if (!profiles.isVmi(warehouse)) {
            return "CUSTOMER_ONLY";
        }
        VmiAgreement agreement = vmiAgreementMapper.selectOne(new LambdaQueryWrapper<VmiAgreement>()
                .eq(VmiAgreement::getWarehouseCode, warehouse)
                .eq(VmiAgreement::getOwnerCode, owner)
                .eq(VmiAgreement::getItemCode, item)
                .eq(VmiAgreement::getTransferPoint, "SHIP")
                .last("LIMIT 1"));
        return agreement == null ? "CUSTOMER_ONLY" : "INCLUDE_VENDOR";
    }

    private static boolean ownershipAllowed(Inventory inv, String mode) {
        String own = inv.getOwnership() == null || inv.getOwnership().isEmpty() ? CUSTOMER : inv.getOwnership();
        if ("VENDOR_ONLY".equals(mode)) {
            return VENDOR.equals(own);
        }
        if ("INCLUDE_VENDOR".equals(mode)) {
            return CUSTOMER.equals(own) || VENDOR.equals(own);
        }
        return CUSTOMER.equals(own);
    }

    private static int locationRank(List<Location> locs, String code) {
        for (Location loc : locs) {
            if (loc.getCode().equals(code)) {
                return "PICKING".equals(loc.getType()) ? 0 : 1;
            }
        }
        return 1;
    }

    private static int pickSeq(List<Location> locs, String code) {
        for (Location loc : locs) {
            if (loc.getCode().equals(code)) {
                return loc.getPickSeq() == null ? Integer.MAX_VALUE : loc.getPickSeq();
            }
        }
        return Integer.MAX_VALUE;
    }

    /** 按箱规向上取整。箱规为空或不超过 1 时保持原数量。 */
    public static BigDecimal roundUpToPack(BigDecimal qty, BigDecimal pack) {
        if (qty == null || qty.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (pack == null || pack.compareTo(BigDecimal.ONE) <= 0) {
            return qty;
        }
        BigDecimal[] parts = qty.divideAndRemainder(pack);
        if (parts[1].signum() == 0) {
            return qty.setScale(3, RoundingMode.HALF_UP);
        }
        return parts[0].add(BigDecimal.ONE).multiply(pack);
    }
}
