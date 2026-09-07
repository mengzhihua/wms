package com.wms.system.auth;

import com.wms.system.entity.User;

/** 当前请求线程绑定的登录用户 */
public final class CurrentUser {
    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static User get() {
        return HOLDER.get();
    }

    static void set(User user) {
        HOLDER.set(user);
    }

    static void clear() {
        HOLDER.remove();
    }
}
