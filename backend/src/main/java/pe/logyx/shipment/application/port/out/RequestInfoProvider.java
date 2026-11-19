package pe.logyx.shipment.application.port.out;

import pe.logyx.catalog.domain.City;
import pe.logyx.shipment.domain.model.StopContact;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Datos de la solicitud de origen que muestra un envío. Los provee marketplace
 * (adaptador en infrastructure/integration); el puerto evita que la capa
 * application de shipment dependa de marketplace, que ya depende de shipment.
 */
public interface RequestInfoProvider {

    /**
     * {@code code}: código legible que el envío hereda (V17). {@code customerReference} y los contactos son datos
     * privados de la solicitud: el adaptador los entrega tal como los devuelve marketplace bajo la RLS de quien
     * llama; el envío solo los muestra a sus partes.
     */
    record RequestInfo(UUID id, City originCity, City destinationCity, String cargoDescription,
                       BigDecimal weightKg, pe.logyx.catalog.domain.CargoType cargoType, BigDecimal volumeM3,
                       java.time.LocalDate pickupDate, BigDecimal distanceKm, String code, String customerReference,
                       StopContact pickupContact, StopContact deliveryContact, BigDecimal declaredValue) {

        /** Sin valor declarado (anterior a 2.7). */
        public RequestInfo(UUID id, City originCity, City destinationCity, String cargoDescription,
                           BigDecimal weightKg, pe.logyx.catalog.domain.CargoType cargoType, BigDecimal volumeM3,
                           java.time.LocalDate pickupDate, BigDecimal distanceKm, String code, String customerReference,
                           StopContact pickupContact, StopContact deliveryContact) {
            this(id, originCity, destinationCity, cargoDescription, weightKg, cargoType, volumeM3, pickupDate,
                    distanceKm, code, customerReference, pickupContact, deliveryContact, null);
        }

        public RequestInfo(UUID id, City originCity, City destinationCity, String cargoDescription,
                           BigDecimal weightKg, pe.logyx.catalog.domain.CargoType cargoType, BigDecimal volumeM3,
                           java.time.LocalDate pickupDate, BigDecimal distanceKm) {
            this(id, originCity, destinationCity, cargoDescription, weightKg, cargoType, volumeM3, pickupDate,
                    distanceKm, null, null, StopContact.NONE, StopContact.NONE, null);
        }
    }

    Map<UUID, RequestInfo> byIds(Collection<UUID> requestIds);
}
