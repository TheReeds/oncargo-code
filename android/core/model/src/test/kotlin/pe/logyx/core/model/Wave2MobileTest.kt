package pe.logyx.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Avisos de las olas 1–2 que antes caían en UNKNOWN, «registrado con retraso» y preferencias de aviso. */
class Wave2MobileTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private fun n(type: NotificationType, target: NotificationTarget?, id: String? = "x") =
        NotificationView("n", type, "t", "b", relatedType = target, relatedId = id)

    @Test fun `ajustes e incidencia asignada abren su recurso`() {
        assertEquals(NotificationDestination.Shipment("x"), n(NotificationType.ADJUSTMENT_PROPOSED, NotificationTarget.SHIPMENT).destination())
        assertEquals(NotificationDestination.Shipment("x"), n(NotificationType.ADJUSTMENT_DECIDED, NotificationTarget.SHIPMENT).destination())
        assertEquals(NotificationDestination.Shipment("x"), n(NotificationType.ADJUSTMENT_ESCALATED, NotificationTarget.SHIPMENT).destination())
        assertEquals(NotificationDestination.Incident("x"), n(NotificationType.INCIDENT_ASSIGNED, NotificationTarget.INCIDENT).destination())
    }

    @Test fun `segunda oferta invitacion y plantillas`() {
        assertEquals(NotificationDestination.BackupConfirmations, n(NotificationType.BACKUP_REQUESTED, NotificationTarget.BID).destination())
        assertEquals(NotificationDestination.Request("x"), n(NotificationType.BACKUP_RESPONDED, NotificationTarget.REQUEST).destination())
        assertEquals(NotificationDestination.Request("x"), n(NotificationType.REQUEST_INVITATION, NotificationTarget.REQUEST).destination())
        // Plantilla publicada → la solicitud; fallida → Plantillas.
        assertEquals(NotificationDestination.Request("x"), n(NotificationType.TEMPLATE_RUN, NotificationTarget.REQUEST).destination())
        assertEquals(NotificationDestination.Templates, n(NotificationType.TEMPLATE_RUN, NotificationTarget.TEMPLATE).destination())
    }

    @Test fun `los tipos nuevos ya no caen en UNKNOWN`() {
        val raw = """{"id":"n","type":"ADJUSTMENT_PROPOSED","title":"t","body":"b","relatedType":"TEMPLATE"}"""
        val v = json.decodeFromString(NotificationView.serializer(), raw)
        assertEquals(NotificationType.ADJUSTMENT_PROPOSED, v.type)
        assertEquals(NotificationTarget.TEMPLATE, v.relatedType)
        val future = json.decodeFromString(NotificationView.serializer(), """{"id":"n","type":"ALGO_NUEVO","title":"t","body":"b"}""")
        assertEquals(NotificationType.UNKNOWN, future.type)
    }

    @Test fun `retraso de mas de 10 minutos entre la hora del conductor y la del servidor`() {
        assertTrue(lateSync("2026-11-19T08:00:00-05:00", "2026-11-19T08:10:01-05:00"))
        assertFalse(lateSync("2026-11-19T08:00:00-05:00", "2026-11-19T08:10:00-05:00"))
        assertFalse(lateSync(null, "2026-11-19T08:30:00-05:00"))
        assertFalse(lateSync("no es fecha", "2026-11-19T08:30:00-05:00"))
        // Distinto offset, mismo instante: no es retraso.
        assertFalse(lateSync("2026-11-19T13:00:00Z", "2026-11-19T08:05:00-05:00"))
    }

    @Test fun `la parada usa la llegada o el cierre segun su estado`() {
        val base = StopView(id = "s", sequence = 1, type = StopType.PICKUP, city = "LIMA", status = StopStatus.ARRIVED,
            arrivedAt = "2026-11-19T08:00:00-05:00", arrivedRecordedAt = "2026-11-19T09:00:00-05:00")
        assertTrue(base.registeredLate)
        val closed = base.copy(status = StopStatus.COMPLETED, completedAt = "2026-11-19T09:10:00-05:00", completedRecordedAt = "2026-11-19T09:11:00-05:00")
        assertFalse(closed.registeredLate)
    }

    @Test fun `preferencias toleran categorias desconocidas y el cambio respeta el bloqueo in-app`() {
        val raw = """[{"category":"ALERTS","label":"Alertas de carga","inApp":true,"email":false},
            {"category":"NUEVA","label":"X","inApp":true,"email":true},
            {"category":"ACCOUNT","label":"Cuenta","inApp":false,"email":true,"inAppLocked":true}]"""
        val list = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(NotificationPreferenceView.serializer()), raw)
        assertEquals(NotificationCategory.UNKNOWN, list[1].category)
        assertNull(list[1].toChange())
        assertEquals(NotificationPreferenceChange(NotificationCategory.ACCOUNT, inApp = true, email = true), list[2].toChange())
        assertEquals("""{"preferences":[{"category":"ALERTS","inApp":true,"email":false}]}""",
            Json.encodeToString(UpdateNotificationPreferences.serializer(), UpdateNotificationPreferences(listOfNotNull(list[0].toChange()))))
    }
}
