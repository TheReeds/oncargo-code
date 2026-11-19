package pe.logyx.templates.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.application.PublishRequestUseCase;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.TimeOfDay;
import pe.logyx.templates.application.TemplateService;
import pe.logyx.templates.domain.model.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}

    /** Alta o reemplazo de una plantilla: lo mismo que se pide al publicar, sin la fecha de recojo. */
    public record TemplateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank String originCity,
            @NotBlank @Size(max = 200) String originAddress,
            @NotBlank String destinationCity,
            @NotBlank @Size(max = 200) String destinationAddress,
            @NotNull CargoType cargoType,
            @NotBlank @Size(max = 300) String cargoDescription,
            @NotNull @DecimalMin(value = "1", message = "El peso debe ser mayor a 0") @DecimalMax("40000") BigDecimal weightKg,
            @DecimalMin("0.01") BigDecimal volumeM3,
            @Size(max = 40) String customerReference,
            @DecimalMin(value = "0.01", message = "El valor declarado debe ser mayor a 0") @Digits(integer = 12, fraction = 2) BigDecimal declaredValue,
            @Size(max = 120) String pickupContactName,
            @Size(max = 20) String pickupContactPhone,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String pickupWindowFrom,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String pickupWindowTo,
            @Size(max = 300) String pickupInstructions,
            @Size(max = 120) String deliveryContactName,
            @Size(max = 20) String deliveryContactPhone,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String deliveryWindowFrom,
            @Pattern(regexp = TimeOfDay.PATTERN, message = "Usa el formato HH:mm") String deliveryWindowTo,
            @Size(max = 300) String deliveryInstructions,
            UUID originPlaceId, UUID destinationPlaceId,
            /** Al publicar desde esta plantilla, invita a ofertar a los transportistas preferidos (2.9). */
            Boolean invitePreferred) {

        RequestTemplate.Data toData() {
            var details = new ShipmentRequest.Details(customerReference, declaredValue,
                    new ShipmentRequest.StopDetails(pickupContactName, pickupContactPhone,
                            TimeOfDay.parse(pickupWindowFrom, "pickupWindowFrom"),
                            TimeOfDay.parse(pickupWindowTo, "pickupWindowTo"), pickupInstructions),
                    new ShipmentRequest.StopDetails(deliveryContactName, deliveryContactPhone,
                            TimeOfDay.parse(deliveryWindowFrom, "deliveryWindowFrom"),
                            TimeOfDay.parse(deliveryWindowTo, "deliveryWindowTo"), deliveryInstructions));
            return new RequestTemplate.Data(name, City.require(originCity), originAddress, City.require(destinationCity),
                    destinationAddress, cargoType, cargoDescription, weightKg, volumeM3, details, originPlaceId,
                    destinationPlaceId, Boolean.TRUE.equals(invitePreferred));
        }
    }

    /** «Guardar como plantilla» desde una solicitud existente. */
    public record TemplateFromRequest(@NotBlank @Size(max = 80) String name, Boolean invitePreferred) {}

    /** Publicar ahora desde la plantilla: la fecha de recojo no puede ser pasada. */
    public record TemplatePublishNow(@NotNull @FutureOrPresent(message = "La fecha de recojo no puede ser pasada") LocalDate pickupDate) {}

    /**
     * Programación. {@code pickupWeekday}: 1 = lunes … 7 = domingo (día del RECOJO). {@code leadDays}: con cuántos días de
     * anticipación se publica (0-14). {@code mode}: PUBLISH publica sola; DRAFT deja un borrador por confirmar.
     */
    public record TemplateScheduleRequest(@NotNull ScheduleFrequency frequency,
                                  @NotNull @Min(1) @Max(7) Integer pickupWeekday,
                                  @Min(0) @Max(14) Integer leadDays, LocalDate endsOn, Boolean skipHolidays,
                                  ScheduleMode mode) {
        RequestSchedule.Config toConfig() {
            return new RequestSchedule.Config(frequency, DayOfWeek.of(pickupWeekday), leadDays == null ? 2 : leadDays,
                    endsOn, skipHolidays == null || skipHolidays, mode == null ? ScheduleMode.PUBLISH : mode);
        }
    }

    public record TemplateScheduleView(ScheduleFrequency frequency, int pickupWeekday, int leadDays, LocalDate endsOn,
                               boolean skipHolidays, ScheduleMode mode, boolean paused, boolean finished,
                               LocalDate nextPickupDate, OffsetDateTime nextRunAt, OffsetDateTime lastRunAt,
                               int failures) {
        static TemplateScheduleView of(RequestSchedule s) {
            if (s == null) return null;
            var c = s.config();
            return new TemplateScheduleView(c.frequency(), c.pickupWeekday().getValue(), c.leadDays(), c.endsOn(),
                    c.skipHolidays(), c.mode(), s.isPaused(), s.isFinished(), s.nextPickupDate(), s.nextRunAt(),
                    s.lastRunAt(), s.failures());
        }
    }

    public record TemplateRunView(UUID id, LocalDate pickupDate, ScheduleRunStatus status, UUID requestId,
                                  String errorCode, String errorMessage, OffsetDateTime createdAt) {
        static TemplateRunView of(ScheduleRun r) {
            return new TemplateRunView(r.id(), r.pickupDate(), r.status(), r.requestId(), r.errorCode(),
                    r.errorMessage(), r.createdAt());
        }
    }

    public record TemplateView(UUID id, String name, City originCity, String originAddress, City destinationCity,
                               String destinationAddress, CargoType cargoType, String cargoDescription,
                               BigDecimal weightKg, BigDecimal volumeM3, String customerReference,
                               BigDecimal declaredValue, String pickupContactName, String pickupContactPhone,
                               String pickupWindowFrom, String pickupWindowTo, String pickupInstructions,
                               String deliveryContactName, String deliveryContactPhone, String deliveryWindowFrom,
                               String deliveryWindowTo, String deliveryInstructions, UUID originPlaceId,
                               UUID destinationPlaceId, boolean invitePreferred, boolean archived,
                               OffsetDateTime lastPublishedAt, OffsetDateTime createdAt, TemplateScheduleView schedule,
                               List<TemplateRunView> recentRuns) {

        static TemplateView of(TemplateService.Overview o) {
            return of(o.template(), o.schedule(), o.recentRuns());
        }

        static TemplateView of(RequestTemplate t) {
            return of(t, null, List.of());
        }

        static TemplateView of(RequestTemplate t, RequestSchedule s, List<ScheduleRun> runs) {
            var d = t.data();
            var det = d.details();
            return new TemplateView(t.id(), d.name(), d.originCity(), d.originAddress(), d.destinationCity(),
                    d.destinationAddress(), d.cargoType(), d.cargoDescription(), d.weightKg(), d.volumeM3(),
                    det.customerReference(), det.declaredValue(), det.pickup().contactName(),
                    det.pickup().contactPhone(), TimeOfDay.format(det.pickup().windowFrom()),
                    TimeOfDay.format(det.pickup().windowTo()), det.pickup().instructions(),
                    det.delivery().contactName(), det.delivery().contactPhone(),
                    TimeOfDay.format(det.delivery().windowFrom()), TimeOfDay.format(det.delivery().windowTo()),
                    det.delivery().instructions(), d.originPlaceId(), d.destinationPlaceId(), d.invitePreferred(),
                    t.isArchived(), t.lastPublishedAt(), t.createdAt(), TemplateScheduleView.of(s),
                    runs.stream().map(TemplateRunView::of).toList());
        }
    }

    /** Una solicitud creada desde una plantilla; {@code replayed}: ya existía con esa Idempotency-Key. */
    public record PublishedFromTemplate(UUID requestId, String code, ShipmentRequest.Status status,
                                        LocalDate pickupDate, boolean replayed) {
        static PublishedFromTemplate of(PublishRequestUseCase.Published p) {
            ShipmentRequest r = p.summary().request();
            return new PublishedFromTemplate(r.id(), r.code(), r.status(), r.cargo().pickupDate(), p.replayed());
        }
    }

    /** Borrador por confirmar, con lo mínimo de la plantilla para mostrarlo. */
    public record TemplateDraftView(UUID runId, UUID templateId, String templateName, LocalDate pickupDate, City originCity,
                            City destinationCity, CargoType cargoType, BigDecimal weightKg, OffsetDateTime createdAt) {
        static TemplateDraftView of(TemplateService.Draft d) {
            var data = d.template().data();
            return new TemplateDraftView(d.run().id(), d.template().id(), data.name(), d.run().pickupDate(), data.originCity(),
                    data.destinationCity(), data.cargoType(), data.weightKg(), d.run().createdAt());
        }
    }
}
