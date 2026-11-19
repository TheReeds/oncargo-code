package pe.logyx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessTest {
    private fun user(role: Role, type: OrgType = OrgType.CARRIER, permissions: List<OrgPermission> = emptyList()) =
        UserInfo("u", "a@b.pe", "Ana", role, "o", "Org", type, permissions = permissions)

    @Test fun `usa los permisos del backend cuando vienen`() {
        val u = user(Role.OPS, permissions = listOf(OrgPermission.READ))
        assertFalse(u.canOperate)
        assertTrue(u.isReadOnly)
    }

    @Test fun `sin permisos deriva del rol (sesiones anteriores a V14)`() {
        assertTrue(user(Role.ADMIN).canOperate)
        assertTrue(user(Role.ADMIN).canManageDrivers)
        assertFalse(user(Role.ADMIN, OrgType.PYME).canManageDrivers)
        assertTrue(user(Role.OPS).canOperate)
        assertFalse(user(Role.OPS).canManageUsers)
        assertFalse(user(Role.VIEWER).canOperate)
        assertTrue(user(Role.VIEWER).isReadOnly)
    }

    @Test fun `permissionsOf da los permisos efectivos (LocalPermissions de la UI)`() {
        assertEquals(setOf(OrgPermission.READ), permissionsOf(user(Role.VIEWER, OrgType.PYME)))
        assertEquals(setOf(OrgPermission.READ, OrgPermission.OPERATE), permissionsOf(user(Role.OPS)))
        assertEquals(OrgPermission.entries.toSet(), permissionsOf(user(Role.ADMIN)))
        assertFalse(OrgPermission.MANAGE_DRIVERS in permissionsOf(user(Role.ADMIN, OrgType.PYME)))
        // Lo que manda el servidor gana sobre la tabla local
        assertEquals(setOf(OrgPermission.READ), permissionsOf(user(Role.ADMIN, permissions = listOf(OrgPermission.READ))))
    }

    @Test fun `el conductor no es lector aunque solo tenga READ`() {
        val d = user(Role.DRIVER, permissions = listOf(OrgPermission.READ))
        assertFalse(d.canOperate)
        assertFalse(d.isReadOnly)
    }

    @Test fun `un rol desconocido es el mas restrictivo`() {
        assertTrue(user(Role.UNKNOWN).isReadOnly)
    }

    @Test fun `AccessBlock se deduce del codigo`() {
        assertEquals(AccessBlock.NOT_VERIFIED, AccessBlock.fromCode("LOGYX-ORG-003"))
        assertEquals(AccessBlock.SUSPENDED, AccessBlock.fromCode("LOGYX-ORG-004"))
        assertEquals(AccessBlock.READ_ONLY, AccessBlock.fromCode("LOGYX-AUTH-007"))
        assertEquals(AccessBlock.ADMIN_ONLY, AccessBlock.fromCode("LOGYX-AUTH-008"))
        assertNull(AccessBlock.fromCode("LOGYX-BID-001"))
        assertNull(AccessBlock.fromCode(null))
    }
}
