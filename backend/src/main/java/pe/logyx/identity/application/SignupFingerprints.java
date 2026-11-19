package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.shared.tenancy.SystemTx;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Huella del registro para las señales de fraude del operador (roadmap 2.15): SHA-256 de la IP desde la que se creó la
 * organización ({@code organizations.signup_ip_hash}, V23). Nunca se guarda la IP. Las direcciones locales o privadas
 * (dev, tests, redes internas) no se registran: no distinguen a nadie. SystemTx: el registro corre sin usuario.
 */
@ApplicationScoped
public class SignupFingerprints {

    @Inject SystemTx system;
    @Inject EntityManager em;

    public void record(UUID organizationId, String ip) {
        String hash = hash(ip);
        if (organizationId == null || hash == null) return;
        system.run(() -> em.createNativeQuery("UPDATE organizations SET signup_ip_hash = ?2 WHERE id = ?1")
                .setParameter(1, organizationId).setParameter(2, hash).executeUpdate());
    }

    /** null si la IP es local, privada o no se puede leer. */
    static String hash(String ip) {
        if (ip == null || ip.isBlank()) return null;
        String literal = ip.strip();
        if (!literal.matches("[0-9A-Fa-f:.]{2,45}")) return null;   // solo literales: getByName no resuelve DNS
        try {
            InetAddress a = InetAddress.getByName(literal);
            if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) {
                return null;
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(("oncargo-signup|" + a.getHostAddress())
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.net.UnknownHostException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
