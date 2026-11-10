package pe.logyx.identity.infrastructure.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.identity.application.port.out.PasswordHasher;

@ApplicationScoped
public class BcryptPasswordHasher implements PasswordHasher {

    @Override
    public String hash(String rawPassword) {
        return BcryptUtil.bcryptHash(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return BcryptUtil.matches(rawPassword, hash);
    }
}
