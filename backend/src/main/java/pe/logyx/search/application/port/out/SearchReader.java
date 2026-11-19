package pe.logyx.search.application.port.out;

import pe.logyx.catalog.domain.City;
import pe.logyx.search.domain.SearchQuery;
import pe.logyx.shared.tenancy.OrgType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lecturas de solo lectura sobre lo que el usuario ya puede ver: las consultas corren con el datasource de la
 * aplicación (RLS de {@code logyx_app}), nunca por {@code SystemTx}.
 */
public interface SearchReader {

    record RequestRow(UUID id, String code, String customerReference, City originCity, City destinationCity,
                      String status, LocalDate pickupDate, boolean viewerHasBid) {}

    record ShipmentRow(UUID id, String code, String customerReference, City originCity, City destinationCity,
                       String status, String vehiclePlate, String counterpartName) {}

    record CounterpartRow(UUID id, String legalName, OrgType type, String city) {}

    List<RequestRow> requests(SearchQuery query, UUID viewerOrg, OrgType viewerType, int limit);

    List<ShipmentRow> shipments(SearchQuery query, UUID viewerOrg, OrgType viewerType, int limit);

    /** Contrapartes con relación (envío u oferta compartidos); el operador ve todas, también por RUC. */
    List<CounterpartRow> counterparts(SearchQuery query, UUID viewerOrg, OrgType viewerType, int limit);
}
