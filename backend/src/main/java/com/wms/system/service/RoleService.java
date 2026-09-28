package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.system.auth.Permission;
import com.wms.system.entity.Role;
import com.wms.system.entity.User;
import com.wms.system.mapper.RoleMapper;
import com.wms.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 角色与权限码解析; 角色权限缓存在内存, 角色变更时失效 */
@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();

    /** 角色的权限码集合; 角色不存在或停用时为空集 */
    public Set<String> permsOf(String roleCode) {
        if (roleCode == null) {
            return Collections.emptySet();
        }
        return cache.computeIfAbsent(roleCode, code -> {
            Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, code));
            if (role == null || role.getStatus() == null || role.getStatus() != 1) {
                return Collections.emptySet();
            }
            return parse(role.getPerms());
        });
    }

    public boolean has(String roleCode, String perm) {
        Set<String> perms = permsOf(roleCode);
        return perms.contains(Permission.ALL) || perms.contains(perm);
    }

    public boolean exists(String roleCode) {
        return roleCode != null && roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode)) > 0;
    }

    public void evict() {
        cache.clear();
    }

    /** 新增/修改前校验: 编码/名称必填, 权限码合法, 内置角色不可改编码, ADMIN 权限固定为全部 */
    @Transactional
    public Role save(Long id, Role incoming) {
        if (incoming.getCode() == null || incoming.getCode().trim().isEmpty()) {
            throw new BizException("角色编码不能为空");
        }
        if (incoming.getName() == null || incoming.getName().trim().isEmpty()) {
            throw new BizException("角色名称不能为空");
        }
        incoming.setCode(incoming.getCode().trim().toUpperCase());
        Set<String> perms = parse(incoming.getPerms());
        for (String p : perms) {
            if (!Permission.isKnown(p)) {
                throw new BizException("未知权限码: " + p);
            }
        }
        if (incoming.getStatus() == null) {
            incoming.setStatus(1);
        }
        Role dup = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, incoming.getCode()));
        if (dup != null && !dup.getId().equals(id)) {
            throw new BizException("角色编码已存在: " + incoming.getCode());
        }
        if (id == null) {
            incoming.setId(null);
            incoming.setBuiltin(false);
            incoming.setPerms(String.join(",", perms));
            roleMapper.insert(incoming);
        } else {
            Role db = require(id);
            if (Boolean.TRUE.equals(db.getBuiltin())) {
                if (!db.getCode().equals(incoming.getCode())) {
                    throw new BizException("内置角色不可修改编码");
                }
                if (User.ADMIN.equals(db.getCode())) {
                    perms = Collections.singleton(Permission.ALL);
                    incoming.setStatus(1);
                }
            }
            incoming.setId(id);
            incoming.setBuiltin(db.getBuiltin());
            incoming.setPerms(String.join(",", perms));
            roleMapper.updateById(incoming);
        }
        evict();
        return roleMapper.selectById(incoming.getId());
    }

    @Transactional
    public void delete(Long id) {
        Role db = require(id);
        if (Boolean.TRUE.equals(db.getBuiltin())) {
            throw new BizException("内置角色不可删除");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, db.getCode())) > 0) {
            throw new BizException("仍有用户使用该角色, 不可删除");
        }
        roleMapper.deleteById(id);
        evict();
    }

    public List<Role> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
    }

    private Role require(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) {
            throw new BizException("角色不存在: " + id);
        }
        return r;
    }

    static Set<String> parse(String perms) {
        if (perms == null || perms.trim().isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(perms.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
