package pe.logyx.negotiation.domain;

import pe.logyx.shared.domain.BusinessException;

/**
 * Quién escribe en un hilo (solicitud, transportista) — RF25:
 * <ul>
 *   <li>Mientras la solicitud está publicada: cualquier transportista en su propio hilo (para ofertar o preguntar)
 *       y la PYME dueña en los hilos de transportistas que ofertaron o ya le escribieron.</li>
 *   <li>Adjudicada: solo el hilo con el transportista adjudicado sigue abierto.</li>
 *   <li>Cancelada (o adjudicada a otro): el hilo queda de solo lectura (LOGYX-NEG-002).</li>
 * </ul>
 */
public final class ThreadAccessPolicy {

    public enum RequestState { PUBLISHED, MATCHED, CANCELLED }

    private ThreadAccessPolicy() {}

    public static void ensureCanPost(RequestState state, boolean threadCarrierIsAssigned) {
        boolean open = state == RequestState.PUBLISHED || (state == RequestState.MATCHED && threadCarrierIsAssigned);
        if (!open) {
            throw BusinessException.conflict("LOGYX-NEG-002", "La conversación está cerrada para esta solicitud");
        }
    }
}
