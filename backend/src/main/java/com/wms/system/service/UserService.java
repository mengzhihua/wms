package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.BizException;
import com.wms.system.auth.PasswordHasher;
import com.wms.system.auth.Permission;
import com.wms.system.entity.Role;
import com.wms.system.mapper.RoleMapper;
import com.wms.system.auth.TokenService;
import com.wms.system.entity.User;
import com.wms.system.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements ApplicationRunner {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final TokenService tokenService;

    @Value("${wms.auth.admin-password:admin123}")
    private String initialAdminPassword;

    /** 首次启动无任何用户时创建 admin 账号（口令来自 wms.auth.admin-password / WMS_ADMIN_PASSWORD） */
    @Override
    public void run(ApplicationArguments args) {
        seedRole(User.ADMIN, "管理员", Permission.ALL);
        seedRole(User.OPERATOR, "作业员", String.join(",", Permission.OPERATOR_DEFAULTS));
        seedRole(User.VIEWER, "只读", "");
        roleService.evict();
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(PasswordHasher.hash(initialAdminPassword));
        admin.setRealName("系统管理员");
        admin.setRole(User.ADMIN);
        admin.setStatus(1);
        userMapper.insert(admin);
        log.info("已初始化管理员账号 admin");
    }

    /** 内置角色不存在时创建(旧库升级 / 首次启动); 已存在则保留管理员的自定义权限 */
    private void seedRole(String code, String name, String perms) {
        if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, code)) > 0) {
            return;
        }
        Role r = new Role();
        r.setCode(code);
        r.setName(name);
        r.setPerms(perms);
        r.setBuiltin(true);
        r.setStatus(1);
        roleMapper.insert(r);
    }

    @Data
    public static class LoginResult {
        private String token;
        private User user;
        /** 角色权限码, "*" 表示全部 */
        private Set<String> perms;
    }

    @Transactional
    public LoginResult login(String username, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !PasswordHasher.verify(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        LoginResult r = new LoginResult();
        r.setToken(tokenService.issue(user.getId(), user.getUsername()));
        r.setUser(user);
        r.setPerms(roleService.permsOf(user.getRole()));
        return r;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null || !PasswordHasher.verify(oldPassword, user.getPassword())) {
            throw new BizException("原密码错误");
        }
        User upd = new User();
        upd.setId(userId);
        upd.setPassword(PasswordHasher.hash(requireStrong(newPassword)));
        userMapper.updateById(upd);
    }

    /** 管理员维护：新增必须带密码，修改时密码为空表示不改 */
    public void prepareForSave(User entity, boolean creating) {
        if (entity.getUsername() == null || entity.getUsername().trim().isEmpty()) {
            throw new BizException("用户名不能为空");
        }
        if (!roleService.exists(entity.getRole())) {
            throw new BizException("角色不存在: " + entity.getRole());
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setLastLoginAt(null);
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            entity.setPassword(PasswordHasher.hash(requireStrong(entity.getPassword())));
        } else if (creating) {
            throw new BizException("新建用户必须设置密码");
        }
    }

    public void ensureNotLastAdmin(Long userId, User incoming) {
        User existing = userMapper.selectById(userId);
        if (existing == null || !User.ADMIN.equals(existing.getRole())) {
            return;
        }
        boolean demoted = incoming == null
                || !User.ADMIN.equals(incoming.getRole())
                || (incoming.getStatus() != null && incoming.getStatus() != 1);
        if (!demoted) {
            return;
        }
        Long admins = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, User.ADMIN).eq(User::getStatus, 1).ne(User::getId, userId));
        if (admins == 0) {
            throw new BizException("至少保留一个启用的管理员账号");
        }
    }

    private static String requireStrong(String pwd) {
        if (pwd == null || pwd.length() < 6) {
            throw new BizException("密码长度至少 6 位");
        }
        return pwd;
    }
}
