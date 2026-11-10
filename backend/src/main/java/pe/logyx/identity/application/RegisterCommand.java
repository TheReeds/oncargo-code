package pe.logyx.identity.application;

import pe.logyx.shared.tenancy.OrgType;

/** Datos de autoregistro. El consentimiento (Ley 29733) ya fue validado en el borde. */
public record RegisterCommand(OrgType organizationType, String legalName, String ruc, String city, String phone,
                              String fullName, String email, String password) {}
