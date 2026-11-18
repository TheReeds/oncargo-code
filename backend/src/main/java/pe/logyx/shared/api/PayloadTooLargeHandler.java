package pe.logyx.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.configuration.MemorySize;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * F45: {@code quarkus.http.limits.max-body-size} corta la petición dentro del lector del cuerpo y respondía 413 sin
 * cuerpo. Este filtro se adelanta cuando el cliente declara un {@code Content-Length} mayor que el límite (lo hacen
 * los navegadores y los clientes HTTP con archivos) y devuelve el mismo sobre de error ({@code LOGYX-HTTP-413}) en
 * español. Un cuerpo en trozos (sin Content-Length) que exceda el límite sigue cortándose con 413 sin cuerpo.
 */
@ApplicationScoped
public class PayloadTooLargeHandler {

    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.http.limits.max-body-size")
    Optional<MemorySize> maxBodySize;

    void register(@Observes Router router) {
        if (maxBodySize.isEmpty()) return;
        long limit = maxBodySize.get().asLongValue();
        router.route().order(-1_000_000).handler(ctx -> {
            long declared = declaredLength(ctx);
            if (declared <= limit) {
                ctx.next();
                return;
            }
            try {
                String body = mapper.writeValueAsString(ApiError.of("LOGYX-HTTP-413",
                        ExceptionMappers.httpMessage(413), "file"));
                ctx.response().setStatusCode(413).putHeader("Content-Type", "application/json")
                        .putHeader("Connection", "close").end(body);
            } catch (Exception e) {
                ctx.next();
            }
        });
    }

    private static long declaredLength(RoutingContext ctx) {
        String h = ctx.request().getHeader("Content-Length");
        if (h == null) return -1;
        try {
            return Long.parseLong(h.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
