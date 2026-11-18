package pe.logyx.shared.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** F42: el 404 concuerda en género con la entidad. */
class NotFoundMessageTest {

    private static String msg(String what) {
        return BusinessException.notFound(what).getMessage();
    }

    @Test
    void sustantivosFemeninos() {
        for (String f : new String[]{"Solicitud", "Oferta", "Incidencia", "Contra-oferta", "Conversación", "Notificación",
                "Organización", "Parada", "Reserva", "Sugerencia"}) {
            assertEquals(f + " no encontrada", msg(f));
        }
    }

    @Test
    void sustantivosMasculinos() {
        for (String m : new String[]{"Envío", "Conductor", "Documento", "Archivo", "Usuario", "Vehículo", "Viaje"}) {
            assertEquals(m + " no encontrado", msg(m));
        }
    }

    @Test
    void conservaCodigoYEstado() {
        var e = BusinessException.notFound("Solicitud");
        assertEquals(404, e.status());
        assertEquals("LOGYX-GEN-404", e.code());
    }
}
