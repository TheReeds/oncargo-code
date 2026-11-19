package pe.logyx.activity.domain.model;

/** Quién creó el recurso (solo si quien mira puede ver a esa persona). */
public record Author(String name, String role) {}
