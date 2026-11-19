package pe.logyx.marketplace.domain.model;

import pe.logyx.shared.domain.BusinessException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Oferta que la adjudicación (RN05) rechazó pero que se guarda de respaldo, con su posición. Si el envío adjudicado se
 * cancela en las 24 h siguientes, la PYME puede pedirle al transportista que confirme y adjudicarle la solicitud.
 */
public final class BidBackup {

    private final UUID bidId;
    private final UUID requestId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final int rank;
    private BackupState state;
    private final OffsetDateTime createdAt;
    private OffsetDateTime requestedAt;
    private OffsetDateTime respondedAt;
    private OffsetDateTime usedAt;

    public BidBackup(UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, int rank, BackupState state,
                     OffsetDateTime createdAt, OffsetDateTime requestedAt, OffsetDateTime respondedAt,
                     OffsetDateTime usedAt) {
        this.bidId = bidId;
        this.requestId = requestId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.rank = rank;
        this.state = state;
        this.createdAt = createdAt;
        this.requestedAt = requestedAt;
        this.respondedAt = respondedAt;
        this.usedAt = usedAt;
    }

    public static BidBackup keep(Bid rejected, int rank) {
        return new BidBackup(rejected.id(), rejected.requestId(), rejected.requesterOrgId(), rejected.carrierOrgId(),
                rank, BackupState.AVAILABLE, OffsetDateTime.now(), null, null, null);
    }

    /** La PYME pide confirmar. Solo una oferta guardada, que aún no se pidió. */
    public void request() {
        if (state != BackupState.AVAILABLE) {
            throw BusinessException.conflict("LOGYX-BCK-005", state == BackupState.REQUESTED
                    ? "Ya le pediste confirmar a este transportista"
                    : "Esa oferta de respaldo ya no está disponible");
        }
        state = BackupState.REQUESTED;
        requestedAt = OffsetDateTime.now();
    }

    /** El transportista responde a un pedido de confirmación. */
    public void respond(boolean accepted) {
        if (state != BackupState.REQUESTED) {
            throw BusinessException.conflict("LOGYX-BCK-005", "No hay un pedido de confirmación pendiente");
        }
        state = accepted ? BackupState.CONFIRMED : BackupState.DECLINED;
        respondedAt = OffsetDateTime.now();
    }

    /** La PYME adjudica: solo si el transportista confirmó. */
    public void use() {
        if (state != BackupState.CONFIRMED) {
            throw BusinessException.conflict("LOGYX-BCK-004",
                    "El transportista aún no confirmó: pídele que confirme antes de adjudicar");
        }
        state = BackupState.USED;
        usedAt = OffsetDateTime.now();
    }

    public UUID bidId() { return bidId; }
    public UUID requestId() { return requestId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public int rank() { return rank; }
    public BackupState state() { return state; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime requestedAt() { return requestedAt; }
    public OffsetDateTime respondedAt() { return respondedAt; }
    public OffsetDateTime usedAt() { return usedAt; }
}
