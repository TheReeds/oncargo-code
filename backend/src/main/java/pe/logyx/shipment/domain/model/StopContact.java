package pe.logyx.shipment.domain.model;

import java.time.LocalTime;

/**
 * Contacto estructurado de una parada (RN07 post-adjudicación, roadmap 1.3): a quién buscar, su teléfono (9 dígitos,
 * ya normalizado por la solicitud), la ventana horaria en que reciben y las instrucciones de acceso. Se copia de la
 * solicitud al adjudicar y solo lo ven las dos partes del envío, el conductor asignado y el operador.
 */
public record StopContact(String name, String phone, LocalTime windowFrom, LocalTime windowTo, String instructions) {

    public static final StopContact NONE = new StopContact(null, null, null, null, null);

    public boolean isEmpty() {
        return name == null && phone == null && windowFrom == null && windowTo == null && instructions == null;
    }

    /** NONE si no hay ningún dato (evita guardar objetos vacíos). */
    public static StopContact ofNullable(StopContact c) {
        return c == null ? NONE : c;
    }
}
