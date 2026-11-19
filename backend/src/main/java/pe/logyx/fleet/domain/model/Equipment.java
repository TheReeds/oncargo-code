package pe.logyx.fleet.domain.model;

/** Equipamiento declarado de un vehículo (2.4). Informativo: no cambia la matriz RN02. */
public enum Equipment {
    GPS("GPS"), RAMP("Rampa"), LIFTGATE("Plataforma hidráulica"), TARP("Toldo o lona"),
    REEFER_CERTIFIED("Frío certificado"), STRAPS("Cintas y amarres");

    public final String label;

    Equipment(String label) { this.label = label; }
}
