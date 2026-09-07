package com.wms.system.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 无状态 HMAC 访问令牌：{@code base64url(userId:username:expiresAtMillis).base64url(hmacSha256)}。
 * 密钥来自 wms.auth.secret（环境变量 WMS_AUTH_SECRET）；未配置时每次启动随机生成，重启后旧令牌全部失效。
 */
@Slf4j
@Component
public class TokenService {
    private final byte[] key;
    private final long ttlMillis;

    public TokenService(@Value("${wms.auth.secret:}") String secret,
                        @Value("${wms.auth.token-ttl:12h}") Duration ttl) {
        if (secret == null || secret.trim().isEmpty()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.key = random;
            log.warn("wms.auth.secret 未配置，使用随机密钥，应用重启后需要重新登录");
        } else {
            this.key = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.ttlMillis = ttl.toMillis();
    }

    public String issue(Long userId, String username) {
        return issue(userId, username, System.currentTimeMillis() + ttlMillis);
    }

    String issue(Long userId, String username, long expiresAt) {
        String payload = userId + ":" + username + ":" + expiresAt;
        String body = b64(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + b64(sign(body));
    }

    /** @return 解析出的主体，令牌无效或过期返回 null */
    public Principal parse(String token) {
        if (token == null) {
            return null;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String body = token.substring(0, dot);
        byte[] sig;
        try {
            sig = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!MessageDigest.isEqual(sig, sign(body))) {
            return null;
        }
        String[] parts = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8).split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        long exp = Long.parseLong(parts[2]);
        if (exp < System.currentTimeMillis()) {
            return null;
        }
        return new Principal(Long.parseLong(parts[0]), parts[1], exp);
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static final class Principal {
        private final Long userId;
        private final String username;
        private final long expiresAt;

        Principal(Long userId, String username, long expiresAt) {
            this.userId = userId;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
