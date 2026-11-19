package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.RequestCode;

/** Asigna códigos legibles y únicos ({@link RequestCode}) a las solicitudes nuevas. */
@ApplicationScoped
public class RequestCodes {

    @Inject ShipmentRequestRepository requests;

    /** Un código libre; el índice único de BD cubre la carrera entre dos publicaciones simultáneas (casi imposible). */
    public String next() {
        for (int i = 0; i < 20; i++) {
            String code = RequestCode.random();
            if (!requests.codeExists(code)) return code;
        }
        throw new IllegalStateException("No se pudo asignar un código de solicitud");
    }
}
