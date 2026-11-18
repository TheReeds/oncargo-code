package pe.logyx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRoutingTest {
    private fun n(type: NotificationType, target: NotificationTarget?, id: String? = "x") =
        NotificationView("n", type, "t", "b", relatedType = target, relatedId = id)

    @Test fun `verificacion de la organizacion lleva a Cuenta`() {
        assertEquals(NotificationDestination.Account, n(NotificationType.ORGANIZATION_VERIFICATION, NotificationTarget.ORGANIZATION).destination())
        // aunque no traiga relatedId
        assertEquals(NotificationDestination.Account, n(NotificationType.ORGANIZATION_VERIFICATION, NotificationTarget.ORGANIZATION, null).destination())
    }

    @Test fun `tipos nuevos de V13 llegan al recurso relacionado`() {
        assertEquals(NotificationDestination.Request("x"), n(NotificationType.AUCTION_CLOSED, NotificationTarget.REQUEST).destination())
        assertEquals(NotificationDestination.Request("x"), n(NotificationType.REQUEST_CANCELLED, NotificationTarget.REQUEST).destination())
        assertEquals(NotificationDestination.Shipment("x"), n(NotificationType.SHIPMENT_CANCELLED, NotificationTarget.SHIPMENT).destination())
        assertEquals(NotificationDestination.Request("x"), n(NotificationType.BID_WITHDRAWN, NotificationTarget.REQUEST).destination())
    }

    @Test fun `mensajes nuevos abren la conversacion`() {
        assertEquals(NotificationDestination.Conversation("x"), n(NotificationType.NEW_MESSAGE, NotificationTarget.REQUEST).destination())
    }

    @Test fun `viajes smart load incidencias y reseñas`() {
        assertEquals(NotificationDestination.Trip("x"), n(NotificationType.TRIP_BOOKING_REQUESTED, NotificationTarget.TRIP).destination())
        assertEquals(NotificationDestination.SmartLoads, n(NotificationType.SMART_LOAD_SUGGESTED, NotificationTarget.SMART_LOAD, null).destination())
        assertEquals(NotificationDestination.Incident("x"), n(NotificationType.INCIDENT_REPORTED, NotificationTarget.INCIDENT).destination())
        assertEquals(NotificationDestination.OwnProfile, n(NotificationType.NEW_REVIEW, NotificationTarget.REVIEW).destination())
        assertEquals(NotificationDestination.BidsOrRequests, n(NotificationType.COUNTER_OFFER, NotificationTarget.COUNTER_OFFER).destination())
    }

    @Test fun `sin destino conocido no navega`() {
        assertNull(n(NotificationType.UNKNOWN, null).destination())
        assertNull(n(NotificationType.NEW_BID, NotificationTarget.REQUEST, null).destination())
    }
}
