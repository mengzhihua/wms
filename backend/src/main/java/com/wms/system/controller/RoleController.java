package com.wms.system.controller;

import com.wms.common.R;
import com.wms.system.auth.Permission;
import com.wms.system.entity.Role;
import com.wms.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping("/list")
    public R<List<Role>> list() {
        return R.ok(roleService.list());
    }

    /** 权限码目录: code -> 名称 */
    @GetMapping("/perms")
    public R<Map<String, String>> perms() {
        return R.ok(Permission.labels());
    }

    @PostMapping
    public R<Role> create(@RequestBody Role role) {
        return R.ok(roleService.save(null, role));
    }

    @PutMapping("/{id}")
    public R<Role> update(@PathVariable Long id, @RequestBody Role role) {
        return R.ok(roleService.save(id, role));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }
}
