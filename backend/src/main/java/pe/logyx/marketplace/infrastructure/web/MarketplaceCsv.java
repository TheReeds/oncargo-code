package pe.logyx.marketplace.infrastructure.web;

import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.api.CsvWriter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Exportación CSV de "Mis solicitudes" (misma consulta, filtros y RLS que el listado JSON). */
final class MarketplaceCsv {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private MarketplaceCsv() {}

    static byte[] requests(List<RequestSummary> rows) {
        CsvWriter csv = new CsvWriter().header("Código", "Referencia", "Origen", "Destino", "Tipo de carga",
                "Descripción", "Peso (kg)", "Valor declarado (S/)", "Fecha de recojo", "Estado", "Precio sugerido (S/)",
                "Ofertas", "Mejor oferta (S/)", "Creada el", "Motivo de cancelación");
        for (RequestSummary s : rows) {
            ShipmentRequest r = s.request();
            var c = r.cargo();
            boolean open = r.status() == ShipmentRequest.Status.PUBLISHED;
            csv.row(java.util.Arrays.asList(r.code(), r.details().customerReference(), c.originCity().label,
                    c.destinationCity().label, c.cargoType().name(), c.cargoDescription(), CsvWriter.number(c.weightKg()),
                    CsvWriter.number(r.details().declaredValue()), c.pickupDate().format(DATE), status(r.status()),
                    CsvWriter.number(r.pricing().suggestedPrice()), open ? s.bidCount() : null,
                    s.bestBid() == null ? null : CsvWriter.number(s.bestBid().bid().amount()),
                    r.createdAt().atZoneSameInstant(LIMA).format(DATE_TIME), r.cancelReason()));
        }
        return csv.bytes();
    }

    private static String status(ShipmentRequest.Status s) {
        return switch (s) {
            case PUBLISHED -> "Publicada";
            case MATCHED -> "Adjudicada";
            case CANCELLED -> "Cancelada";
        };
    }
}
