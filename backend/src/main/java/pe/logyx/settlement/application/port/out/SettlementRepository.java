package pe.logyx.settlement.application.port.out;

import pe.logyx.settlement.domain.model.Settlement;
import pe.logyx.settlement.domain.model.SettlementStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cobros por envío (V24 {@code shipment_settlements} + {@code settlement_history}), bajo la RLS de quien llama. */
public interface SettlementRepository {

    /** Paso del cobro visible para las dos partes. */
    record HistoryEntry(SettlementStatus from, SettlementStatus to, UUID actorProfileId, String actorName,
                        UUID actorOrgId, String note, OffsetDateTime at) {}

    Optional<Settlement> find(UUID shipmentId);

    List<Settlement> findAll(Collection<UUID> shipmentIds);

    void update(Settlement s);

    void addHistory(Settlement s, SettlementStatus from, UUID actorProfileId, UUID actorOrgId, String note);

    List<HistoryEntry> history(UUID shipmentId);
}
