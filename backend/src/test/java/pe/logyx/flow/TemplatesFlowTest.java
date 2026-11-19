package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api.Session;
import pe.logyx.templates.application.ScheduledTemplates;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Plantillas y cargas recurrentes (2.1, V21, ADR-0013): CRUD y aislamiento, publicar ahora con idempotencia, y el job
 * programado bajo el tenant sintético (publica, deja borrador, salta feriados, falla sin reintentar en bucle y pausa).
 */
@QuarkusTest
class TemplatesFlowTest {

    private static final String T = "/api/v1/request-templates";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject ScheduledTemplates scheduled;
    @Inject SystemTx systemTx;
    @Inject EntityManager em;
    @Inject AuthService auth;

    private static Map<String, Object> template(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("originCity", "LIMA");
        m.put("originAddress", "Av. Argentina 1234, Callao");
        m.put("destinationCity", "JULIACA");
        m.put("destinationAddress", "Jr. San Román 456");
        m.put("cargoType", "GENERAL");
        m.put("cargoDescription", "Quinua en sacos de 50 kg");
        m.put("weightKg", 3000);
        m.put("customerReference", "OC-77");
        m.put("pickupContactName", "Rosa Huanca");
        m.put("pickupContactPhone", "912345678");
        m.put("pickupWindowFrom", "08:00");
        m.put("pickupWindowTo", "12:00");
        return m;
    }

    private static String create(Session pyme, String name) {
        return as(pyme).body(template(name)).post(T).then().statusCode(201).extract().path("data.id");
    }

    /** Programa para el recojo de dentro de 2 días con 2 de anticipación (toca hoy) y fuerza que ya venza. */
    private String scheduleDue(Session pyme, String templateId, String mode) {
        int weekday = LocalDate.now(LIMA).plusDays(2).getDayOfWeek().getValue();
        as(pyme).body(Map.of("frequency", "WEEKLY", "pickupWeekday", weekday, "leadDays", 2, "skipHolidays", false,
                "mode", mode)).put(T + "/" + templateId + "/schedule").then().statusCode(200);
        makeDue(templateId);
        return LocalDate.now(LIMA).plusDays(2).toString();
    }

    private void makeDue(String templateId) {
        systemTx.run(() -> em.createNativeQuery("UPDATE request_schedules SET next_run_at = now() - interval '1 hour' "
                + "WHERE template_id = ?1").setParameter(1, UUID.fromString(templateId)).executeUpdate());
    }

    private static String sub(Session s) {
        String payload = s.token().split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        var m = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new IllegalStateException("token sin sub");
        return m.group(1);
    }

    @Test
    void altaEdicionArchivoYAislamiento() {
        Session pyme = register("PYME", "Plantillas Pyme");
        Session other = register("PYME", "Plantillas Ajena");
        String id = create(pyme, "Quinua semanal");
        as(pyme).get(T + "/" + id).then().statusCode(200).body("data.name", is("Quinua semanal"),
                "data.originCity", is("LIMA"), "data.pickupContactPhone", is("912345678"),
                "data.pickupWindowFrom", is("08:00"), "data.archived", is(false), "data.schedule", nullValue());
        as(pyme).get(T).then().body("data", hasSize(1));

        // Nombre único (sin distinguir mayúsculas) entre las activas
        as(pyme).body(template("QUINUA semanal")).post(T).then().statusCode(409).body("error.code", is("LOGYX-TPL-001"));
        // RN07 también al guardar; el teléfono de contacto de la parada es un campo propio y no se filtra
        var bad = template("Con teléfono");
        bad.put("cargoDescription", "Llamar al 987654321");
        as(pyme).body(bad).post(T).then().statusCode(400).body("error.code", is("LOGYX-NEG-001"),
                "error.field", is("cargoDescription"));
        var same = template("Mismo");
        same.put("destinationCity", "LIMA");
        as(pyme).body(same).post(T).then().statusCode(400).body("error.code", is("LOGYX-PRC-002"));

        var edit = template("Quinua quincenal");
        edit.put("weightKg", 3500);
        as(pyme).body(edit).put(T + "/" + id).then().statusCode(200).body("data.name", is("Quinua quincenal"),
                "data.weightKg", num(3500));

        // Otra organización no la ve ni la toca
        as(other).get(T).then().body("data", hasSize(0));
        as(other).get(T + "/" + id).then().statusCode(404);
        as(other).body(edit).put(T + "/" + id).then().statusCode(404);
        as(other).post(T + "/" + id + "/archive").then().statusCode(404);

        as(pyme).post(T + "/" + id + "/archive").then().statusCode(200).body("data.archived", is(true));
        as(pyme).get(T).then().body("data", hasSize(0));
        as(pyme).get(T + "?includeArchived=true").then().body("data", hasSize(1));
        as(pyme).body(edit).put(T + "/" + id).then().statusCode(409).body("error.code", is("LOGYX-TPL-002"));
        as(pyme).body(Map.of("pickupDate", LocalDate.now().plusDays(3).toString())).post(T + "/" + id + "/publish")
                .then().statusCode(409).body("error.code", is("LOGYX-TPL-002"));
        as(pyme).post(T + "/" + id + "/restore").then().statusCode(200).body("data.archived", is(false));
    }

    @Test
    void elLectorSoloLeeYNoPublica() {
        Session pyme = register("PYME", "Plantillas Lector");
        String id = create(pyme, "Solo lectura");
        String email = "lector" + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(pyme.orgId()), email, "Miembro1234", "Lector", Profile.Role.VIEWER);
        Session viewer = login(email, "Miembro1234");
        as(viewer).get(T).then().statusCode(200).body("data", hasSize(1));
        as(viewer).body(template("Otra")).post(T).then().statusCode(403).body("error.code", is("LOGYX-AUTH-007"));
        as(viewer).body(Map.of("pickupDate", LocalDate.now().plusDays(3).toString())).post(T + "/" + id + "/publish")
                .then().statusCode(403).body("error.code", is("LOGYX-AUTH-007"));
        as(viewer).body(Map.of("frequency", "WEEKLY", "pickupWeekday", 1)).put(T + "/" + id + "/schedule")
                .then().statusCode(403);
    }

    @Test
    void guardarComoPlantillaUnaSolicitudYPublicarAhoraSinDuplicar() {
        Session pyme = register("PYME", "Plantillas Desde");
        var body = new HashMap<String, Object>(template("x"));
        body.remove("name");
        body.put("pickupDate", LocalDate.now().plusDays(5).toString());
        body.put("declaredValue", 12000);
        String requestId = as(pyme).body(body).post("/api/v1/shipment-requests").then().statusCode(201)
                .extract().path("data.id");

        String id = as(pyme).body(Map.of("name", "Desde la solicitud")).post(T + "/from-request/" + requestId)
                .then().statusCode(201).body("data.name", is("Desde la solicitud"), "data.customerReference", is("OC-77"),
                        "data.declaredValue", num(12000), "data.pickupContactName", is("Rosa Huanca"),
                        "data.deliveryContactName", nullValue()).extract().path("data.id");
        as(register("PYME", "Plantillas Ajena2")).body(Map.of("name", "Robada")).post(T + "/from-request/" + requestId)
                .then().statusCode(404);

        // Publicar ahora: con la misma Idempotency-Key devuelve la solicitud ya creada
        String when = LocalDate.now().plusDays(3).toString();
        String key = "tpl-" + UUID.randomUUID();
        String created = as(pyme).header("Idempotency-Key", key).body(Map.of("pickupDate", when))
                .post(T + "/" + id + "/publish").then().statusCode(201)
                .body("data.status", is("PUBLISHED"), "data.pickupDate", is(when), "data.replayed", is(false),
                        "data.code", startsWith("OC-")).extract().path("data.requestId");
        as(pyme).header("Idempotency-Key", key).body(Map.of("pickupDate", when)).post(T + "/" + id + "/publish")
                .then().statusCode(201).header("Idempotent-Replayed", "true")
                .body("data.requestId", is(created), "data.replayed", is(true));
        as(pyme).get("/api/v1/shipment-requests/mine?status=PUBLISHED").then().body("data", hasSize(2));   // la de origen + la creada
        // La solicitud lleva todo lo de la plantilla, contactos incluidos (los ve la PYME dueña)
        as(pyme).get("/api/v1/shipment-requests/" + created).then().body("data.customerReference", is("OC-77"),
                "data.pickupContactName", is("Rosa Huanca"), "data.pickupWindowTo", is("12:00"));
        as(pyme).get(T + "/" + id).then().body("data.lastPublishedAt", notNullValue());
        assertEquals(1L, systemTx.call(() -> ((Number) em.createNativeQuery("SELECT count(*) FROM audit_log WHERE "
                + "action = 'REQUEST_PUBLISHED_BY_TEMPLATE' AND entity_id = ?1").setParameter(1, UUID.fromString(created))
                .getSingleResult()).longValue()));

        // Fecha pasada: 400
        as(pyme).body(Map.of("pickupDate", LocalDate.now().minusDays(1).toString())).post(T + "/" + id + "/publish")
                .then().statusCode(400).body("error.field", is("pickupDate"));
    }

    @Test
    void laProgramacionPublicaSolaEnNombreDeLaOrganizacionYAvisaAlCreador() {
        Session pyme = register("PYME", "Plantillas Job");
        Session other = register("PYME", "Plantillas Job Ajena");
        String id = create(pyme, "Lunes de quinua");
        String pickup = scheduleDue(pyme, id, "PUBLISH");
        as(pyme).get(T + "/" + id).then().body("data.schedule.frequency", is("WEEKLY"),
                "data.schedule.leadDays", is(2), "data.schedule.paused", is(false), "data.schedule.nextPickupDate", is(pickup));

        assertTrue(scheduled.runDue(OffsetDateTime.now()) >= 1);

        var view = as(pyme).get(T + "/" + id).then().statusCode(200)
                .body("data.recentRuns", hasSize(1), "data.recentRuns[0].status", is("PUBLISHED"),
                        "data.recentRuns[0].pickupDate", is(pickup),
                        "data.schedule.nextPickupDate", is(LocalDate.parse(pickup).plusDays(7).toString()),
                        "data.schedule.failures", is(0), "data.lastPublishedAt", notNullValue()).extract();
        String requestId = view.path("data.recentRuns[0].requestId");
        // La solicitud existe, en la organización dueña (RLS la acotó) y a nombre de quien creó la plantilla
        as(pyme).get("/api/v1/shipment-requests/" + requestId).then().statusCode(200)
                .body("data.status", is("PUBLISHED"), "data.pickupDate", is(pickup), "data.customerReference", is("OC-77"));
        as(other).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(0));
        assertEquals(sub(pyme), systemTx.call(() -> String.valueOf(em.createNativeQuery(
                "SELECT created_by FROM shipment_requests WHERE id = ?1").setParameter(1, UUID.fromString(requestId))
                .getSingleResult())));
        assertEquals(1L, systemTx.call(() -> ((Number) em.createNativeQuery("SELECT count(*) FROM audit_log WHERE "
                + "action = 'REQUEST_PUBLISHED_BY_TEMPLATE' AND entity_id = ?1 AND detail LIKE '%automatica=si%'")
                .setParameter(1, UUID.fromString(requestId)).getSingleResult()).longValue()));
        // Aviso a la persona
        as(pyme).get("/api/v1/notifications").then().body("data.find { it.type == 'TEMPLATE_RUN' }.title",
                containsString("Lunes de quinua"), "data.find { it.type == 'TEMPLATE_RUN' }.relatedType", is("REQUEST"));

        // Volver a correr el job no duplica: la siguiente ocurrencia es dentro de una semana
        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(1));
    }

    @Test
    void elModoBorradorDejaUnaSolicitudPorConfirmarYSePuedeDescartar() {
        Session pyme = register("PYME", "Plantillas Borrador");
        String id = create(pyme, "Borradores");
        String pickup = scheduleDue(pyme, id, "DRAFT");
        scheduled.runDue(OffsetDateTime.now());

        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(0));
        String runId = as(pyme).get(T + "/drafts").then().statusCode(200).body("data", hasSize(1),
                "data[0].templateName", is("Borradores"), "data[0].pickupDate", is(pickup),
                "data[0].originCity", is("LIMA")).extract().path("data[0].runId");
        as(pyme).get("/api/v1/notifications").then().body("data.find { it.type == 'TEMPLATE_RUN' }.title",
                containsString("Borrador listo"));
        as(register("PYME", "Plantillas Borrador Ajena")).post(T + "/runs/" + runId + "/publish").then().statusCode(404);

        as(pyme).post(T + "/runs/" + runId + "/publish").then().statusCode(201).body("data.status", is("PUBLISHED"),
                "data.pickupDate", is(pickup));
        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(1));
        as(pyme).get(T + "/drafts").then().body("data", hasSize(0));
        as(pyme).post(T + "/runs/" + runId + "/publish").then().statusCode(409).body("error.code", is("LOGYX-TPL-004"));

        // Segundo borrador: se descarta
        makeDue(id);
        scheduled.runDue(OffsetDateTime.now());
        String second = as(pyme).get(T + "/drafts").then().body("data", hasSize(1)).extract().path("data[0].runId");
        as(pyme).post(T + "/runs/" + second + "/discard").then().statusCode(200).body("data.status", is("DISCARDED"));
        as(pyme).get(T + "/drafts").then().body("data", hasSize(0));
        as(pyme).post(T + "/runs/" + second + "/discard").then().statusCode(409).body("error.code", is("LOGYX-TPL-004"));
    }

    @Test
    void unFeriadoSeSaltaSiLaPersonaLoPidio() {
        Session pyme = register("PYME", "Plantillas Feriado");
        String id = create(pyme, "Navidad");
        int weekday = LocalDate.now(LIMA).plusDays(2).getDayOfWeek().getValue();
        as(pyme).body(Map.of("frequency", "WEEKLY", "pickupWeekday", weekday, "leadDays", 2, "skipHolidays", true,
                "mode", "PUBLISH")).put(T + "/" + id + "/schedule").then().statusCode(200);
        LocalDate christmas = LocalDate.of(LocalDate.now(LIMA).getYear(), 12, 25);
        if (!christmas.isAfter(LocalDate.now(LIMA).plusDays(1))) christmas = christmas.plusYears(1);
        LocalDate day = christmas;
        systemTx.run(() -> em.createNativeQuery("UPDATE request_schedules SET next_pickup_date = ?1, "
                        + "next_run_at = now() - interval '1 hour' WHERE template_id = ?2")
                .setParameter(1, day).setParameter(2, UUID.fromString(id)).executeUpdate());

        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get(T + "/" + id).then().body("data.recentRuns[0].status", is("SKIPPED"),
                "data.recentRuns[0].errorMessage", containsString("Feriado"),
                "data.schedule.nextPickupDate", is(day.plusDays(7).toString()));
        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(0));
    }

    @Test
    void siPublicarFallaSeAvisaNoSeReintentaYSePausaTrasTresFallos() {
        Session pyme = register("PYME", "Plantillas Falla");
        String id = create(pyme, "Se va a caer");
        scheduleDue(pyme, id, "PUBLISH");
        // RN01: la organización queda suspendida
        systemTx.run(() -> em.createNativeQuery("UPDATE organizations SET verification_status = 'SUSPENDED' WHERE id = ?1")
                .setParameter(1, UUID.fromString(pyme.orgId())).executeUpdate());

        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get(T + "/" + id).then().body("data.recentRuns", hasSize(1), "data.recentRuns[0].status", is("FAILED"),
                "data.recentRuns[0].errorCode", is("LOGYX-ORG-004"), "data.schedule.failures", is(1),
                "data.schedule.paused", is(false));
        as(pyme).get("/api/v1/notifications").then().body("data.find { it.type == 'TEMPLATE_RUN' }.title",
                containsString("No se pudo publicar"));
        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(0));

        // Sin reintento en bucle: otra pasada inmediata no hace nada (la siguiente ocurrencia es la semana próxima)
        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get(T + "/" + id).then().body("data.recentRuns", hasSize(1));

        makeDue(id);
        scheduled.runDue(OffsetDateTime.now());
        makeDue(id);
        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get(T + "/" + id).then().body("data.recentRuns", hasSize(3), "data.schedule.failures", is(3),
                "data.schedule.paused", is(true));
        // Pausada: aunque esté vencida, no corre
        makeDue(id);
        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get(T + "/" + id).then().body("data.recentRuns", hasSize(3));
    }

    @Test
    void pausarReanudarYQuitarLaProgramacion() {
        Session pyme = register("PYME", "Plantillas Pausa");
        String id = create(pyme, "Pausable");
        as(pyme).post(T + "/" + id + "/schedule/pause").then().statusCode(409).body("error.code", is("LOGYX-TPL-005"));
        scheduleDue(pyme, id, "PUBLISH");
        as(pyme).post(T + "/" + id + "/schedule/pause").then().statusCode(200).body("data.schedule.paused", is(true));
        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(0));
        as(pyme).post(T + "/" + id + "/schedule/resume").then().statusCode(200).body("data.schedule.paused", is(false));
        as(pyme).body(Map.of("frequency", "BIWEEKLY", "pickupWeekday", 9)).put(T + "/" + id + "/schedule")
                .then().statusCode(400);
        as(pyme).body(Map.of("frequency", "WEEKLY", "pickupWeekday", 1, "leadDays", 15)).put(T + "/" + id + "/schedule")
                .then().statusCode(400);
        as(pyme).body(Map.of("frequency", "WEEKLY", "pickupWeekday", 1, "endsOn", LocalDate.now().minusDays(1).toString()))
                .put(T + "/" + id + "/schedule").then().statusCode(400).body("error.code", is("LOGYX-TPL-003"));
        as(pyme).delete(T + "/" + id + "/schedule").then().statusCode(200).body("data.schedule", nullValue());
        // Archivar pausa la programación: una plantilla archivada no publica sola
        scheduleDue(pyme, id, "PUBLISH");
        as(pyme).post(T + "/" + id + "/archive").then().statusCode(200);
        scheduled.runDue(OffsetDateTime.now());
        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(0));
    }
}
