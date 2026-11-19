package pe.logyx.identity.application.port.out;

import java.time.Duration;

/**
 * Correos de cuenta (RF50 parcial): enlace para restablecer la contraseña e invitación a la organización. El enlace
 * lleva el token en claro ({@code ?token=…}); nunca se loguea. Implementación: Quarkus Mailer (mock en dev/test).
 */
public interface AccountMailer {
    void sendPasswordReset(String to, String fullName, String link, Duration validFor);
    void sendInvitation(String to, String fullName, String organizationName, String invitedByName, String link,
                        Duration validFor);

    /**
     * 2.15: alguien que intentó registrarse con el RUC de la organización pide acceso. Le llega a cada ADMIN activo.
     * El correo del solicitante no está verificado: el texto lo dice.
     */
    default void sendOrganizationAccessRequest(String to, String adminName, String organizationName,
                                               String requesterName, String requesterEmail, String link) {}
}
