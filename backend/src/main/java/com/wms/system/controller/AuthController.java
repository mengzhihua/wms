package com.wms.system.controller;

import com.wms.common.R;
import com.wms.system.auth.CurrentUser;
import com.wms.system.entity.User;
import com.wms.system.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @Data
    public static class LoginReq {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class ChangePwdReq {
        @NotBlank
        private String oldPassword;
        @NotBlank
        private String newPassword;
    }

    @PostMapping("/login")
    public R<UserService.LoginResult> login(@Valid @RequestBody LoginReq req) {
        return R.ok(userService.login(req.getUsername().trim(), req.getPassword()));
    }

    @GetMapping("/me")
    public R<User> me() {
        return R.ok(CurrentUser.get());
    }

    @PostMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePwdReq req) {
        userService.changePassword(CurrentUser.get().getId(), req.getOldPassword(), req.getNewPassword());
        return R.ok();
    }

    /** 令牌无状态，登出由前端丢弃令牌即可；保留端点便于审计扩展 */
    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }
}
