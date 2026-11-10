package pe.logyx.identity.application;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;

/** Usuario con su organización (lo que necesita "me" y la respuesta de login). */
public record UserAccount(Profile profile, Organization organization) {}
