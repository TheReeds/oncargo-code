package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.marketplace.domain.model.ShipmentRequest.Details;
import pe.logyx.marketplace.domain.model.ShipmentRequest.StopDetails;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentRequestDetailsTest {

    private static ShipmentRequest publish(Details details) {
        var cargo = new ShipmentRequest.Cargo(City.LIMA, "Av. Argentina 1234", City.JULIACA, "Jr. Lima 456",
                CargoType.GENERAL, "Cajas", new BigDecimal("1000"), null, LocalDate.now().plusDays(2));
        var pricing = new ShipmentRequest.Pricing(new BigDecimal("1300"), new BigDecimal("2000"), new BigDecimal("1500"));
        return ShipmentRequest.publish(UUID.randomUUID(), UUID.randomUUID(), cargo, pricing, null, "OC-7K2QX", details);
    }

    @Test
    void guardaCodigoYDatosNormalizados() {
        var r = publish(new Details("  OC 4500123 ", new BigDecimal("12500.50"),
                new StopDetails(" María Quispe ", "+51 987 654 321", LocalTime.of(8, 0), LocalTime.of(12, 0), " Puerta 3 "),
                StopDetails.NONE));
        assertEquals("OC-7K2QX", r.code());
        assertEquals("OC 4500123", r.details().customerReference());
        assertEquals(new BigDecimal("12500.50"), r.details().declaredValue());
        assertEquals("María Quispe", r.details().pickup().contactName());
        assertEquals("987654321", r.details().pickup().contactPhone());
        assertEquals("Puerta 3", r.details().pickup().instructions());
        assertTrue(r.details().delivery().isEmpty());
    }

    @Test
    void sinDatosOpcionalesTodoQuedaVacio() {
        var r = publish(null);
        assertNull(r.details().customerReference());
        assertNull(r.details().declaredValue());
        assertTrue(r.details().pickup().isEmpty());
    }

    @Test
    void lasInstruccionesNoPasanPorRn07PeroLaDireccionSi() {
        // El contacto va en campos propios y es privado: no se filtra como texto libre público (decisión 2026-11-17)
        var ok = publish(new Details(null, null, new StopDetails("Ana", "987654321", null, null, "Llamar al 987654321"),
                StopDetails.NONE));
        assertEquals("Llamar al 987654321", ok.details().pickup().instructions());
    }

    @Test
    void ventanaIncompletaOInvertidaFallaConElCampo() {
        var a = assertThrows(BusinessException.class, () -> publish(new Details(null, null,
                new StopDetails(null, null, LocalTime.of(8, 0), null, null), StopDetails.NONE)));
        assertEquals("LOGYX-REQ-006", a.code());
        assertEquals("pickupWindowTo", a.field());
        var b = assertThrows(BusinessException.class, () -> publish(new Details(null, null, StopDetails.NONE,
                new StopDetails(null, null, LocalTime.of(14, 0), LocalTime.of(9, 0), null))));
        assertEquals("deliveryWindowTo", b.field());
    }

    @Test
    void telefonoInvalidoFallaConElCampo() {
        var e = assertThrows(BusinessException.class, () -> publish(new Details(null, null, StopDetails.NONE,
                new StopDetails("Luis", "12345", null, null, null))));
        assertEquals("LOGYX-VAL-002", e.code());
        assertEquals("deliveryContactPhone", e.field());
    }

    @Test
    void valorDeclaradoDebeSerPositivo() {
        var e = assertThrows(BusinessException.class, () -> publish(new Details(null, BigDecimal.ZERO, StopDetails.NONE,
                StopDetails.NONE)));
        assertEquals("LOGYX-REQ-007", e.code());
        assertEquals("declaredValue", e.field());
    }

    @Test
    void referenciaLargaFalla() {
        var e = assertThrows(BusinessException.class, () -> publish(new Details("x".repeat(41), null, StopDetails.NONE,
                StopDetails.NONE)));
        assertEquals("customerReference", e.field());
    }
}
