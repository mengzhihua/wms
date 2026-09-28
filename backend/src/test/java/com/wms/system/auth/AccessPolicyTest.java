package com.wms.system.auth;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {
    private static final Set<String> ADMIN = Collections.singleton(Permission.ALL);
    private static final Set<String> VIEWER = Collections.emptySet();
    private static final Set<String> OPERATOR = new HashSet<>(Permission.OPERATOR_DEFAULTS);

    @Test
    void adminCanDoAnything() {
        assertTrue(AccessPolicy.allows(ADMIN, "DELETE", "/api/system/user/1"));
        assertTrue(AccessPolicy.allows(ADMIN, "POST", "/api/basic/item"));
        assertTrue(AccessPolicy.allows(ADMIN, "POST", "/api/inbound/asn/1/approve"));
    }

    @Test
    void viewerIsReadOnlyButMayChangeOwnPassword() {
        assertTrue(AccessPolicy.allows(VIEWER, "GET", "/api/inventory/page"));
        assertFalse(AccessPolicy.allows(VIEWER, "POST", "/api/inbound/asn"));
        assertTrue(AccessPolicy.allows(VIEWER, "POST", "/api/auth/password"));
        assertFalse(AccessPolicy.allows(null, "POST", "/api/inbound/asn"));
    }

    @Test
    void operatorWorksButCannotMaintainMasterDataOrUsersOrApprove() {
        assertTrue(AccessPolicy.allows(OPERATOR, "POST", "/api/inbound/asn"));
        assertTrue(AccessPolicy.allows(OPERATOR, "POST", "/api/outbound/wave"));
        assertTrue(AccessPolicy.allows(OPERATOR, "GET", "/api/basic/item/list"));
        assertFalse(AccessPolicy.allows(OPERATOR, "PUT", "/api/basic/item/1"));
        assertFalse(AccessPolicy.allows(OPERATOR, "POST", "/api/system/user"));
        assertFalse(AccessPolicy.allows(OPERATOR, "POST", "/api/system/role"));
        assertTrue(AccessPolicy.allows(OPERATOR, "POST", "/api/outbound/wave-strategy/run"));
        assertFalse(AccessPolicy.allows(OPERATOR, "POST", "/api/outbound/wave-strategy"));
        assertFalse(AccessPolicy.allows(OPERATOR, "POST", "/api/outbound/wave-strategy/1/toggle"));
        assertFalse(AccessPolicy.allows(OPERATOR, "POST", "/api/inbound/asn/1/approve"));
        assertFalse(AccessPolicy.allows(OPERATOR, "POST", "/api/outbound/order/1/reject"));
    }

    @Test
    void customApproverRoleOnlyApproves() {
        Set<String> approver = new HashSet<>();
        approver.add(Permission.INBOUND_APPROVE);
        approver.add(Permission.OUTBOUND_APPROVE);
        assertTrue(AccessPolicy.allows(approver, "POST", "/api/inbound/asn/1/approve"));
        assertTrue(AccessPolicy.allows(approver, "POST", "/api/outbound/order/1/reject"));
        assertFalse(AccessPolicy.allows(approver, "POST", "/api/inbound/asn"));
        assertFalse(AccessPolicy.allows(approver, "POST", "/api/inventory/move"));
        assertEquals(Permission.INBOUND_APPROVE, AccessPolicy.requiredPermission("POST", "/api/inbound/asn/1/approve"));
        assertEquals(Permission.ALL, AccessPolicy.requiredPermission("POST", "/api/unknown/x"));
    }
}
