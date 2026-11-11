package pe.logyx.realtime.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

/** Evento ya proyectado ({@code realtime_events}): {@code seq} es el id SSE (Last-Event-ID). */
public record RealtimeEvent(long seq, String type, Recipient recipient, Map<String, Object> data,
                            OffsetDateTime occurredAt) {}
