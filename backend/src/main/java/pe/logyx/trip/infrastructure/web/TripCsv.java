package pe.logyx.trip.infrastructure.web;

import pe.logyx.shared.api.CsvWriter;
import pe.logyx.trip.application.TripSummary;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** Exportación CSV de «Mis viajes» (misma consulta y RLS que el listado JSON). */
final class TripCsv {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TripCsv() {}

    static byte[] trips(List<TripSummary> rows) {
        CsvWriter csv = new CsvWriter().header("Origen", "Destino", "Salida", "Vehículo", "Capacidad (kg)",
                "Reservado (kg)", "Tarifa", "Tipo de tarifa", "Estado", "Reservas confirmadas", "Reservas por confirmar",
                "Publicado el");
        for (TripSummary s : rows) {
            var t = s.trip();
            csv.row(Arrays.asList(t.originCity().label, t.destinationCity().label,
                    t.departureAt().atZoneSameInstant(LIMA).format(DATE_TIME), t.vehiclePlate(),
                    CsvWriter.number(t.capacityKg()), CsvWriter.number(t.confirmedWeightKg()), CsvWriter.number(t.rate()),
                    t.pricingMode() == pe.logyx.trip.domain.model.TripPricingMode.PER_KG ? "Por kg" : "Por tramo", t.status().name(),
                    s.confirmedBookings(), s.pendingBookings(), t.createdAt().atZoneSameInstant(LIMA).format(DATE_TIME)));
        }
        return csv.bytes();
    }
}
