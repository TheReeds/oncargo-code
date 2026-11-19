package pe.logyx.shipment.infrastructure.web;

import pe.logyx.shared.api.CsvWriter;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** Exportación CSV de «Mis envíos» (roadmap 1.5): misma consulta, filtros y RLS que el listado JSON. */
final class ShipmentCsv {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ShipmentCsv() {}

    /** {@code asCarrier}: la contraparte es la PYME (columna «PYME»); si no, el transportista. */
    static byte[] shipments(List<ShipmentSummary> rows, boolean asCarrier) {
        CsvWriter csv = new CsvWriter().header("Código", "Referencia", "Origen", "Destino", "Carga", "Peso (kg)",
                "Fecha de recojo", "Creado el", "Estado", asCarrier ? "PYME" : "Transportista", "Placa", "Conductor",
                "Precio acordado (S/)", "Recogido el", "Entregado el", "Motivo de cancelación", "Ajustes aceptados (S/)",
                "Precio final (S/)");
        for (ShipmentSummary sum : rows) {
            Shipment s = sum.shipment();
            var r = sum.request();
            var pickup = sum.stops().stream().filter(x -> x.sequence() == 1).findFirst().orElse(null);
            csv.row(Arrays.asList(r == null ? null : r.code(), r == null ? null : r.customerReference(),
                    r == null ? null : r.originCity().label, r == null ? null : r.destinationCity().label,
                    r == null ? null : r.cargoDescription(), r == null ? null : CsvWriter.number(r.weightKg()),
                    r == null || r.pickupDate() == null ? null : r.pickupDate().format(DATE),
                    dateTime(s.createdAt()), status(s.status()), asCarrier ? sum.requesterName() : sum.carrierName(),
                    s.vehiclePlate(), s.driverName(), CsvWriter.number(s.agreedPrice()),
                    pickup == null ? null : dateTime(pickup.completedAt()), dateTime(s.deliveredAt()),
                    s.cancellation() == null ? null : s.cancellation().reason(),
                    CsvWriter.number(sum.adjustmentsTotal()), CsvWriter.number(sum.finalPrice())));
        }
        return csv.bytes();
    }

    private static String dateTime(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(LIMA).format(DATE_TIME);
    }

    private static String status(Shipment.Status s) {
        return switch (s) {
            case PENDING -> "Por recoger";
            case IN_TRANSIT -> "En tránsito";
            case DELIVERED -> "Entregado";
            case CANCELLED -> "Cancelado";
        };
    }
}
