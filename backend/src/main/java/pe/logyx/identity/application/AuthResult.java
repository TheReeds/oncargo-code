package pe.logyx.identity.application;

/**
 * Sesión emitida. {@code refreshToken}/{@code refreshExpiresIn} solo vienen si se pidió refresh (opt-in, móvil):
 * {@code refreshExpiresIn} = segundos hasta el vencimiento absoluto de la familia.
 */
public record AuthResult(String accessToken, long expiresIn, UserAccount user, String refreshToken,
                         Long refreshExpiresIn) {

    public AuthResult(String accessToken, long expiresIn, UserAccount user) {
        this(accessToken, expiresIn, user, null, null);
    }
}
