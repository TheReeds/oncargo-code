package pe.logyx.settlement.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dato de cobro del transportista (V24 {@code carrier_payout_accounts}, roadmap 2.12): cuenta bancaria con CCI o
 * billetera Yape/Plin. {@code number} viaja en claro solo en memoria; en la BD va cifrado.
 */
public record PayoutAccount(UUID id, UUID carrierOrgId, PayoutKind kind, String bankName, String holderName,
                            String number, String last4, boolean primary, OffsetDateTime archivedAt,
                            OffsetDateTime createdAt) {

    public boolean archived() { return archivedAt != null; }

    /** «•••• 1234» para quien no debe ver el número completo. */
    public String masked() { return "•••• " + last4; }
}
