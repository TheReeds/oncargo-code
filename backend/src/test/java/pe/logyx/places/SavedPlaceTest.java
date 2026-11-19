package pe.logyx.places;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;
import pe.logyx.places.domain.model.PlaceKind;
import pe.logyx.places.domain.model.SavedPlace;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SavedPlaceTest {

    private static SavedPlace.Data data(String label, String phone, LocalTime from, LocalTime to) {
        return new SavedPlace.Data(label, City.LIMA, " Av. Argentina 1234 ", " Rosa ", phone, "Lun-Vie 8-17", from, to,
                null, null);
    }

    @Test
    void creaNormalizandoYPorOmisionSirveParaAmbos() {
        var p = SavedPlace.create(UUID.randomUUID(), UUID.randomUUID(), data(" Almacén Callao ", "+51 987 654 321", null, null));
        assertEquals("Almacén Callao", p.data().label());
        assertEquals("Av. Argentina 1234", p.data().address());
        assertEquals("987654321", p.data().contactPhone());
        assertEquals(PlaceKind.BOTH, p.data().kind());
        assertFalse(p.isArchived());
    }

    @Test
    void nombreYDireccionSonObligatorios() {
        var e = assertThrows(BusinessException.class,
                () -> SavedPlace.create(UUID.randomUUID(), UUID.randomUUID(), data(" ", null, null, null)));
        assertEquals("label", e.field());
    }

    @Test
    void horarioDebeSerCompletoYOrdenado() {
        assertThrows(BusinessException.class, () -> SavedPlace.create(UUID.randomUUID(), UUID.randomUUID(),
                data("A", null, LocalTime.of(8, 0), null)));
        var e = assertThrows(BusinessException.class, () -> SavedPlace.create(UUID.randomUUID(), UUID.randomUUID(),
                data("A", null, LocalTime.of(17, 0), LocalTime.of(8, 0))));
        assertEquals("LOGYX-PLC-001", e.code());
    }

    @Test
    void archivadaNoSeEditaHastaRestaurar() {
        var p = SavedPlace.create(UUID.randomUUID(), UUID.randomUUID(), data("A", null, null, null));
        p.archive();
        assertTrue(p.isArchived());
        var e = assertThrows(BusinessException.class, () -> p.update(data("B", null, null, null)));
        assertEquals("LOGYX-PLC-002", e.code());
        p.restore();
        p.update(data("B", null, null, null));
        assertEquals("B", p.data().label());
    }

    @Test
    void kindServes() {
        assertTrue(PlaceKind.BOTH.serves(PlaceKind.PICKUP));
        assertTrue(PlaceKind.PICKUP.serves(PlaceKind.PICKUP));
        assertFalse(PlaceKind.PICKUP.serves(PlaceKind.DELIVERY));
    }
}
