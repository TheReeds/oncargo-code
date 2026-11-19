package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Parámetros de sistema editables por el operador (V18): comisión, SLA y umbrales de incidencias, con historial. */
@QuarkusTest
class OperatorParametersFlowTest {

    @Inject AuthService auth;

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Parámetros " + System.nanoTime() % 1000, Api.newRuc(), "Operador Config",
                email, "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    private static io.restassured.response.ValidatableResponse put(Session s, String key, Map<String, Object> body) {
        return as(s).body(body).put("/api/v1/operator/parameters/" + key).then();
    }

    private static String valueOf(Session op, String key) {
        return as(op).get("/api/v1/operator/parameters").then().statusCode(200).extract()
                .path("data.find { it.key == '" + key + "' }.value");
    }

    @Test
    void listaLosParametrosConSuValorPorOmisionYSoloElOperadorLosVe() {
        Session op = operator();
        as(op).get("/api/v1/operator/parameters").then().statusCode(200)
                .body("data.key", hasItems("commission_rate", "assignment_sla_hours", "incident_warn_hours",
                        "incident_breach_hours"))
                .body("data.find { it.key == 'commission_rate' }.kind", is("RATE"))
                .body("data.find { it.key == 'commission_rate' }.defaultValue", is("0.08"))
                .body("data.find { it.key == 'incident_warn_hours' }.defaultValue", is("12"))
                .body("data.find { it.key == 'incident_breach_hours' }.defaultValue", is("24"));

        Session pyme = register("PYME", "Params Pyme");
        as(pyme).get("/api/v1/operator/parameters").then().statusCode(403);
        as(pyme).get("/api/v1/operator/parameters/history").then().statusCode(403);
        put(pyme, "commission_rate", Map.of("value", "0.01", "reason", "intento")).statusCode(403);
        Session carrier = register("CARRIER", "Params Carrier");
        put(carrier, "commission_rate", Map.of("value", "0.01", "reason", "intento")).statusCode(403);
    }

    @Test
    void cambiarLaComisionSubeLaVersionGuardaHistorialYAudita() {
        Session op = operator();
        String before = valueOf(op, "commission_rate");
        String target = before.equals("0.1") ? "0.12" : "0.1";
        int v0 = as(op).get("/api/v1/operator/parameters").then().extract()
                .path("data.find { it.key == 'commission_rate' }.version");
        try {
            put(op, "commission_rate", Map.of("value", target, "reason", "Piloto con comisión del " + target,
                    "expectedVersion", v0)).statusCode(200)
                    .body("data.key", is("commission_rate")).body("data.value", is(target))
                    .body("data.version", is(v0 + 1)).body("data.overridden", is(true))
                    .body("data.reason", startsWith("Piloto"));

            // Cualquier rol la lee al instante: la API de comisión usa el valor vigente
            Session pyme = register("PYME", "Comision Pyme");
            as(pyme).get("/api/v1/pricing/commission?amount=1180").then().statusCode(200)
                    .body("data.commissionRate", num(Double.parseDouble(target)))
                    .body("data.vatRate", num(0.18))
                    .body("data.breakdown.netOfVat", num(1000.0))
                    .body("data.breakdown.vat", num(180.0));

            // Historial: valor anterior, nuevo, motivo y quién
            as(op).get("/api/v1/operator/parameters/history?key=commission_rate").then().statusCode(200)
                    .body("data[0].key", is("commission_rate")).body("data[0].version", is(v0 + 1))
                    .body("data[0].previousValue", is(before)).body("data[0].newValue", is(target))
                    .body("data[0].changedByEmail", is(op.email())).body("meta.total", greaterThanOrEqualTo(1));

            // Auditoría del operador
            as(op).get("/api/v1/operator/audit?action=SYSTEM_PARAMETER_CHANGED&size=5").then().statusCode(200)
                    .body("data[0].action", is("SYSTEM_PARAMETER_CHANGED"))
                    .body("data[0].detail", containsString("commission_rate"));

            // Métricas con el valor vigente
            as(op).get("/api/v1/operator/metrics").then().statusCode(200)
                    .body("data.commissionRate", num(Double.parseDouble(target)));
        } finally {
            put(op, "commission_rate", Map.of("value", before, "reason", "Restaurar el valor de la prueba")).statusCode(200);
        }
    }

    @Test
    void rechazaValoresInvalidosMotivoFaltanteYVersionObsoleta() {
        Session op = operator();
        put(op, "commission_rate", Map.of("value", "0.5", "reason", "demasiado")).statusCode(400)
                .body("error.code", is("LOGYX-PAR-001")).body("error.field", is("value"));
        put(op, "commission_rate", Map.of("value", "ocho", "reason", "texto")).statusCode(400)
                .body("error.code", is("LOGYX-PAR-001"));
        put(op, "assignment_sla_hours", Map.of("value", "1.5", "reason", "no entero")).statusCode(400)
                .body("error.code", is("LOGYX-PAR-001"));
        put(op, "commission_rate", Map.of("value", "0.05")).statusCode(400).body("error.field", is("reason"));
        put(op, "commission_rate", Map.of("value", "0.05", "reason", "  ")).statusCode(400);
        put(op, "inexistente", Map.of("value", "1", "reason", "no existe")).statusCode(404);
        put(op, "commission_rate", Map.of("value", "0.05", "reason", "versión vieja", "expectedVersion", 999))
                .statusCode(409).body("error.code", is("LOGYX-PAR-004"));
        // Nada de lo anterior cambió el valor
        org.junit.jupiter.api.Assertions.assertNotEquals("0.5", valueOf(op, "commission_rate"));
    }

    @Test
    void elAvisoDeIncidenciasSiempreVaAntesDelIncumplimiento() {
        Session op = operator();
        String warn = valueOf(op, "incident_warn_hours");
        String breach = valueOf(op, "incident_breach_hours");
        // aviso ≥ incumplimiento → 409
        put(op, "incident_warn_hours", Map.of("value", String.valueOf(Integer.parseInt(breach) + 1), "reason", "incoherente"))
                .statusCode(409).body("error.code", is("LOGYX-PAR-003"));
        put(op, "incident_breach_hours", Map.of("value", warn, "reason", "incoherente")).statusCode(409)
                .body("error.code", is("LOGYX-PAR-003"));
        // coherente: se expone en las métricas para que la UI coloree con ellos
        try {
            put(op, "incident_breach_hours", Map.of("value", "30", "reason", "SLA de 30 h")).statusCode(200);
            put(op, "incident_warn_hours", Map.of("value", "10", "reason", "Aviso a las 10 h")).statusCode(200);
            as(op).get("/api/v1/operator/metrics").then().statusCode(200)
                    .body("data.incidentWarnHours", is(10)).body("data.incidentBreachHours", is(30));
        } finally {
            put(op, "incident_warn_hours", Map.of("value", warn, "reason", "Restaurar el valor de la prueba"));
            put(op, "incident_breach_hours", Map.of("value", breach, "reason", "Restaurar el valor de la prueba"));
        }
    }

    @Test
    void elSlaDeAsignacionSaleDelParametro() {
        Session op = operator();
        String before = valueOf(op, "assignment_sla_hours");
        try {
            put(op, "assignment_sla_hours", Map.of("value", "48", "reason", "Más margen en feriados")).statusCode(200);
            as(op).get("/api/v1/operator/sla-alerts").then().statusCode(200).body("data.slaHours", is(48));
            as(op).get("/api/v1/operator/metrics").then().statusCode(200).body("data.assignmentSlaHours", is(48));
        } finally {
            put(op, "assignment_sla_hours", Map.of("value", before, "reason", "Restaurar el valor de la prueba"));
        }
        as(op).get("/api/v1/operator/sla-alerts").then().body("data.slaHours", is(Integer.parseInt(before)));
    }
}
