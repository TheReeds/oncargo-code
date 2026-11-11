package pe.logyx.realtime.application.port.out;

import pe.logyx.realtime.domain.model.RealtimeEvent;
import pe.logyx.realtime.domain.model.Subscriber;

import java.time.Instant;

/** Una conexión SSE abierta (adaptador web). Las implementaciones deben ser seguras entre hilos. */
public interface RealtimeConnection {

    Subscriber subscriber();

    /** Cuándo vence el JWT con el que se abrió: el hub cierra la conexión en ese momento. */
    Instant expiresAt();

    /** Entrega un evento; false si la conexión ya no sirve (el hub la descarta). */
    boolean deliver(RealtimeEvent event);

    /** Envía el latido (comentario SSE); false si la conexión ya no sirve. */
    boolean heartbeat();

    /** Envía un evento de control final ({@code stream.expired}) y cierra. */
    void close(String reason);

    boolean isClosed();
}
