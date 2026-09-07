package com.wms.system.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {
    @Test
    void adminCanDoAnything() {
        assertTrue(AccessPolicy.allows("ADMIN", "DELETE", "/api/system/user/1"));
        assertTrue(AccessPolicy.allows("ADMIN", "POST", "/api/basic/item"));
    }

    @Test
    void viewerIsReadOnlyButMayChangeOwnPassword() {
        assertTrue(AccessPolicy.allows("VIEWER", "GET", "/api/inventory/page"));
        assertFalse(AccessPolicy.allows("VIEWER", "POST", "/api/inbound/asn"));
        assertTrue(AccessPolicy.allows("VIEWER", "POST", "/api/auth/password"));
    }

    @Test
    void operatorWorksButCannotMaintainMasterDataOrUsers() {
        assertTrue(AccessPolicy.allows("OPERATOR", "POST", "/api/inbound/asn"));
        assertTrue(AccessPolicy.allows("OPERATOR", "POST", "/api/outbound/wave"));
        assertTrue(AccessPolicy.allows("OPERATOR", "GET", "/api/basic/item/list"));
        assertFalse(AccessPolicy.allows("OPERATOR", "PUT", "/api/basic/item/1"));
        assertFalse(AccessPolicy.allows("OPERATOR", "POST", "/api/system/user"));
    }
}
