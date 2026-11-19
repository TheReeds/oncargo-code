package pe.logyx.marketplace.application;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ciudades como código de texto: se validan contra el corredor (LOGYX-PRC-001). Los datos opcionales (referencia,
 * valor declarado, contactos) y los ids de las direcciones guardadas de las que se publica ({@code originPlaceId},
 * {@code destinationPlaceId}: solo para ordenar la libreta por uso) pueden ir nulos.
 * {@code idempotencyKey}: cabecera {@code Idempotency-Key}; con la misma clave no se crea una segunda solicitud.
 * {@code invitePreferred}: al publicar, avisa a los transportistas preferidos de la PYME (roadmap 2.9).
 */
public record PublishRequestCommand(String originCity, String originAddress, String destinationCity,
                                    String destinationAddress, CargoType cargoType, String cargoDescription,
                                    BigDecimal weightKg, BigDecimal volumeM3, LocalDate pickupDate,
                                    ShipmentRequest.Details details, UUID originPlaceId, UUID destinationPlaceId,
                                    String idempotencyKey, boolean invitePreferred) {

    public PublishRequestCommand(String originCity, String originAddress, String destinationCity,
                                 String destinationAddress, CargoType cargoType, String cargoDescription,
                                 BigDecimal weightKg, BigDecimal volumeM3, LocalDate pickupDate,
                                 ShipmentRequest.Details details, UUID originPlaceId, UUID destinationPlaceId,
                                 String idempotencyKey) {
        this(originCity, originAddress, destinationCity, destinationAddress, cargoType, cargoDescription, weightKg,
                volumeM3, pickupDate, details, originPlaceId, destinationPlaceId, idempotencyKey, false);
    }

    public PublishRequestCommand(String originCity, String originAddress, String destinationCity,
                                 String destinationAddress, CargoType cargoType, String cargoDescription,
                                 BigDecimal weightKg, BigDecimal volumeM3, LocalDate pickupDate) {
        this(originCity, originAddress, destinationCity, destinationAddress, cargoType, cargoDescription, weightKg,
                volumeM3, pickupDate, ShipmentRequest.Details.NONE, null, null, null, false);
    }
}
