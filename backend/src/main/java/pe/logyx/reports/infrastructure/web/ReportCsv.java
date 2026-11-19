package pe.logyx.reports.infrastructure.web;

import pe.logyx.reports.domain.ReportLine;
import pe.logyx.shared.api.CsvWriter;

import java.util.ArrayList;
import java.util.List;

import static pe.logyx.shared.api.CsvWriter.number;

/** CSV del reporte (2.11): una fila por envío, con IGV y, para transportista y operador, comisión y neto. */
final class ReportCsv {

    private ReportCsv() {}

    static byte[] of(List<ReportLine> lines, boolean showCommission) {
        CsvWriter w = new CsvWriter();
        List<String> header = new ArrayList<>(List.of("Código", "Adjudicado", "Entregado", "Estado", "Origen", "Destino",
                "Tipo de carga", "Peso (kg)", "Km", "PYME", "Transportista", "Placa", "Sugerido (S/)", "Acordado (S/)",
                "Ajustes aceptados (S/)", "Precio final (S/)", "Valor de venta (S/)", "IGV (S/)"));
        if (showCommission) header.addAll(List.of("Tasa de comisión", "Comisión (S/)", "IGV de la comisión (S/)",
                "Neto transportista (S/)"));
        header.add("Cobro");
        w.header(header.toArray(String[]::new));
        for (ReportLine l : lines) {
            List<Object> row = new ArrayList<>(List.of(nz(l.code()), l.awardedOn().toString(),
                    l.deliveredOn() == null ? "" : l.deliveredOn().toString(), l.status(), l.originCity(),
                    l.destinationCity(), l.cargoType(), number(l.weightKg()), number(l.distanceKm()), l.requesterName(),
                    l.carrierName(), nz(l.vehiclePlate()), number(l.suggestedPrice()), number(l.agreedPrice()),
                    number(l.adjustmentsTotal()), number(l.finalPrice()), number(l.netOfVat()), number(l.vat())));
            if (showCommission) {
                row.add(l.commissionRate() == null ? "" : number(l.commissionRate()));
                row.add(number(l.commission()));
                row.add(number(l.commissionVat()));
                row.add(number(l.carrierNet()));
            }
            row.add(l.settlementStatus() == null ? "" : l.settlementStatus());
            w.row(row);
        }
        return w.bytes();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
