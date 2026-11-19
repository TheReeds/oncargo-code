package pe.logyx.catalog.domain.model;

/** Tipo de día del calendario (V24, 2.14): feriado, paro (p. ej. de transportistas) u otro aviso. */
public enum CalendarDayKind {
    HOLIDAY("Feriado"), STRIKE("Paro"), OTHER("Aviso");

    public final String label;

    CalendarDayKind(String label) { this.label = label; }
}
