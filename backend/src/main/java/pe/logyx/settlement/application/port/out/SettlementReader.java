package pe.logyx.settlement.application.port.out;

import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Listados de cobros (SQL nativo <b>bajo RLS</b>: la PYME ve sus envíos, el transportista los suyos y el operador todos).
 * Un envío entregado sin fila de cobro aparece como {@code PENDING_INVOICE} con vencimiento = entrega + plazo por
 * omisión (la fila la abre el sistema al entregar; esto cubre los segundos hasta que se despacha el evento).
 */
public interface SettlementReader {

    /** Filtros (todos opcionales). {@code commissionPending}: solo comisiones por cobrar. */
    record Filter(Set<String> statuses, Boolean overdue, UUID carrierOrgId, Boolean commissionPending,
                  LocalDate from, LocalDate to, LocalDate today, int defaultTermsDays) {}

    /** Un envío entregado y su cobro. {@code acceptedAdjustments}: suma de ajustes aceptados (con IGV). */
    record Row(UUID shipmentId, String code, String originCity, String destinationCity, UUID requesterOrgId,
               String requesterName, UUID carrierOrgId, String carrierName, OffsetDateTime deliveredAt,
               BigDecimal agreedPrice, BigDecimal acceptedAdjustments, long pendingAdjustments,
               BigDecimal commissionRate, String status, LocalDate dueOn, String invoiceNumber, LocalDate paidOn,
               BigDecimal paidAmount, String commissionStatus) {}

    Paged<Row> list(Filter filter, PageRequest page);

    /** Hasta {@code limit} filas (para totales y jobs) y el total real que cumple el filtro. */
    record Batch(List<Row> rows, long total) {}

    Batch top(Filter filter, int offset, int limit);

    /** Filas de esos envíos (las que quien llama ve). */
    List<Row> rows(Collection<UUID> shipmentIds, int defaultTermsDays);
}
