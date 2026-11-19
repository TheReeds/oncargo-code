package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Zona A (V17): código legible, referencia propia, valor declarado, contactos privados hasta adjudicar,
 * idempotencia al publicar, aviso de duplicado, filtros en el servidor, conteos y exportación CSV.
 */
@QuarkusTest
class RequestDetailsFlowTest {

    private static final String REQ = "/api/v1/shipment-requests";

    private static Map<String, Object> body(String from, String to, int kg, int days) {
        Map<String, Object> m = new HashMap<>();
        m.put("originCity", from);
        m.put("originAddress", "Av. Origen 100");
        m.put("destinationCity", to);
        m.put("destinationAddress", "Jr. Destino 200");
        m.put("cargoType", "GENERAL");
        m.put("cargoDescription", "Rollos de tela");
        m.put("weightKg", kg);
        m.put("pickupDate", LocalDate.now().plusDays(days).toString());
        return m;
    }

    private static Map<String, Object> withDetails(Map<String, Object> m) {
        m.put("customerReference", "OC 4500123");
        m.put("declaredValue", 18500.50);
        m.put("pickupContactName", "María Quispe");
        m.put("pickupContactPhone", "+51 987 654 321");
        m.put("pickupWindowFrom", "08:00");
        m.put("pickupWindowTo", "12:30");
        m.put("pickupInstructions", "Muelle 2, preguntar por almacén");
        m.put("deliveryContactName", "Luis Mamani");
        m.put("deliveryContactPhone", "951234567");
        m.put("deliveryWindowFrom", "14:00");
        m.put("deliveryWindowTo", "18:00");
        m.put("deliveryInstructions", "Portón verde");
        return m;
    }

    @Test
    void publicaConCodigoLegibleYDatosOpcionales() {
        Session pyme = register("PYME", "Detalles Pyme");
        var r = as(pyme).body(withDetails(body("LIMA", "JULIACA", 1500, 3))).post(REQ).then().statusCode(201)
                .body("data.code", matchesPattern("OC-[2-9A-HJKMNP-TV-Z]{5}"),
                        "data.customerReference", is("OC 4500123"), "data.declaredValue", num(18500.50),
                        "data.pickupContactPhone", is("987654321"), "data.pickupWindowFrom", is("08:00"),
                        "data.pickupWindowTo", is("12:30"), "data.deliveryInstructions", is("Portón verde"))
                .extract();
        String id = r.path("data.id");
        // El detalle devuelve lo mismo a la PYME dueña
        as(pyme).get(REQ + "/" + id).then().statusCode(200).body("data.code", is((String) r.path("data.code")),
                "data.deliveryContactName", is("Luis Mamani"));
        // Sin datos opcionales también se publica, con código y sin contactos
        as(pyme).body(body("LIMA", "PUNO", 900, 3)).post(REQ).then().statusCode(201)
                .body("data.code", notNullValue(), "data.customerReference", nullValue(),
                        "data.pickupContactName", nullValue(), "data.declaredValue", nullValue());
    }

    @Test
    void codigosUnicosEntreSolicitudes() {
        Session pyme = register("PYME", "Codigos Pyme");
        var codes = new java.util.HashSet<String>();
        for (int i = 0; i < 12; i++) {
            codes.add(as(pyme).body(body("LIMA", "JULIACA", 500 + i, 4)).post(REQ).then().statusCode(201)
                    .extract().path("data.code"));
        }
        assertEquals(12, codes.size());
    }

    @Test
    void validaTelefonoVentanaYValorConElCampo() {
        Session pyme = register("PYME", "Valida Pyme");
        var bad = body("LIMA", "JULIACA", 800, 3);
        bad.put("pickupContactPhone", "12345");
        as(pyme).body(bad).post(REQ).then().statusCode(400).body("error.code", is("LOGYX-VAL-002"),
                "error.field", is("pickupContactPhone"));
        bad = body("LIMA", "JULIACA", 800, 3);
        bad.put("deliveryWindowFrom", "18:00");
        bad.put("deliveryWindowTo", "09:00");
        as(pyme).body(bad).post(REQ).then().statusCode(400).body("error.code", is("LOGYX-REQ-006"),
                "error.field", is("deliveryWindowTo"));
        bad = body("LIMA", "JULIACA", 800, 3);
        bad.put("pickupWindowFrom", "8:00");
        as(pyme).body(bad).post(REQ).then().statusCode(400).body("error.field", is("pickupWindowFrom"));
        bad = body("LIMA", "JULIACA", 800, 3);
        bad.put("declaredValue", 0);
        as(pyme).body(bad).post(REQ).then().statusCode(400).body("error.field", is("declaredValue"));
        bad = body("LIMA", "JULIACA", 800, 3);
        bad.put("customerReference", "x".repeat(41));
        as(pyme).body(bad).post(REQ).then().statusCode(400).body("error.field", is("customerReference"));
    }

    @Test
    void contactosPrivadosHastaAdjudicar() {
        Session pyme = register("PYME", "Privado Pyme");
        Session winner = register("CARRIER", "Privado Ganador");
        Session loser = register("CARRIER", "Privado Perdedor");
        String vWin = vehicle(winner, plate(), 8000), vLose = vehicle(loser, plate(), 8000);
        String id = as(pyme).body(withDetails(body("LIMA", "JULIACA", 1500, 3))).post(REQ).then().statusCode(201)
                .extract().path("data.id");

        // Transportista (marketplace y detalle): ve código y valor declarado, nada privado
        for (Session carrier : new Session[]{winner, loser}) {
            as(carrier).get(REQ + "/" + id).then().statusCode(200)
                    .body("data.code", notNullValue(), "data.declaredValue", num(18500.50),
                            "data.customerReference", nullValue(), "data.pickupContactName", nullValue(),
                            "data.pickupContactPhone", nullValue(), "data.pickupInstructions", nullValue(),
                            "data.deliveryContactName", nullValue(), "data.deliveryWindowFrom", nullValue());
            String list = as(carrier).get(REQ + "?size=100").then().statusCode(200).extract().asString();
            assertFalse(list.contains("OC 4500123") || list.contains("987654321") || list.contains("María Quispe"), list);
        }
        String bidWin = bid(winner, id, vWin, 200), bidLose = bid(loser, id, vLose, 400);
        // Quien ofertó sigue sin verlos
        as(loser).get(REQ + "/my-bids").then().statusCode(200).body("data[0].pickupContactName", nullValue(),
                "data[0].customerReference", nullValue());
        // El chat sigue bajo RN07
        as(pyme).post("/api/v1/bids/" + bidWin + "/accept").then().statusCode(200);
        // Tras adjudicar: el ganador los ve; el perdedor (que ofertó) no
        as(winner).get(REQ + "/" + id).then().statusCode(200).body("data.customerReference", is("OC 4500123"),
                "data.pickupContactName", is("María Quispe"), "data.pickupContactPhone", is("987654321"),
                "data.deliveryInstructions", is("Portón verde"));
        as(loser).get(REQ + "/" + id).then().statusCode(200).body("data.pickupContactName", nullValue(),
                "data.customerReference", nullValue(), "data.status", is("MATCHED"));
        assertNotNull(bidLose);
    }

    @Test
    void idempotencyKeyEvitaLaDobleSolicitud() {
        Session pyme = register("PYME", "Idem Pyme");
        String key = UUID.randomUUID().toString();
        var first = as(pyme).header("Idempotency-Key", key).body(body("LIMA", "CUSCO", 700, 5)).post(REQ).then()
                .statusCode(201).header("Idempotent-Replayed", nullValue()).extract();
        var second = as(pyme).header("Idempotency-Key", key).body(body("LIMA", "CUSCO", 700, 5)).post(REQ).then()
                .statusCode(201).header("Idempotent-Replayed", is("true")).extract();
        assertEquals((String) first.path("data.id"), second.path("data.id"));
        assertEquals((String) first.path("data.code"), second.path("data.code"));
        as(pyme).get(REQ + "/mine?destinationCity=CUSCO").then().body("data", hasSize(1));
        // Otra clave sí crea otra; sin clave, comportamiento de siempre
        as(pyme).header("Idempotency-Key", UUID.randomUUID().toString()).body(body("LIMA", "CUSCO", 700, 5)).post(REQ)
                .then().statusCode(201);
        as(pyme).body(body("LIMA", "CUSCO", 700, 5)).post(REQ).then().statusCode(201);
        as(pyme).get(REQ + "/mine?destinationCity=CUSCO").then().body("data", hasSize(3));
        as(pyme).header("Idempotency-Key", "corta").body(body("LIMA", "CUSCO", 700, 5)).post(REQ).then()
                .statusCode(400).body("error.field", is("Idempotency-Key"));
        // La clave es por organización: otra PYME con la misma clave crea la suya
        Session other = register("PYME", "Idem Otra");
        var third = as(other).header("Idempotency-Key", key).body(body("LIMA", "CUSCO", 700, 5)).post(REQ).then()
                .statusCode(201).header("Idempotent-Replayed", nullValue()).extract();
        assertNotEquals((String) first.path("data.id"), third.path("data.id"));
    }

    @Test
    void dobleClicSimultaneoCreaUnaSola() throws Exception {
        Session pyme = register("PYME", "Idem Paralela");
        String key = UUID.randomUUID().toString();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) {
                tasks.add(pool.submit(() -> as(pyme).header("Idempotency-Key", key).body(body("LIMA", "TACNA", 650, 6))
                        .post(REQ).then().statusCode(201).extract().path("data.id")));
            }
            var ids = new java.util.HashSet<String>();
            for (var t : tasks) ids.add(t.get());
            assertEquals(1, ids.size(), "cuatro peticiones simultáneas con la misma clave deben dar una sola solicitud");
        } finally {
            pool.shutdownNow();
        }
        as(pyme).get(REQ + "/mine?destinationCity=TACNA").then().body("data", hasSize(1));
    }

    @Test
    void avisaDeDuplicadoReciente() {
        Session pyme = register("PYME", "Similar Pyme");
        String date = LocalDate.now().plusDays(4).toString();
        as(pyme).body(body("LIMA", "AREQUIPA", 1200, 4)).post(REQ).then().statusCode(201);
        String q = "?originCity=LIMA&destinationCity=AREQUIPA&cargoType=GENERAL&weightKg=1200&pickupDate=" + date;
        as(pyme).get(REQ + "/mine/similar" + q).then().statusCode(200).body("data", hasSize(1),
                "data[0].code", notNullValue());
        // Otro peso o otra fecha: no es duplicado
        as(pyme).get(REQ + "/mine/similar" + q.replace("1200", "1300")).then().body("data", hasSize(0));
        as(pyme).get(REQ + "/mine/similar" + q.replace(date, LocalDate.now().plusDays(5).toString())).then()
                .body("data", hasSize(0));
        // Otra organización no ve los de esta
        Session other = register("PYME", "Similar Otra");
        as(other).get(REQ + "/mine/similar" + q).then().body("data", hasSize(0));
        as(pyme).get(REQ + "/mine/similar?originCity=LIMA").then().statusCode(400);
    }

    @Test
    void filtrosOrdenYPaginacionEnElServidor() {
        Session pyme = register("PYME", "Filtros Pyme");
        String a = as(pyme).body(withDetails(body("LIMA", "JULIACA", 3000, 2))).post(REQ).then().statusCode(201)
                .extract().path("data.id");
        String codeA = as(pyme).get(REQ + "/" + a).path("data.code");
        as(pyme).body(body("LIMA", "PUNO", 1000, 6)).post(REQ).then().statusCode(201);
        String c = as(pyme).body(body("AREQUIPA", "CUSCO", 2000, 4)).post(REQ).then().statusCode(201).extract().path("data.id");
        as(pyme).post(REQ + "/" + c + "/cancel").then().statusCode(200);

        as(pyme).get(REQ + "/mine").then().body("data", hasSize(3));
        as(pyme).get(REQ + "/mine?page=0&size=2").then().body("data", hasSize(2), "meta.total", is(3), "meta.size", is(2));
        as(pyme).get(REQ + "/mine?page=1&size=2").then().body("data", hasSize(1), "meta.page", is(1));
        as(pyme).get(REQ + "/mine?status=CANCELLED").then().body("data", hasSize(1), "data[0].id", is(c));
        as(pyme).get(REQ + "/mine?status=PUBLISHED&originCity=LIMA").then().body("data", hasSize(2));
        as(pyme).get(REQ + "/mine?destinationCity=puno").then().body("data", hasSize(1));
        as(pyme).get(REQ + "/mine?fromDate=" + LocalDate.now().plusDays(5) + "&toDate=" + LocalDate.now().plusDays(7))
                .then().body("data", hasSize(1), "data[0].destinationCity", is("PUNO"));
        // Orden
        as(pyme).get(REQ + "/mine?sort=pickup").then().body("data[0].id", is(a));
        as(pyme).get(REQ + "/mine?sort=price_desc").then().body("data[0].id", is(a));
        as(pyme).get(REQ + "/mine?sort=price_asc").then().body("data[2].id", is(a));
        // Texto: código (con o sin guion, en minúsculas), referencia propia, ciudad y descripción
        as(pyme).get(REQ + "/mine?q=" + codeA.toLowerCase()).then().body("data", hasSize(1), "data[0].id", is(a));
        as(pyme).get(REQ + "/mine?q=" + codeA.substring(3)).then().body("data", hasSize(1), "data[0].id", is(a));
        as(pyme).get(REQ + "/mine?q=4500123").then().body("data", hasSize(1), "data[0].id", is(a));
        as(pyme).get(REQ + "/mine?q=cusco").then().body("data", hasSize(1), "data[0].id", is(c));
        as(pyme).get(REQ + "/mine?q=rollos").then().body("data", hasSize(3));
        as(pyme).get(REQ + "/mine?q=%25").then().body("data", hasSize(0));   // el % del usuario no es comodín
        // Validaciones con el campo
        as(pyme).get(REQ + "/mine?status=X").then().statusCode(400).body("error.field", is("status"));
        as(pyme).get(REQ + "/mine?sort=zzz").then().statusCode(400).body("error.field", is("sort"));
        as(pyme).get(REQ + "/mine?originCity=MARTE").then().statusCode(400).body("error.field", is("originCity"));
        as(pyme).get(REQ + "/mine?fromDate=2026-12-02&toDate=2026-12-01").then().statusCode(400);
        // Conteos
        as(pyme).get(REQ + "/mine/counts").then().statusCode(200).body("data.all", is(3), "data.published", is(2),
                "data.matched", is(0), "data.cancelled", is(1), "data.withBids", is(0));
    }

    @Test
    void conOfertasPorElegirYOrdenPorOfertas() {
        Session pyme = register("PYME", "Ofertas Filtro Pyme");
        Session carrier = register("CARRIER", "Ofertas Filtro Carrier");
        String v = vehicle(carrier, plate(), 8000);
        String withBid = as(pyme).body(body("LIMA", "JULIACA", 1000, 3)).post(REQ).then().statusCode(201).extract().path("data.id");
        String without = as(pyme).body(body("LIMA", "PUNO", 1000, 3)).post(REQ).then().statusCode(201).extract().path("data.id");
        bid(carrier, withBid, v, 100);
        as(pyme).get(REQ + "/mine?withBids=true").then().body("data", hasSize(1), "data[0].id", is(withBid));
        as(pyme).get(REQ + "/mine?sort=bids").then().body("data[0].id", is(withBid), "data[1].id", is(without));
        as(pyme).get(REQ + "/mine/counts").then().body("data.withBids", is(1));
        as(pyme).get(REQ + "/mine?withBids=true&page=0&size=5").then().body("meta.total", is(1));
    }

    @Test
    void exportaCsvConLosMismosFiltros() {
        Session pyme = register("PYME", "Csv Pyme");
        var m = withDetails(body("LIMA", "JULIACA", 3000, 2));
        m.put("customerReference", "=HYPERLINK(\"http://x\")");
        as(pyme).body(m).post(REQ).then().statusCode(201);
        as(pyme).body(body("LIMA", "PUNO", 1000, 6)).post(REQ).then().statusCode(201);
        var resp = as(pyme).get(REQ + "/mine/export?destinationCity=JULIACA").then().statusCode(200)
                .header("Content-Type", containsString("text/csv"))
                .header("Content-Disposition", containsString("solicitudes-")).extract();
        String csv = new String(resp.asByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿Código;Referencia;Origen;Destino"), csv);
        String[] lines = csv.split("\r\n");
        assertEquals(2, lines.length, csv);                     // cabecera + 1 fila (filtro respetado)
        assertTrue(lines[1].contains(";Lima;Juliaca;"), lines[1]);
        assertTrue(lines[1].contains("'=HYPERLINK("), "debe neutralizar la fórmula: " + lines[1]);
        assertTrue(lines[1].contains(";Publicada;"), lines[1]);
        // Otra PYME exporta solo lo suyo
        Session other = register("PYME", "Csv Otra");
        String empty = new String(as(other).get(REQ + "/mine/export").then().statusCode(200).extract().asByteArray(),
                StandardCharsets.UTF_8);
        assertEquals(1, empty.split("\r\n").length);
        as(register("CARRIER", "Csv Carrier")).get(REQ + "/mine/export").then().statusCode(403);
    }

    @Test
    void republicarConservaLosDatosDeLaSolicitud() {
        Session pyme = register("PYME", "Republica Pyme");
        String id = as(pyme).body(withDetails(body("LIMA", "JULIACA", 1500, 3))).post(REQ).then().statusCode(201)
                .extract().path("data.id");
        as(pyme).post(REQ + "/" + id + "/cancel").then().statusCode(200);
        JsonPath again = as(pyme).body(Map.of()).post(REQ + "/" + id + "/republish").then().statusCode(201).extract().jsonPath();
        assertEquals("OC 4500123", again.getString("data.customerReference"));
        assertEquals("987654321", again.getString("data.pickupContactPhone"));
        assertNotEquals(as(pyme).get(REQ + "/" + id).path("data.code"), again.getString("data.code"));
    }
}
