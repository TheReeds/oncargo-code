package pe.logyx.identity.infrastructure.scheduling;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.identity.application.RefreshSessions;

/** Borra tokens de contraseña y familias de refresh vencidos ({@code logyx.auth.token-cleanup.every}). */
@ApplicationScoped
public class AuthTokenCleanup {

    @Inject RefreshSessions sessions;

    @Scheduled(every = "{logyx.auth.token-cleanup.every}", identity = "auth-token-cleanup", delayed = "1m",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void cleanup() {
        int n = sessions.cleanup();
        if (n > 0) Log.infof("Tokens de cuenta vencidos borrados: %d", n);
    }
}
