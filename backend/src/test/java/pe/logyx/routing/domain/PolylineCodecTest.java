package pe.logyx.routing.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.routing.domain.model.LatLng;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolylineCodecTest {

    /** Ejemplo de la especificación del algoritmo de Google. */
    private static final String SAMPLE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Test
    void decodificaElEjemploDeReferencia() {
        List<LatLng> pts = PolylineCodec.decode(SAMPLE, 5);
        assertEquals(List.of(new LatLng(38.5, -120.2), new LatLng(40.7, -120.95), new LatLng(43.252, -126.453)), pts);
    }

    @Test
    void codificaYDecodificaIdaYVuelta() {
        List<LatLng> pts = List.of(new LatLng(-15.5, -70.13333), new LatLng(-15.84020, -70.02190), new LatLng(-16.409, -71.5375));
        assertEquals(SAMPLE, PolylineCodec.encode(PolylineCodec.decode(SAMPLE, 5), 5));
        List<LatLng> back = PolylineCodec.decode(PolylineCodec.encode(pts, 5), 5);
        for (int i = 0; i < pts.size(); i++) {
            assertEquals(pts.get(i).lat(), back.get(i).lat(), 1e-5);
            assertEquals(pts.get(i).lng(), back.get(i).lng(), 1e-5);
        }
    }

    @Test
    void rechazaUnaPolylineTruncada() {
        assertThrows(IllegalArgumentException.class, () -> PolylineCodec.decode("_p~iF~ps|U_", 5));
    }
}
