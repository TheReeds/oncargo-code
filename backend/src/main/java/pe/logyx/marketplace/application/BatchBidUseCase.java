package pe.logyx.marketplace.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ofertar a varias cargas de una vez (roadmap 2.10.3). <b>No es atómico</b>: cada oferta va por {@link PlaceBidUseCase}
 * en su propia transacción (RN01–RN04, RN02 por fecha, RN07, documentos de flota, bloqueos de la PYME) y el resultado
 * dice, por ítem, si funcionó (con los avisos de agenda) o con qué código falló. Este bean no es transaccional a propósito.
 */
@ApplicationScoped
public class BatchBidUseCase {

    public static final int MAX_ITEMS = 20;

    public record Item(UUID requestId, UUID vehicleId, BigDecimal amount, String notes) {}

    public record ItemResult(UUID requestId, boolean ok, UUID bidId, List<BidWarning> warnings, String code,
                             String message, String field) {}

    @Inject PlaceBidUseCase placeBid;

    public List<ItemResult> place(List<Item> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Envía entre 1 y " + MAX_ITEMS + " ofertas por vez", "items");
        }
        List<ItemResult> out = new ArrayList<>();
        for (Item i : items) {
            try {
                var placed = placeBid.placeBidWithWarnings(i.requestId(),
                        new PlaceBidCommand(i.vehicleId(), i.amount(), i.notes()));
                out.add(new ItemResult(i.requestId(), true, placed.summary().bid().id(), placed.warnings(), null, null, null));
            } catch (BusinessException e) {
                out.add(new ItemResult(i.requestId(), false, null, List.of(), e.code(), e.getMessage(), e.field()));
            } catch (RuntimeException e) {
                Log.warn("Oferta en lote falló para " + i.requestId(), e);
                out.add(new ItemResult(i.requestId(), false, null, List.of(), "LOGYX-SYS-500",
                        "No pudimos enviar esta oferta. Inténtalo de nuevo.", null));
            }
        }
        return out;
    }
}
