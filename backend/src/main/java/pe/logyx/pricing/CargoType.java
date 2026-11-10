package pe.logyx.pricing;

import java.math.BigDecimal;

public enum CargoType {
    GENERAL("Carga general", "1.00"),
    FRAGILE("Frágil", "1.30"),
    PERISHABLE("Perecedero", "1.25"),
    HAZARDOUS("Materiales peligrosos", "1.50"),
    BULK("Granel", "0.95"),
    OVERSIZED("Sobredimensionado", "1.40");

    public final String label;
    public final BigDecimal multiplier;

    CargoType(String label, String multiplier) {
        this.label = label;
        this.multiplier = new BigDecimal(multiplier);
    }
}
