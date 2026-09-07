package com.wms.system.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    private final TokenService svc = new TokenService("secret-a", Duration.ofHours(1));

    @Test
    void roundTrip() {
        String token = svc.issue(7L, "admin");
        TokenService.Principal p = svc.parse(token);
        assertNotNull(p);
        assertEquals(7L, p.getUserId());
        assertEquals("admin", p.getUsername());
        assertTrue(p.getExpiresAt() > System.currentTimeMillis());
    }

    @Test
    void expiredTokenRejected() {
        String token = svc.issue(7L, "admin", System.currentTimeMillis() - 1);
        assertNull(svc.parse(token));
    }

    @Test
    void tamperedOrForeignTokenRejected() {
        String token = svc.issue(7L, "admin");
        assertNull(svc.parse(token.substring(0, token.length() - 2) + "xx"));
        assertNull(svc.parse("garbage"));
        assertNull(svc.parse(null));
        TokenService other = new TokenService("secret-b", Duration.ofHours(1));
        assertNull(other.parse(token));
    }
}
