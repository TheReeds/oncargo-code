package pe.logyx.realtime.application.port.out;

import pe.logyx.realtime.domain.model.RealtimeEvent;
import pe.logyx.realtime.domain.model.Recipient;
import pe.logyx.realtime.domain.model.Subscriber;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Persistencia de la proyección {@code realtime_events} (V12). */
public interface RealtimeEventRepository {

    /** Inserta una fila por destinatario (dentro de la transacción del despacho del outbox). */
    void append(String type, Recipient recipient, Map<String, Object> data);

    /** Filas por seq, sin filtrar (solo en {@code SystemTx}: el hub filtra por destinatario en memoria). */
    List<RealtimeEvent> findBySeqs(Collection<Long> seqs);

    /** Filas con {@code seq > after} sin filtrar (solo en {@code SystemTx}: recuperación del listener). */
    List<RealtimeEvent> after(long after, int limit);

    /**
     * Reanudación como el usuario (bajo RLS, además filtrada explícitamente por la organización/tipo del suscriptor):
     * filas con {@code seq > after} dirigidas a la organización o a su tipo (no conductores) o a ese usuario
     * (conductor). Máximo {@code limit}, por seq ascendente.
     */
    List<RealtimeEvent> visibleAfter(Subscriber s, long after, int limit);

    /** Último seq visible para el suscriptor (mismas reglas que {@link #visibleAfter}), 0 si no hay. */
    long latestVisible(Subscriber s);

    /** Borra lo más antiguo que {@code before} (job de limpieza, SystemTx). */
    int deleteOlderThan(OffsetDateTime before);
}
