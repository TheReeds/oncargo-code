package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Acciones masivas sobre «Mis solicitudes» (roadmap 2.10): cancelar con un motivo común, reprogramar la fecha y duplicar
 * para la semana siguiente. <b>No es atómico</b>: cada solicitud va en su propia transacción y el resultado dice, por
 * ítem, si funcionó o con qué código falló (las reglas RN01, RN03 y RN07 se evalúan por ítem). Este bean no es
 * transaccional a propósito: cada caso de uso que invoca abre y cierra la suya.
 */
@ApplicationScoped
public class BatchRequestsUseCase {

    public static final int MAX_ITEMS = 50;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Resultado por solicitud. {@code resultId}/{@code resultCode}: la solicitud nueva (duplicar). */
    public record ItemResult(UUID requestId, boolean ok, String code, String message, String field, UUID resultId,
                             String resultCode) {}

    @Inject CancelRequestUseCase cancel;
    @Inject AuctionRecoveryUseCase recovery;
    @Inject PublishRequestUseCase publish;
    @Inject MarketplaceQueries queries;
    @Inject ShipmentRequestRepository requests;
    @Inject TenantContext tenant;

    public List<ItemResult> cancel(List<UUID> ids, String reason) {
        return each(ids, id -> {
            cancel.cancel(id, reason);
            return new ItemResult(id, true, null, null, null, null, null);
        });
    }

    public List<ItemResult> reschedule(List<UUID> ids, LocalDate pickupDate) {
        if (pickupDate == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica la nueva fecha de recojo", "pickupDate");
        }
        return each(ids, id -> {
            recovery.reschedule(id, pickupDate);
            return new ItemResult(id, true, null, null, null, null, null);
        });
    }

    /**
     * Publica una copia de cada solicitud con el recojo el mismo día de la semana siguiente (nunca en el pasado). Con
     * {@code idempotencyKey} cada copia lleva su propia clave derivada: reintentar el lote no duplica las ya creadas.
     */
    public List<ItemResult> duplicate(List<UUID> ids, String idempotencyKey) {
        return each(ids, id -> {
            RequestSummary src = queries.detail(id);
            ShipmentRequest r = src.request();
            var c = r.cargo();
            String key = idempotencyKey == null ? null : (idempotencyKey + ":" + id);
            var out = publish.publishOnce(new PublishRequestCommand(c.originCity().name(), c.originAddress(),
                    c.destinationCity().name(), c.destinationAddress(), c.cargoType(), c.cargoDescription(),
                    c.weightKg(), c.volumeM3(), nextWeek(c.pickupDate(), LocalDate.now(LIMA)), r.details(), null, null,
                    key, false));
            ShipmentRequest created = out.summary().request();
            return new ItemResult(id, true, null, null, null, created.id(), created.code());
        });
    }

    /** El mismo día de la semana, la semana siguiente a {@code pickup}; si ya pasó, la primera ocurrencia desde hoy. */
    public static LocalDate nextWeek(LocalDate pickup, LocalDate today) {
        LocalDate d = pickup.plusDays(7);
        while (d.isBefore(today)) d = d.plusDays(7);
        return d;
    }

    private List<ItemResult> each(List<UUID> ids, Function<UUID, ItemResult> action) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Selecciona al menos una solicitud", "ids");
        }
        var unique = new ArrayList<>(new LinkedHashSet<>(ids));
        if (unique.size() > MAX_ITEMS) {
            throw BusinessException.badRequest("LOGYX-BAT-001", "Máximo " + MAX_ITEMS + " solicitudes por lote", "ids");
        }
        List<ItemResult> out = new ArrayList<>();
        for (UUID id : unique) {
            try {
                out.add(action.apply(id));
            } catch (BusinessException e) {
                out.add(new ItemResult(id, false, e.code(), e.getMessage(), e.field(), null, null));
            } catch (RuntimeException e) {
                Log.errorf(e, "Fallo inesperado en una acción masiva (solicitud %s)", id);
                out.add(new ItemResult(id, false, "LOGYX-GEN-500", "No se pudo completar esta solicitud", null, null, null));
            }
        }
        return out;
    }

}
