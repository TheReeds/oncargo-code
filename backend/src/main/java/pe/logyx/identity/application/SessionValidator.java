package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.identity.application.event.SessionsRevoked;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ¿Sigue vigente la sesión de este JWT? Compara el claim {@code sv} con {@code profiles.session_version} y exige que
 * el usuario esté activo (F55, F31, F03). Lo usa {@code AccountAccessFilter} en cada petición autenticada.
 *
 * <p>Lee por {@code SystemTx} (antes de la transacción del caso de uso, solo versión/estado/rol del propio usuario)
 * con una caché en memoria de {@code logyx.session.cache-ttl} (30 s). En este proceso la caché se vacía al confirmar
 * el cambio ({@link SessionsRevoked}); con varias instancias, una revocación tarda hasta ese TTL en las demás.
 */
@ApplicationScoped
public class SessionValidator {

    record State(int version, boolean active, long loadedAt) {}

    private static final int MAX_ENTRIES = 50_000;

    private final ConcurrentHashMap<UUID, State> cache = new ConcurrentHashMap<>();

    @Inject SystemTx system;
    @Inject ProfileRepository profiles;

    @ConfigProperty(name = "logyx.session.cache-ttl", defaultValue = "PT30S")
    Duration cacheTtl;

    public boolean isCurrent(UUID profileId, int tokenVersion) {
        State s = state(profileId);
        return s != null && s.active() && s.version() == tokenVersion;
    }

    public void invalidate(UUID profileId) {
        cache.remove(profileId);
    }

    void onRevoked(@Observes(during = TransactionPhase.AFTER_COMPLETION) SessionsRevoked e) {
        invalidate(e.profileId());
    }

    private State state(UUID profileId) {
        long now = System.currentTimeMillis();
        State cached = cache.get(profileId);
        if (cached != null && now - cached.loadedAt() < cacheTtl.toMillis()) return cached;
        // SystemTx: validar la sesión ocurre antes de la transacción de usuario y solo lee el propio perfil
        // (versión de sesión y estado), nunca datos de otra organización.
        Optional<Profile> p = system.call(() -> profiles.findById(profileId));
        State fresh = p.map(x -> new State(x.sessionVersion(), x.active(), now)).orElse(null);
        if (fresh == null) {
            cache.remove(profileId);
            return null;
        }
        if (cache.size() > MAX_ENTRIES) cache.clear();
        cache.put(profileId, fresh);
        return fresh;
    }
}
