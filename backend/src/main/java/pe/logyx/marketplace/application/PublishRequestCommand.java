package pe.logyx.marketplace.application;

import pe.logyx.catalog.domain.CargoType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ciudades como código de texto: se validan contra el corredor (LOGYX-PRC-001). */
public record PublishRequestCommand(String originCity, String originAddress, String destinationCity,
                                    String destinationAddress, CargoType cargoType, String cargoDescription,
                                    BigDecimal weightKg, BigDecimal volumeM3, LocalDate pickupDate) {}
