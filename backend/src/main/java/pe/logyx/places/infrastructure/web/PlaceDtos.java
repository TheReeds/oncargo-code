package pe.logyx.places.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.logyx.catalog.domain.City;
import pe.logyx.places.domain.model.PlaceKind;
import pe.logyx.places.domain.model.SavedPlace;
import pe.logyx.shared.domain.TimeOfDay;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class PlaceDtos {
    private PlaceDtos() {}

    /** Alta o edición (PATCH reemplaza todos los campos editables). RN07 no aplica: la libreta es privada. */
    public record PlaceRequest(
            @NotBlank @Size(max = 80) String label,
            @NotBlank String city,
            @NotBlank @Size(max = 200) String address,
            @Size(max = 120) String contactName,
            @Size(max = 20) String contactPhone,
            @Size(max = 120) String openingHours,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String windowFrom,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String windowTo,
            @Size(max = 300) String instructions,
            PlaceKind kind) {

        SavedPlace.Data toData() {
            return new SavedPlace.Data(label, City.require(city), address, contactName, contactPhone, openingHours,
                    TimeOfDay.parse(windowFrom, "windowFrom"), TimeOfDay.parse(windowTo, "windowTo"), instructions, kind);
        }
    }

    public record PlaceView(UUID id, String label, City city, String address, String contactName, String contactPhone,
                            String openingHours,
                            /** Ventana por omisión (HH:mm) para precargar la solicitud. */
                            String windowFrom, String windowTo, String instructions, PlaceKind kind,
                            boolean archived, OffsetDateTime lastUsedAt, OffsetDateTime createdAt) {
        static PlaceView of(SavedPlace p) {
            var d = p.data();
            return new PlaceView(p.id(), d.label(), d.city(), d.address(), d.contactName(), d.contactPhone(),
                    d.openingHours(), TimeOfDay.format(d.windowFrom()), TimeOfDay.format(d.windowTo()),
                    d.instructions(), d.kind(), p.isArchived(), p.lastUsedAt(), p.createdAt());
        }
    }
}
