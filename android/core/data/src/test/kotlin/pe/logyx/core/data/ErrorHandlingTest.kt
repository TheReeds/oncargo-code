package pe.logyx.core.data

import pe.logyx.core.model.AccessBlock
import pe.logyx.core.network.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Manejador común de ORG-003/004 y AUTH-007/008, y errores de campo reutilizables (RN07 `NEG-001`). */
class ErrorHandlingTest {
    private fun api(code: String, message: String = "mensaje del backend", field: String? = null, status: Int = 403) =
        ApiException(status, code, message, field)

    @Test fun `ORG-003 y ORG-004 se clasifican y usan el texto de OnCargo`() {
        val unverified = api("LOGYX-ORG-003", "Tu organización está pendiente de verificación por LOGYX (RN01)")
        assertEquals(AccessBlock.NOT_VERIFIED, unverified.accessBlock)
        assertTrue(unverified.isAccessBlocked)
        assertEquals(AccessBlock.NOT_VERIFIED.defaultMessage, unverified.userMessage())
        assertTrue("OnCargo" in unverified.userMessage() && "LOGYX" !in unverified.userMessage())

        val suspended = api("LOGYX-ORG-004")
        assertEquals(AccessBlock.SUSPENDED, suspended.accessBlock)
        assertEquals(AccessBlock.SUSPENDED.defaultMessage, suspended.userMessage())
    }

    @Test fun `AUTH-007 y AUTH-008 conservan el mensaje del backend`() {
        val viewer = api("LOGYX-AUTH-007", "Tu rol Lector solo permite consultar. Pide a un administrador…")
        assertEquals(AccessBlock.READ_ONLY, viewer.accessBlock)
        assertEquals("Tu rol Lector solo permite consultar. Pide a un administrador…", viewer.userMessage())
        assertEquals(AccessBlock.ADMIN_ONLY, api("LOGYX-AUTH-008").accessBlock)
    }

    @Test fun `otros errores no son bloqueos`() {
        assertNull(api("LOGYX-BID-001", status = 409).accessBlock)
        assertFalse(IllegalStateException("x").isAccessBlocked)
        assertEquals("Ocurrió un error inesperado. Inténtalo de nuevo.", IllegalStateException("x").userMessage())
    }

    @Test fun `NEG-001 con field llega como error de campo solo si el formulario lo muestra`() {
        val e = api("LOGYX-NEG-001", "El texto contiene datos de contacto", field = "notes", status = 400)
        assertTrue(e.isContactInfoRejected)
        val fe = e.fieldErrors("notes", "amount")
        assertEquals("El texto contiene datos de contacto", fe["notes"])
        assertNull(fe["amount"])
        // un campo que el formulario no muestra: se trata como mensaje general
        assertTrue(e.fieldErrors("amount").isEmpty)
        assertTrue(api("LOGYX-AUTH-001", status = 401).fieldErrors("notes").isEmpty)
    }

    @Test fun `FieldErrors se limpia por campo al editar`() {
        val fe = FieldErrors().with("notes", "x").with("amount", "y")
        assertEquals(FieldErrors(mapOf("amount" to "y")), fe.without("notes"))
        assertTrue(fe.without("notes").without("amount").isEmpty)
        assertEquals("notes", api("LOGYX-NEG-001", field = "notes").errorField)
        assertNull(api("LOGYX-NEG-001", field = " ").errorField)
    }
}
