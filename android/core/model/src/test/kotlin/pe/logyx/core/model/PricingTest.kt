package pe.logyx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingTest {
    @Test fun `ejemplo del dueno - 1180 al 8 por ciento da neto 1085,60`() {
        val b = priceBreakdown(1180.0, 0.08)
        assertEquals(1000.0, b.netOfVat, 0.0)
        assertEquals(180.0, b.vat, 0.0)
        assertEquals(80.0, b.commission, 0.0)
        assertEquals(14.4, b.commissionVat, 0.0)
        assertEquals(1085.6, b.carrierNet, 0.0)
    }

    @Test fun `neto mas IGV suman el total y neto mas comision mas su IGV tambien`() {
        for (amount in listOf(1.0, 99.99, 1850.0, 3333.33, 12345.67)) {
            val b = priceBreakdown(amount, 0.08)
            assertEquals(b.amount, b.netOfVat + b.vat, 0.0051)
            assertEquals(b.amount, b.carrierNet + b.commission + b.commissionVat, 0.0051)
        }
    }

    @Test fun `con tasa cero el transportista cobra todo`() {
        val b = priceBreakdown(2500.0, 0.0)
        assertEquals(0.0, b.commission, 0.0)
        assertEquals(2500.0, b.carrierNet, 0.0)
    }

    @Test fun `los atajos nunca bajan del piso`() {
        assertEquals(2000.0, BidShortcut.SUGGESTED.amount(2000.0, 1500.0))
        assertEquals(1900.0, BidShortcut.MINUS_5.amount(2000.0, 1500.0))
        assertEquals(1800.0, BidShortcut.MINUS_10.amount(2000.0, 1500.0))
        assertEquals(1500.0, BidShortcut.FLOOR.amount(2000.0, 1500.0))
        // piso cerca del sugerido: -5 % y -10 % se recortan al piso (RN03)
        assertEquals(1950.0, BidShortcut.MINUS_5.amount(2000.0, 1950.0))
        assertEquals(1950.0, BidShortcut.MINUS_10.amount(2000.0, 1950.0))
        assertNull(BidShortcut.SUGGESTED.amount(null, 1500.0))
        assertTrue(BidShortcut.entries.mapNotNull { it.amount(1234.0, 1100.0) }.all { it >= 1100.0 })
    }

    @Test fun `RN02 por fecha - en ruta puede ofertar y mantenimiento no`() {
        val r = RequestView(id = "r", originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = 2000.0, status = RequestStatus.PUBLISHED)
        fun v(s: VehicleStatus) = VehicleView("v", "ABC-123", VehicleType.TRUCK_MEDIUM, 9000.0, null, s)
        assertEquals(VehicleFit.FITS, v(VehicleStatus.IN_ROUTE).fitForBid(r))
        assertEquals(VehicleFit.FITS, v(VehicleStatus.AVAILABLE).fitForBid(r))
        assertEquals(VehicleFit.UNAVAILABLE, v(VehicleStatus.MAINTENANCE).fitForBid(r))
        // el cambio de vehículo de un envío sigue pidiendo disponible ahora
        assertEquals(VehicleFit.UNAVAILABLE, v(VehicleStatus.IN_ROUTE).fitFor(r))
    }
}
