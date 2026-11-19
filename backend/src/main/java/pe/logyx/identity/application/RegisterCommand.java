package pe.logyx.identity.application;

import pe.logyx.shared.tenancy.OrgType;

/**
 * Datos de autoregistro. El consentimiento (Ley 29733) y la aceptación de los Términos ya fueron validados en el borde.
 * {@code termsVersion}: versión de los Términos y condiciones aceptada (nula solo en altas internas: seed y tests).
 */
public record RegisterCommand(OrgType organizationType, String legalName, String ruc, String city, String phone,
                              String fullName, String email, String password, String termsVersion) {

    /** Alta sin aceptación registrada (seed dev y tests de dominio). */
    public RegisterCommand(OrgType organizationType, String legalName, String ruc, String city, String phone,
                           String fullName, String email, String password) {
        this(organizationType, legalName, ruc, city, phone, fullName, email, password, null);
    }
}
