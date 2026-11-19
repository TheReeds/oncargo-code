package pe.logyx.settlement.application.port.out;

import pe.logyx.settlement.domain.model.PaymentMethod;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Cobros de comisión que registra el operador (V24 {@code commission_collections}). */
public interface CommissionCollectionRepository {

    record Collection(UUID id, UUID carrierOrgId, String carrierName, BigDecimal amount, PaymentMethod method,
                      String operationNumber, LocalDate paidOn, String note, int shipmentsCount, UUID recordedBy,
                      String recordedByName, OffsetDateTime createdAt) {}

    void insert(Collection c, UUID recordedByOrgId);

    Paged<Collection> list(UUID carrierOrgId, PageRequest page);
}
