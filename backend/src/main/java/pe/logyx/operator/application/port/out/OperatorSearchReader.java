package pe.logyx.operator.application.port.out;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.OperatorRequestFilter;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shipment.application.OperatorShipmentFilter;

import java.util.List;
import java.util.UUID;

/**
 * Búsqueda por texto del operador (roadmap v2, ítem 1.13): devuelve los ids que coinciden, ya filtrados, ordenados y
 * paginados; el servicio los convierte en resúmenes con las consultas de cada módulo. Corre con las políticas RLS del
 * operador (lectura global).
 */
public interface OperatorSearchReader {

    record Hits(List<UUID> ids, long total) {}

    /** Código (OC-…), referencia de la PYME, razón social/RUC del solicitante o transportista adjudicado, placa, id. */
    Hits requests(String term, OperatorRequestFilter filter, PageRequest page);

    /** Código y referencia de la solicitud, razón social/RUC de ambas partes, placa, conductor, id del envío o la solicitud. */
    Hits shipments(String term, OperatorShipmentFilter filter, PageRequest page);

    /** Razón social, RUC, ciudad, correo de un usuario, placa de un vehículo o id. Excluye las operadoras. */
    Hits organizations(String term, Organization.VerificationStatus status, OrgType type, PageRequest page);
}
