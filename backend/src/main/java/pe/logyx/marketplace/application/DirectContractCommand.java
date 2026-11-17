package pe.logyx.marketplace.application;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Contrato directo PYME ↔ transportista pactado fuera de la subasta (reserva en un viaje publicado). */
public record DirectContractCommand(UUID requesterOrgId, UUID createdBy, UUID carrierOrgId, UUID vehicleId,
                                    Vehicle.Type vehicleType, String vehiclePlate,
                                    City originCity, String originAddress, City destinationCity,
                                    String destinationAddress, CargoType cargoType, String cargoDescription,
                                    BigDecimal weightKg, LocalDate pickupDate, BigDecimal agreedPrice,
                                    /** Viaje publicado: agrupa los envíos de sus reservas en el mismo vehículo. */
                                    UUID loadGroupId) {}
