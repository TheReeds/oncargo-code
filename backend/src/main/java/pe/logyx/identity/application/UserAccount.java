package pe.logyx.identity.application;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;

/**
 * Usuario con su organización (lo que necesita "me" y la respuesta de login). {@code termsAccepted} (V24, 2.17): la
 * persona aceptó la versión vigente de los Términos y condiciones (lo arma {@link TermsService#account}).
 */
public record UserAccount(Profile profile, Organization organization, boolean termsAccepted) {}
