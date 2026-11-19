package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** RN02 (vehículo disponible y con capacidad) y RN03 (piso de precio) al ofertar. */
class BidEligibilityPolicyTest {

    @Test
    void rechazaOfertaBajoElPiso() {
        var e = assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureAboveFloor(new BigDecimal("99.99"), new BigDecimal("100.00")));
        assertEquals("LOGYX-BID-001", e.code());
        assertDoesNotThrow(() -> BidEligibilityPolicy.ensureAboveFloor(new BigDecimal("100.00"), new BigDecimal("100.00")));
    }

    @Test
    void vehiculoNoDisponibleEsConflicto() {
        var e = assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureVehicleEligible(false, new BigDecimal("8000"), new BigDecimal("100")));
        assertEquals("LOGYX-CAP-002", e.code());
        assertEquals(409, e.status());
    }

    @Test
    void vehiculoSinCapacidadEsRechazado() {
        var e = assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureVehicleEligible(true, new BigDecimal("1000"), new BigDecimal("1000.5")));
        assertEquals("LOGYX-CAP-001", e.code());
        assertDoesNotThrow(() -> BidEligibilityPolicy.ensureVehicleEligible(true, new BigDecimal("1000"), new BigDecimal("1000")));
    }

    @Test
    void erroresIndicanElCampoYFormateanElMonto() {
        var floor = assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureAboveFloor(new BigDecimal("1000"), new BigDecimal("1850")));
        assertEquals("amount", floor.field());
        assertTrue(floor.getMessage().contains("S/ 1,850.00"), floor.getMessage());
        assertEquals("vehicleId", assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureVehicleEligible(false, BigDecimal.TEN, BigDecimal.ONE)).field());
        assertEquals("vehicleId", assertThrows(BusinessException.class,
                () -> BidEligibilityPolicy.ensureVehicleEligible(true, BigDecimal.ONE, BigDecimal.TEN)).field());
    }

    // ----- RN02 por fecha de recojo (decisión del dueño, 2026-11-17)

    private static pe.logyx.fleet.domain.model.Vehicle vehicle(pe.logyx.fleet.domain.model.Vehicle.Status status) {
        return new pe.logyx.fleet.domain.model.Vehicle(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "ABC-123",
                pe.logyx.fleet.domain.model.Vehicle.Type.TRUCK_MEDIUM, new BigDecimal("8000"), null, status,
                java.time.OffsetDateTime.now());
    }

    private static pe.logyx.marketplace.domain.model.ShipmentRequest.Cargo cargo() {
        return new pe.logyx.marketplace.domain.model.ShipmentRequest.Cargo(pe.logyx.catalog.domain.City.LIMA, "Av. 1",
                pe.logyx.catalog.domain.City.JULIACA, "Jr. 2", pe.logyx.catalog.domain.CargoType.GENERAL, "Cajas",
                new BigDecimal("1000"), null, java.time.LocalDate.now().plusDays(5));
    }

    @Test
    void porFechaSoloElMantenimientoBloqueaYElEnRutaNo() {
        assertDoesNotThrow(() -> BidEligibilityPolicy.ensureVehicleEligibleOnDate(
                vehicle(pe.logyx.fleet.domain.model.Vehicle.Status.IN_ROUTE), cargo()));
        assertDoesNotThrow(() -> BidEligibilityPolicy.ensureVehicleEligibleOnDate(
                vehicle(pe.logyx.fleet.domain.model.Vehicle.Status.AVAILABLE), cargo()));
        var e = assertThrows(BusinessException.class, () -> BidEligibilityPolicy.ensureVehicleEligibleOnDate(
                vehicle(pe.logyx.fleet.domain.model.Vehicle.Status.MAINTENANCE), cargo()));
        assertEquals("LOGYX-CAP-002", e.code());
        assertEquals("vehicleId", e.field());
        // el criterio "ahora" de siempre sigue rechazando el en ruta (asignación manual, cambio de vehículo)
        assertThrows(BusinessException.class, () -> BidEligibilityPolicy.ensureVehicleEligible(
                vehicle(pe.logyx.fleet.domain.model.Vehicle.Status.IN_ROUTE), cargo()));
    }

    @Test
    void unVehiculoArchivadoNoOferta() {
        var v = vehicle(pe.logyx.fleet.domain.model.Vehicle.Status.AVAILABLE);
        v.archive(java.time.OffsetDateTime.now());
        var e = assertThrows(BusinessException.class, () -> BidEligibilityPolicy.ensureVehicleEligibleOnDate(v, cargo()));
        assertEquals("LOGYX-CAP-002", e.code());
    }

    @Test
    void topeDeOfertasSolapadasYTextoDelAviso() {
        assertDoesNotThrow(() -> BidEligibilityPolicy.ensureBelowOverlapLimit("ASA-202", 2, 3));
        var e = assertThrows(BusinessException.class, () -> BidEligibilityPolicy.ensureBelowOverlapLimit("ASA-202", 3, 3));
        assertEquals("LOGYX-CAP-007", e.code());
        assertEquals("ASA-202 ya tiene 1 oferta para esas fechas", BidEligibilityPolicy.overlapWarning("ASA-202", 1));
        assertEquals("ASA-202 ya tiene 2 ofertas para esas fechas", BidEligibilityPolicy.overlapWarning("ASA-202", 2));
    }
}
