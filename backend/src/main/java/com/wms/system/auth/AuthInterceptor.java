package com.wms.system.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.R;
import com.wms.system.entity.User;
import com.wms.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 校验 Authorization: Bearer 令牌，并按 {@link AccessPolicy} 做角色鉴权 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    public static final String LOGIN_PATH = "/api/auth/login";

    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod()) || LOGIN_PATH.equals(req.getRequestURI())) {
            return true;
        }
        TokenService.Principal principal = tokenService.parse(bearer(req.getHeader("Authorization")));
        if (principal == null) {
            return reject(res, HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        User user = userMapper.selectById(principal.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return reject(res, HttpStatus.UNAUTHORIZED, "账号不存在或已停用");
        }
        if (!AccessPolicy.allows(user.getRole(), req.getMethod(), req.getRequestURI())) {
            return reject(res, HttpStatus.FORBIDDEN, "当前角色无权执行此操作");
        }
        CurrentUser.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        CurrentUser.clear();
    }

    private static String bearer(String header) {
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    private boolean reject(HttpServletResponse res, HttpStatus status, String msg) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(R.fail(status.value(), msg)));
        return false;
    }
}
