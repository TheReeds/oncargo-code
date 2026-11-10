package pe.logyx.catalog.domain;

/**
 * Tipos de carga. Vocabulario compartido: el recargo por tipo vive en
 * {@code pricing.domain.TariffCalculator}.
 */
public enum CargoType {
    GENERAL("Carga general"),
    FRAGILE("Frágil"),
    PERISHABLE("Perecedero"),
    HAZARDOUS("Materiales peligrosos"),
    BULK("Granel"),
    OVERSIZED("Sobredimensionado");

    public final String label;

    CargoType(String label) {
        this.label = label;
    }
}
