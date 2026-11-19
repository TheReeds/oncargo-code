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

    // ----- Mis ofertas (transportista, roadmap 1.5)

    /** Estado de MI oferta como lo muestran la web y Android: aceptada, retirada, cancelada (la PYME canceló), perdida o pendiente. */
    static String bidState(RequestSummary s) {
        var bid = s.myBid().bid();
        if (bid.status() == pe.logyx.marketplace.domain.model.Bid.Status.ACCEPTED) return "ACCEPTED";
        if (bid.status() == pe.logyx.marketplace.domain.model.Bid.Status.WITHDRAWN) return "WITHDRAWN";
        if (s.request().status() == ShipmentRequest.Status.CANCELLED) return "CANCELLED";
        if (bid.status() == pe.logyx.marketplace.domain.model.Bid.Status.REJECTED
                || s.request().status() == ShipmentRequest.Status.MATCHED) return "LOST";
        return "PENDING";
    }

    private static String bidStateLabel(String state) {
        return switch (state) {
            case "ACCEPTED" -> "Aceptada";
            case "WITHDRAWN" -> "Retirada";
            case "CANCELLED" -> "Solicitud cancelada";
            case "LOST" -> "Perdida";
            default -> "Pendiente";
        };
    }

    /**
     * «Neto estimado» = lo que cobraría el transportista con la comisión vigente (ADR-0010: comisión sobre el valor de venta
     * sin IGV y con su IGV); en una oferta ya aceptada es una referencia, el desglose real está en el envío.
     */
    static byte[] myBids(List<RequestSummary> rows, java.util.function.Function<java.math.BigDecimal, java.math.BigDecimal> carrierNet) {
        CsvWriter csv = new CsvWriter().header("Código", "Origen", "Destino", "Tipo de carga", "Peso (kg)", "Fecha de recojo",
                "Mi oferta (S/)", "Neto estimado (S/)", "Vehículo", "Estado de la oferta", "Posición", "Ofertas en la subasta",
                "Enviada el");
        for (RequestSummary s : rows) {
            var r = s.request();
            var c = r.cargo();
            var b = s.myBid().bid();
            String standing = s.myStanding() == null ? null
                    : s.myStanding().rank() + " de " + s.myStanding().bidCount();
            csv.row(java.util.Arrays.asList(r.code(), c.originCity().label, c.destinationCity().label,
                    c.cargoType().name(), CsvWriter.number(c.weightKg()), c.pickupDate().format(DATE),
                    CsvWriter.number(b.amount()), CsvWriter.number(carrierNet.apply(b.amount())), b.vehiclePlate(),
                    bidStateLabel(bidState(s)), standing, s.bidCount(),
                    b.createdAt().atZoneSameInstant(LIMA).format(DATE_TIME)));
        }
        return csv.bytes();
    }
}
