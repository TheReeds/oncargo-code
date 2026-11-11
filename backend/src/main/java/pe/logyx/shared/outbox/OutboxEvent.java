package pe.logyx.shared.outbox;

/**
 * Marca un evento de dominio que se entrega por el outbox transaccional (ADR-0004, ADR-0006).
 *
 * <p>El record se serializa a JSON con Jackson en la misma transacción de negocio (tabla {@code outbox_events})
 * y el {@link OutboxDispatcher} lo vuelve a disparar como evento CDI con el calificador {@link Outboxed}. Debe
 * ser un record inmutable con los datos que el consumidor necesita; cambiar sus campos de forma incompatible
 * rompe la deserialización de los eventos pendientes (agrega campos, no renombres).
 */
public interface OutboxEvent {}
