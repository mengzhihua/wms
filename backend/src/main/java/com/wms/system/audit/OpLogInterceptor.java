package com.wms.system.audit;

import com.wms.system.auth.CurrentUser;
import com.wms.system.entity.OpLog;
import com.wms.system.entity.User;
import com.wms.system.mapper.OpLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

/** 记录所有非 GET 的 /api 调用（登录接口除外） */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpLogInterceptor implements HandlerInterceptor {
    private static final String START = OpLogInterceptor.class.getName() + ".start";
    private final OpLogMapper mapper;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        req.setAttribute(START, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        String method = req.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return;
        }
        try {
            OpLog l = new OpLog();
            User u = CurrentUser.get();
            l.setUsername(u != null ? u.getUsername() : null);
            l.setMethod(method);
            l.setPath(trim(req.getRequestURI(), 255));
            l.setQuery(trim(req.getQueryString(), 255));
            l.setHttpStatus(res.getStatus());
            Object start = req.getAttribute(START);
            l.setCostMs(start instanceof Long ? (int) (System.currentTimeMillis() - (Long) start) : null);
            l.setClientIp(trim(req.getRemoteAddr(), 64));
            l.setCreatedAt(LocalDateTime.now());
            mapper.insert(l);
        } catch (RuntimeException e) {
            log.warn("写操作日志失败: {}", e.getMessage());
        }
    }

    private static String trim(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
