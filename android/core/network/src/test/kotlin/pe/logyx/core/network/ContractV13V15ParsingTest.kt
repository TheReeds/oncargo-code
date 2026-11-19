package pe.logyx.core.network

import kotlinx.serialization.builtins.ListSerializer
import pe.logyx.core.model.AuctionView
import pe.logyx.core.model.AuthResponse
import pe.logyx.core.model.BidStandingBand
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CloseReason
import pe.logyx.core.model.CounterOfferStatus
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.IncidentShipmentOutcome
import pe.logyx.core.model.IncidentView
import pe.logyx.core.model.NotificationTarget
import pe.logyx.core.model.NotificationType
import pe.logyx.core.model.NotificationView
import pe.logyx.core.model.OrgPermission
import pe.logyx.core.model.PublicOrganization
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.Role
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.SmartLoadView
import pe.logyx.core.model.UserInfo
import pe.logyx.core.model.VehicleType
import pe.logyx.core.model.VerificationStatus
import pe.logyx.core.model.canManageDrivers
import pe.logyx.core.model.canOperate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ejemplos de docs/api/CHANGELOG.md (V13 huecos P1, V14 cuentas y roles, V15 auditoría y lecturas) contra los DTOs. */
class ContractV13V15ParsingTest {
    private val u = "11111111-1111-1111-1111-111111111111"
    private inline fun <reified T> parse(json: String): T = LogyxJson.decodeFromString<T>(json)

    // --- V16 (zona D): avisos por persona y «Pedir acceso» ---
    @Test fun `NotificationView trae mine y los tipos nuevos de V16`() {
        val n = parse<NotificationView>(
            """{"id":"$u","type":"ACCESS_REQUESTED","title":"Pedido de acceso de Ana","body":"Ana pide permiso para operar.",
            "relatedType":"USER","relatedId":"$u","read":false,"createdAt":"2026-11-17T10:00:00Z","mine":true}""",
        )
        assertEquals(NotificationType.ACCESS_REQUESTED, n.type)
        assertEquals(NotificationTarget.USER, n.relatedType)
        assertTrue(n.mine)
        // Sin el campo (backend anterior a V16): no es «Tuyo»
        assertFalse(parse<NotificationView>("""{"id":"$u","type":"NEW_BID","title":"t","body":"b"}""").mine)
    }

    @Test fun `AccessRequestView y su cuerpo`() {
        val v = parse<pe.logyx.core.model.AccessRequestView>("""{"permission":"OPERATE","notifiedAdmins":2}""")
        assertEquals(OrgPermission.OPERATE, v.permission)
        assertEquals(2, v.notifiedAdmins)
        val body = LogyxJson.encodeToString(pe.logyx.core.model.CreateAccessRequest.serializer(),
            pe.logyx.core.model.CreateAccessRequest(OrgPermission.OPERATE, "Publico las cargas de Arequipa"))
        assertTrue(body.contains("\"permission\":\"OPERATE\"") && body.contains("Arequipa"))
    }

    // --- V14 ---
    @Test fun `UserInfo trae verificacion, RUC valido y permisos`() {
        val user = parse<UserInfo>(
            """{"id":"$u","email":"a@b.pe","fullName":"Ana","role":"ADMIN","organizationId":"$u","organizationName":"Andes SAC",
            "organizationType":"CARRIER","rucVerified":false,"rucValid":true,"verificationStatus":"REJECTED",
            "verificationNote":"El RUC no coincide con la razón social","permissions":["READ","OPERATE","MANAGE_USERS","MANAGE_ORGANIZATION","MANAGE_DRIVERS"]}""",
        )
        assertEquals(VerificationStatus.REJECTED, user.verificationStatus)
        assertEquals("El RUC no coincide con la razón social", user.verificationNote)
        assertTrue(user.rucValid)
        assertEquals(5, user.permissions.size)
        assertTrue(user.canOperate && user.canManageDrivers)
    }

    @Test fun `sesion guardada antes de V14 sigue parseando y deriva permisos del rol`() {
        val old = parse<UserInfo>("""{"id":"$u","email":"a@b.pe","fullName":"Ana","role":"VIEWER","organizationId":"$u","organizationType":"PYME","rucVerified":true}""")
        assertTrue(old.permissions.isEmpty())
        assertNull(old.verificationStatus)
        assertFalse(old.canOperate)
    }

    @Test fun `permisos y rol desconocidos no rompen el parseo`() {
        val user = parse<UserInfo>(
            """{"id":"$u","email":"a@b.pe","fullName":"Ana","role":"AUDITOR","organizationId":"$u","organizationType":"PYME",
            "verificationStatus":"ON_HOLD","permissions":["READ","EXPORT_REPORTS","OPERATE"]}""",
        )
        assertEquals(Role.UNKNOWN, user.role)
        assertNull(user.verificationStatus) // nullable con default: coerceInputValues -> null
        assertEquals(listOf(OrgPermission.READ, OrgPermission.OPERATE), user.permissions)
    }

    @Test fun `AuthResponse con refresh`() {
        val a = parse<AuthResponse>(
            """{"accessToken":"a","expiresIn":3600,"refreshToken":"r","refreshExpiresIn":2592000,"user":{"id":"$u","email":"a@b.pe","fullName":"Ana","role":"DRIVER","organizationId":"$u","organizationType":"CARRIER"}}""",
        )
        assertEquals("r", a.refreshToken)
        assertEquals(2_592_000L, a.refreshExpiresIn)
        val web = parse<AuthResponse>("""{"accessToken":"a","expiresIn":28800,"refreshToken":null,"user":{"id":"$u","email":"a@b.pe","fullName":"Ana","role":"ADMIN","organizationId":"$u","organizationType":"PYME"}}""")
        assertNull(web.refreshToken)
    }

    @Test fun `DriverView active por omision es true`() {
        assertTrue(parse<DriverView>("""{"id":"$u","fullName":"Luis","status":"AVAILABLE"}""").active)
        assertFalse(parse<DriverView>("""{"id":"$u","fullName":"Luis","status":"AVAILABLE","active":false}""").active)
    }

    @Test fun `PublicOrganization con verificacion`() {
        val o = parse<PublicOrganization>("""{"id":"$u","type":"CARRIER","legalName":"Andes","rucVerified":true,"rucValid":true,"verificationStatus":"VERIFIED"}""")
        assertEquals(VerificationStatus.VERIFIED, o.verificationStatus)
    }

    // --- V13 ---
    @Test fun `RequestView con campos de V13 y V15`() {
        val r = parse<RequestView>(
            """{"id":"$u","originCity":"LIMA","destinationCity":"JULIACA","cargoType":"GENERAL","weightKg":1500.0,"status":"CANCELLED",
            "compatibleVehicleTypes":["TRUCK_MEDIUM","TRUCK_LARGE","FUTURE_TYPE"],"cancelReason":"Ya no se necesita","republishedFrom":"$u",
            "bestBidAmount":1750.5,"bestBidCarrierName":"Andes","bestBidCarrierTrustScore":81.2,
            "myBidStanding":{"rank":2,"bidCount":5,"isBest":false,"vsBest":"WITHIN_5_PCT","finalStanding":false},
            "auction":{"id":"$u","status":"CLOSED","closesAt":"2026-11-17T10:00:00Z","maxBids":5,"bidCount":3,"closeReason":"TIMEOUT","closedAt":"2026-11-17T10:00:00Z"}}""",
        )
        assertEquals(RequestStatus.CANCELLED, r.status)
        assertEquals(listOf(VehicleType.TRUCK_MEDIUM, VehicleType.TRUCK_LARGE), r.compatibleVehicleTypes)
        assertEquals("Ya no se necesita", r.cancelReason)
        assertEquals(u, r.republishedFrom)
        assertEquals(1750.5, r.bestBidAmount)
        assertEquals("Andes", r.bestBidCarrierName)
        assertEquals(81.2, r.bestBidCarrierTrustScore)
        assertEquals(2, r.myBidStanding?.rank)
        assertEquals(BidStandingBand.WITHIN_5_PCT, r.myBidStanding?.vsBest)
        assertFalse(r.myBidStanding!!.isBest)
        assertEquals(CloseReason.TIMEOUT, r.auction?.closeReason)
        assertEquals("2026-11-17T10:00:00Z", r.auction?.closedAt)
    }

    @Test fun `estado final de la posicion sin banda`() {
        val r = parse<RequestView>(
            """{"id":"$u","originCity":"LIMA","destinationCity":"CUSCO","cargoType":"GENERAL","weightKg":1.0,"status":"MATCHED",
            "myBidStanding":{"rank":3,"bidCount":4,"isBest":false,"vsBest":null,"finalStanding":true}}""",
        )
        assertNull(r.myBidStanding?.vsBest)
        assertTrue(r.myBidStanding!!.finalStanding)
    }

    @Test fun `oferta retirada y contraoferta del transportista que vencio`() {
        assertEquals(BidStatus.WITHDRAWN, parse<BidView>("""{"id":"$u","requestId":"$u","amount":10.0,"status":"WITHDRAWN"}""").status)
        val co = parse<CounterOfferView>("""{"id":"$u","bidId":"$u","amount":1700.0,"status":"EXPIRED","proposedByCarrier":true}""")
        assertEquals(CounterOfferStatus.EXPIRED, co.status)
        assertTrue(co.proposedByCarrier)
        assertFalse(parse<CounterOfferView>("""{"id":"$u","bidId":"$u","amount":1700.0,"status":"PENDING"}""").proposedByCarrier)
    }

    @Test fun `ShipmentView cancelado con sus campos nuevos`() {
        val s = parse<ShipmentView>(
            """{"id":"$u","originCity":"LIMA","destinationCity":"PUNO","status":"CANCELLED","pickupDate":"2026-11-20","vehicleId":"$u",
            "loadGroupId":"$u","cancellationReason":"Cliente cerró","cancelledAt":"2026-11-18T09:00:00-05:00","cancelledByOrgId":"$u"}""",
        )
        assertEquals(ShipmentStatus.CANCELLED, s.status)
        assertEquals("2026-11-20", s.pickupDate)
        assertEquals(u, s.vehicleId)
        assertEquals(u, s.loadGroupId)
        assertEquals("Cliente cerró", s.cancellationReason)
        assertEquals(u, s.cancelledByOrgId)
    }

    @Test fun `estados desconocidos caen en UNKNOWN en vez de romper la lista`() {
        val list = LogyxJson.decodeFromString(
            ListSerializer(ShipmentView.serializer()),
            """[{"id":"$u","originCity":"LIMA","destinationCity":"PUNO","status":"ON_HOLD"},{"id":"$u","originCity":"LIMA","destinationCity":"PUNO","status":"DELIVERED"}]""",
        )
        assertEquals(ShipmentStatus.UNKNOWN, list[0].status)
        assertEquals(ShipmentStatus.DELIVERED, list[1].status)
        assertEquals(BidStatus.UNKNOWN, parse<BidView>("""{"id":"$u","requestId":"$u","amount":1.0,"status":"PAUSED"}""").status)
        assertEquals(CounterOfferStatus.UNKNOWN, parse<CounterOfferView>("""{"id":"$u","bidId":"$u","amount":1.0,"status":"SUPERSEDED"}""").status)
        val a = parse<AuctionView>("""{"id":"$u","status":"PAUSED","closesAt":"x","closeReason":"SOLD_OUT"}""")
        assertEquals(pe.logyx.core.model.AuctionStatus.UNKNOWN, a.status)
        assertNull(a.closeReason)
    }

    @Test fun `incidencia con el estado final del envio`() {
        val i = parse<IncidentView>("""{"id":"$u","shipmentId":"$u","category":"LOST_CARGO","description":"x","status":"RESOLVED","shipmentOutcome":"CANCELLED"}""")
        assertEquals(IncidentShipmentOutcome.CANCELLED, i.shipmentOutcome)
        assertNull(parse<IncidentView>("""{"id":"$u","shipmentId":"$u","category":"NEW_ONE","description":"x","status":"OPEN"}""").shipmentOutcome)
    }

    @Test fun `SmartLoadView con las ofertas creadas al aceptar`() {
        val s = parse<SmartLoadView>(
            """{"id":"$u","status":"ACCEPTED","bids":[{"requestId":"$u","bidId":"$u","amount":900.0},{"requestId":"$u","bidId":"$u","amount":1100.0}]}""",
        )
        assertEquals(2, s.bids.size)
        assertEquals(1100.0, s.bids[1].amount)
        assertTrue(parse<SmartLoadView>("""{"id":"$u","status":"PENDING"}""").bids.isEmpty())
    }

    @Test fun `tipos de notificacion nuevos y relatedType ORGANIZATION`() {
        val types = listOf("AUCTION_CLOSED", "REQUEST_CANCELLED", "SHIPMENT_CANCELLED", "BID_WITHDRAWN", "ORGANIZATION_VERIFICATION")
        types.forEach { t ->
            val n = parse<NotificationView>("""{"id":"$u","type":"$t","title":"x","body":"y","relatedType":"ORGANIZATION","relatedId":"$u"}""")
            assertEquals(NotificationType.valueOf(t), n.type)
            assertEquals(NotificationTarget.ORGANIZATION, n.relatedType)
        }
        val unknown = parse<NotificationView>("""{"id":"$u","type":"SOMETHING_NEW","title":"x","body":"y","relatedType":"GALAXY"}""")
        assertEquals(NotificationType.UNKNOWN, unknown.type)
        assertNull(unknown.relatedType)
    }
}
