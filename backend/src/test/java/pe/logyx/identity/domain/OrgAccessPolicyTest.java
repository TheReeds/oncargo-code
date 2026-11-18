package pe.logyx.identity.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrgAccessPolicyTest {

    @Test
    void viewerSoloEscribeEnLaListaDeAutoservicio() {
        assertTrue(OrgAccessPolicy.viewerMayWrite("GET", "/api/v1/shipment-requests"));
        assertTrue(OrgAccessPolicy.viewerMayWrite("POST", "/api/v1/auth/change-password"));
        assertTrue(OrgAccessPolicy.viewerMayWrite("POST", "/api/v1/auth/logout-all"));
        assertTrue(OrgAccessPolicy.viewerMayWrite("POST", "/api/v1/notifications/" + UUID.randomUUID() + "/read"));
        assertTrue(OrgAccessPolicy.viewerMayWrite("POST", "/api/v1/notifications/read-all/"));
        assertTrue(OrgAccessPolicy.viewerMayWrite("POST", "api/v1/pricing/calculate"));
        assertFalse(OrgAccessPolicy.viewerMayWrite("POST", "/api/v1/shipment-requests"));
        assertFalse(OrgAccessPolicy.viewerMayWrite("PUT", "/api/v1/shipments/x/driver"));
        assertFalse(OrgAccessPolicy.viewerMayWrite("DELETE", "/api/v1/notifications/x"));
        assertFalse(OrgAccessPolicy.viewerMayWrite("POST", "/api/v1/notifications/x/read/extra"));
        assertFalse(OrgAccessPolicy.viewerMayWrite("PATCH", "/api/v1/organizations/me"));
        assertFalse(OrgAccessPolicy.viewerMayWrite("POST", "/api/v1/nuevo-endpoint"));
    }

    @Test
    void permisosPorRol() {
        assertTrue(OrgAccessPolicy.permissions(Profile.Role.ADMIN, OrgType.CARRIER).contains(OrgPermission.MANAGE_DRIVERS));
        assertFalse(OrgAccessPolicy.permissions(Profile.Role.ADMIN, OrgType.PYME).contains(OrgPermission.MANAGE_DRIVERS));
        assertFalse(OrgAccessPolicy.permissions(Profile.Role.OPS, OrgType.CARRIER).contains(OrgPermission.MANAGE_USERS));
        assertEquals(1, OrgAccessPolicy.permissions(Profile.Role.VIEWER, OrgType.PYME).size());
    }

    @Test
    void ultimoAdminActivo() {
        UUID org = UUID.randomUUID();
        Profile admin = Profile.create(org, "a@x.pe", "h", "A", Profile.Role.ADMIN, OffsetDateTime.now());
        Profile ops = Profile.create(org, "o@x.pe", "h", "O", Profile.Role.OPS, OffsetDateTime.now());
        Profile invited = Profile.invite(org, "i@x.pe", "h", "I", Profile.Role.ADMIN, admin.id(), OffsetDateTime.now());
        assertTrue(OrgAccessPolicy.leavesNoAdmin(admin, 1));
        assertFalse(OrgAccessPolicy.leavesNoAdmin(admin, 2));
        assertFalse(OrgAccessPolicy.leavesNoAdmin(ops, 1));
        assertFalse(OrgAccessPolicy.leavesNoAdmin(invited, 1));
    }

    @Test
    void cambiosDeCuentaSubenLaVersionDeSesion() {
        Profile p = Profile.create(UUID.randomUUID(), "a@x.pe", "h", "A", Profile.Role.OPS, OffsetDateTime.now());
        p.changeRole(Profile.Role.OPS);
        assertEquals(0, p.sessionVersion());
        p.changeRole(Profile.Role.VIEWER);
        p.changePassword("h2", OffsetDateTime.now());
        p.deactivate();
        p.deactivate();
        assertEquals(3, p.sessionVersion());
        assertFalse(p.canLogIn());
    }

    @Test
    void tokensOpacos() {
        String t = OpaqueTokens.generate();
        assertTrue(OpaqueTokens.wellFormed(t));
        assertNotEquals(t, OpaqueTokens.generate());
        assertEquals(64, OpaqueTokens.hash(t).length());
        assertEquals(OpaqueTokens.hash(t), OpaqueTokens.hash(t));
        assertFalse(OpaqueTokens.wellFormed("abc"));
        assertFalse(OpaqueTokens.wellFormed(t.substring(1) + "!"));
    }
}
