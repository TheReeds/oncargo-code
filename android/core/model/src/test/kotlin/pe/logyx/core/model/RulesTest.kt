package pe.logyx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private fun request(
        cargo: CargoType = CargoType.GENERAL,
        kg: Double = 2000.0,
        m3: Double? = null,
        compatible: List<VehicleType> = emptyList(),
        status: RequestStatus = RequestStatus.PUBLISHED,
        auction: AuctionView? = AuctionView("a", AuctionStatus.OPEN, "2099-01-01T00:00:00Z"),
        bid: BidView? = null,
    ) = RequestView(
        id = "r", originCity = "LIMA", destinationCity = "JULIACA", cargoType = cargo, weightKg = kg, volumeM3 = m3,
        compatibleVehicleTypes = compatible, status = status, auction = auction, myBid = bid,
    )

    private fun vehicle(type: VehicleType, kg: Double = 10_000.0, m3: Double? = null, status: VehicleStatus = VehicleStatus.AVAILABLE) =
        VehicleView("v-${type.name}", "ABC-123", type, kg, m3, status)

    @Test fun `matriz RN02 igual a la del backend`() {
        assertEquals(
            listOf(VehicleType.VAN, VehicleType.TRUCK_SMALL, VehicleType.TRUCK_MEDIUM, VehicleType.TRUCK_LARGE, VehicleType.PLATFORM),
            CargoType.GENERAL.compatibleVehicleTypes,
        )
        assertEquals(listOf(VehicleType.REFRIGERATED), CargoType.PERISHABLE.compatibleVehicleTypes)
        assertEquals(listOf(VehicleType.PLATFORM), CargoType.OVERSIZED.compatibleVehicleTypes)
        // Un refrigerado solo lleva perecibles; la cisterna lleva granel y peligrosa pero no carga general.
        assertTrue(VehicleType.REFRIGERATED !in CargoType.GENERAL.compatibleVehicleTypes)
        assertTrue(VehicleType.TANKER in CargoType.BULK.compatibleVehicleTypes && VehicleType.TANKER in CargoType.HAZARDOUS.compatibleVehicleTypes)
        assertTrue(VehicleType.TANKER !in CargoType.GENERAL.compatibleVehicleTypes)
    }

    @Test fun `F09 un camion pequeno no lleva carga perecible aunque alcancen los kg`() {
        val r = request(cargo = CargoType.PERISHABLE, kg = 1000.0, m3 = 60.0)
        assertEquals(VehicleFit.TYPE, vehicle(VehicleType.TRUCK_SMALL).fitFor(r))
        assertEquals(VehicleFit.FITS, vehicle(VehicleType.REFRIGERATED).fitFor(r))
    }

    @Test fun `el orden de motivos es disponibilidad kg tipo volumen`() {
        val r = request(cargo = CargoType.PERISHABLE, kg = 5000.0, m3 = 30.0)
        assertEquals(VehicleFit.UNAVAILABLE, vehicle(VehicleType.VAN, kg = 100.0, status = VehicleStatus.IN_ROUTE).fitFor(r))
        assertEquals(VehicleFit.WEIGHT, vehicle(VehicleType.VAN, kg = 100.0).fitFor(r))
        assertEquals(VehicleFit.TYPE, vehicle(VehicleType.TRUCK_LARGE, kg = 9000.0).fitFor(r))
        assertEquals(VehicleFit.VOLUME, vehicle(VehicleType.REFRIGERATED, kg = 9000.0, m3 = 20.0).fitFor(r))
        assertEquals(VehicleFit.FITS, vehicle(VehicleType.REFRIGERATED, kg = 9000.0, m3 = 40.0).fitFor(r))
        // Sin volumen declarado en alguno de los dos lados no se rechaza por m3.
        assertEquals(VehicleFit.FITS, vehicle(VehicleType.REFRIGERATED, kg = 9000.0, m3 = null).fitFor(r))
    }

    @Test fun `usa la lista del servidor y cae a la matriz local si viene vacia`() {
        val server = request(cargo = CargoType.GENERAL, compatible = listOf(VehicleType.TRUCK_LARGE))
        assertEquals(VehicleFit.TYPE, vehicle(VehicleType.VAN).fitFor(server))
        assertEquals(VehicleFit.FITS, vehicle(VehicleType.TRUCK_LARGE).fitFor(server))
        val legacy = request(cargo = CargoType.GENERAL, compatible = emptyList())
        assertEquals(VehicleFit.FITS, vehicle(VehicleType.VAN).fitFor(legacy))
    }

    @Test fun `F35 el chip compatible solo si algun vehiculo apto`() {
        val r = request(cargo = CargoType.PERISHABLE)
        assertFalse(r.isCompatibleWith(listOf(vehicle(VehicleType.TRUCK_LARGE), vehicle(VehicleType.VAN))))
        assertTrue(r.isCompatibleWith(listOf(vehicle(VehicleType.TRUCK_LARGE), vehicle(VehicleType.REFRIGERATED))))
        assertFalse(r.isCompatibleWith(emptyList()))
    }

    private fun bid(status: BidStatus) = BidView("b", "r", amount = 1000.0, status = status)

    @Test fun `resultado de mi oferta`() {
        assertNull(request().myBidOutcome)
        assertEquals(BidOutcome.OPEN, request(bid = bid(BidStatus.PENDING)).myBidOutcome)
        assertEquals(
            BidOutcome.AWAITING_DECISION,
            request(bid = bid(BidStatus.PENDING), auction = AuctionView("a", AuctionStatus.CLOSED, "2026-01-01T00:00:00Z")).myBidOutcome,
        )
        assertEquals(BidOutcome.WON, request(status = RequestStatus.MATCHED, bid = bid(BidStatus.ACCEPTED)).myBidOutcome)
        assertEquals(BidOutcome.LOST, request(status = RequestStatus.MATCHED, bid = bid(BidStatus.REJECTED)).myBidOutcome)
        assertEquals(BidOutcome.REQUEST_CANCELLED, request(status = RequestStatus.CANCELLED, bid = bid(BidStatus.REJECTED)).myBidOutcome)
        assertEquals(BidOutcome.WITHDRAWN, request(bid = bid(BidStatus.WITHDRAWN)).myBidOutcome)
        assertTrue(request(bid = bid(BidStatus.PENDING)).canManageMyBid)
        assertFalse(request(bid = bid(BidStatus.WITHDRAWN)).canManageMyBid)
    }

    @Test fun `reabrir solo con cierre por tiempo y republicar solo si cancelada`() {
        val closed = AuctionView("a", AuctionStatus.CLOSED, "2026-01-01T00:00:00Z", closeReason = CloseReason.TIMEOUT)
        assertTrue(request(auction = closed).canReopenAuction)
        assertFalse(request(auction = closed.copy(closeReason = CloseReason.MAX_BIDS)).canReopenAuction)
        assertFalse(request(status = RequestStatus.MATCHED, auction = closed).canReopenAuction)
        assertTrue(request(status = RequestStatus.CANCELLED).canRepublish)
        assertFalse(request().canRepublish)
        assertTrue(request().canCancelRequest)
    }

    @Test fun `contraoferta que puedo responder es la de la contraparte`() {
        val fromCarrier = CounterOfferView("c", "b", amount = 900.0, status = CounterOfferStatus.PENDING, proposedByCarrier = true)
        val fromPyme = fromCarrier.copy(proposedByCarrier = false)
        assertTrue(fromCarrier.isPendingFor(viewerIsCarrier = false))
        assertFalse(fromCarrier.isPendingFor(viewerIsCarrier = true))
        assertTrue(fromPyme.isPendingFor(viewerIsCarrier = true))
        assertFalse(fromPyme.copy(status = CounterOfferStatus.EXPIRED).isPendingFor(viewerIsCarrier = true))
    }

    @Test fun `envio por asignar y cancelable`() {
        val s = ShipmentView("s", originCity = "LIMA", destinationCity = "JULIACA", status = ShipmentStatus.PENDING)
        assertTrue(s.needsDriver && s.isCancellable && s.isReassignable)
        assertFalse(s.copy(driverId = "d").needsDriver)
        assertFalse(s.copy(hasActiveIncident = true).isCancellable)
        assertFalse(s.copy(status = ShipmentStatus.IN_TRANSIT).isCancellable)
        assertTrue(s.copy(status = ShipmentStatus.IN_TRANSIT).isReassignable)
        assertFalse(s.copy(status = ShipmentStatus.DELIVERED).isReassignable)
    }
}
