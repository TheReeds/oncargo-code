package pe.logyx.search.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.search.application.port.out.SearchReader;
import pe.logyx.search.domain.SearchHit;
import pe.logyx.search.domain.SearchHitType;
import pe.logyx.search.domain.SearchQuery;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Búsqueda global (Ctrl+K): solicitudes, envíos y contrapartes por código, referencia, ruta (y placa o RUC). Solo
 * devuelve lo que el usuario ya puede ver: corre bajo su RLS en una transacción de usuario. Hasta 8 por tipo.
 */
@ApplicationScoped
@Transactional
public class SearchService {

    @Inject SearchReader reader;
    @Inject TenantContext tenant;

    public List<SearchHit> search(String q, String types) {
        SearchQuery query = SearchQuery.parse(q, types);
        var org = tenant.orgId();
        var type = tenant.orgType();
        List<SearchHit> hits = new ArrayList<>();
        if (query.wants(SearchHitType.REQUEST)) {
            for (var r : reader.requests(query, org, type, SearchQuery.LIMIT_PER_TYPE)) {
                hits.add(new SearchHit(SearchHitType.REQUEST, r.id(), r.code(),
                        r.originCity().label + " → " + r.destinationCity().label,
                        subtitle(r.customerReference(), statusLabel(r.status()), r.pickupDate() == null ? null
                                : "recojo " + r.pickupDate()),
                        requestHref(type, r)));
            }
        }
        if (query.wants(SearchHitType.SHIPMENT)) {
            for (var s : reader.shipments(query, org, type, SearchQuery.LIMIT_PER_TYPE)) {
                hits.add(new SearchHit(SearchHitType.SHIPMENT, s.id(), s.code(),
                        s.originCity().label + " → " + s.destinationCity().label,
                        subtitle(s.customerReference(), s.counterpartName(), s.vehiclePlate()), shipmentHref(type, s.id())));
            }
        }
        if (query.wants(SearchHitType.COUNTERPART)) {
            for (var c : reader.counterparts(query, org, type, SearchQuery.LIMIT_PER_TYPE)) {
                hits.add(new SearchHit(SearchHitType.COUNTERPART, c.id(), null, c.legalName(),
                        subtitle(c.type() == OrgType.CARRIER ? "Transportista" : c.type() == OrgType.PYME ? "PYME"
                                : "Operador", c.city()), "/organizaciones/" + c.id()));
            }
        }
        return hits;
    }

    static String requestHref(OrgType viewer, SearchReader.RequestRow r) {
        return switch (viewer) {
            case PYME -> "/pyme/solicitudes/" + r.id();
            case OPERATOR -> "/operador/solicitudes/" + r.id();
            case CARRIER -> r.viewerHasBid() ? "/transportista/ofertas"
                    : "/transportista/marketplace?origen=" + r.originCity().name() + "&destino=" + r.destinationCity().name();
        };
    }

    static String shipmentHref(OrgType viewer, java.util.UUID id) {
        return switch (viewer) {
            case PYME -> "/pyme/envios?id=" + id;
            case OPERATOR -> "/operador/envios/" + id;
            case CARRIER -> "/transportista/envios?id=" + id;
        };
    }

    private static String subtitle(String... parts) {
        return java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank())
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "PUBLISHED" -> "Publicada";
            case "MATCHED" -> "Adjudicada";
            case "CANCELLED" -> "Cancelada";
            default -> status;
        };
    }
}
