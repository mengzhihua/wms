package com.wms.system.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test
    void hashIsSaltedAndVerifiable() {
        String h1 = PasswordHasher.hash("admin123");
        String h2 = PasswordHasher.hash("admin123");
        assertNotEquals(h1, h2);
        assertTrue(h1.startsWith("pbkdf2$"));
        assertTrue(PasswordHasher.verify("admin123", h1));
        assertTrue(PasswordHasher.verify("admin123", h2));
        assertFalse(PasswordHasher.verify("admin124", h1));
    }

    @Test
    void rejectsMalformedOrNull() {
        assertFalse(PasswordHasher.verify("x", null));
        assertFalse(PasswordHasher.verify(null, "pbkdf2$1$a$b"));
        assertFalse(PasswordHasher.verify("x", "plaintext"));
    }
}
