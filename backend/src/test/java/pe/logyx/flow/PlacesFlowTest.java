package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Libreta de direcciones (V17): CRUD por organización, archivo, orden por uso y aislamiento. */
@QuarkusTest
class PlacesFlowTest {

    private static final String PLACES = "/api/v1/places";

    private static Map<String, Object> place(String label, String city) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", label);
        m.put("city", city);
        m.put("address", "Av. Industrial 450");
        m.put("contactName", "Rosa Huanca");
        m.put("contactPhone", "+51 912 345 678");
        m.put("openingHours", "Lun–Vie 8:00–17:00");
        m.put("windowFrom", "08:00");
        m.put("windowTo", "17:00");
        m.put("instructions", "Tocar el timbre del portón");
        m.put("kind", "PICKUP");
        return m;
    }

    @Test
    void altaEdicionYArchivo() {
        Session pyme = register("PYME", "Places Pyme");
        String id = as(pyme).body(place("Almacén Callao", "LIMA")).post(PLACES).then().statusCode(201)
                .body("data.label", is("Almacén Callao"), "data.contactPhone", is("912345678"),
                        "data.windowFrom", is("08:00"), "data.kind", is("PICKUP"), "data.archived", is(false))
                .extract().path("data.id");
        as(pyme).get(PLACES).then().body("data", hasSize(1));
        as(pyme).get(PLACES + "/" + id).then().statusCode(200).body("data.instructions", is("Tocar el timbre del portón"));

        var edit = place("Almacén Callao Norte", "LIMA");
        edit.put("kind", "BOTH");
        as(pyme).body(edit).patch(PLACES + "/" + id).then().statusCode(200).body("data.label", is("Almacén Callao Norte"),
                "data.kind", is("BOTH"));

        as(pyme).post(PLACES + "/" + id + "/archive").then().statusCode(200).body("data.archived", is(true));
        as(pyme).get(PLACES).then().body("data", hasSize(0));
        as(pyme).get(PLACES + "?includeArchived=true").then().body("data", hasSize(1));
        as(pyme).body(edit).patch(PLACES + "/" + id).then().statusCode(409).body("error.code", is("LOGYX-PLC-002"));
        as(pyme).post(PLACES + "/" + id + "/restore").then().statusCode(200).body("data.archived", is(false));
        as(pyme).get(PLACES).then().body("data", hasSize(1));
    }

    @Test
    void validaLosCamposConElNombreDelCampo() {
        Session pyme = register("PYME", "Places Valida");
        var bad = place("", "LIMA");
        as(pyme).body(bad).post(PLACES).then().statusCode(400).body("error.field", is("label"));
        bad = place("X", "MARTE");
        as(pyme).body(bad).post(PLACES).then().statusCode(400).body("error.code", is("LOGYX-PRC-001"));
        bad = place("X", "LIMA");
        bad.put("contactPhone", "123");
        as(pyme).body(bad).post(PLACES).then().statusCode(400).body("error.code", is("LOGYX-VAL-002"),
                "error.field", is("contactPhone"));
        bad = place("X", "LIMA");
        bad.put("windowTo", "07:00");
        as(pyme).body(bad).post(PLACES).then().statusCode(400).body("error.code", is("LOGYX-PLC-001"));
        // RN07 no aplica a la libreta privada: el teléfono en las instrucciones es válido
        var ok = place("Con teléfono", "LIMA");
        ok.put("instructions", "Llamar al 987654321 al llegar");
        as(pyme).body(ok).post(PLACES).then().statusCode(201);
    }

    @Test
    void filtraPorCiudadTipoYTexto() {
        Session pyme = register("PYME", "Places Filtros");
        var a = place("Almacén Lima", "LIMA");
        a.put("kind", "PICKUP");
        var b = place("Tienda Juliaca", "JULIACA");
        b.put("kind", "DELIVERY");
        var c = place("Depósito mixto", "LIMA");
        c.put("kind", "BOTH");
        for (var m : java.util.List.of(a, b, c)) as(pyme).body(m).post(PLACES).then().statusCode(201);
        as(pyme).get(PLACES + "?city=LIMA").then().body("data", hasSize(2));
        as(pyme).get(PLACES + "?kind=PICKUP").then().body("data.label", containsInAnyOrder("Almacén Lima", "Depósito mixto"));
        as(pyme).get(PLACES + "?kind=DELIVERY&city=JULIACA").then().body("data", hasSize(1));
        as(pyme).get(PLACES + "?q=tienda").then().body("data", hasSize(1));
        as(pyme).get(PLACES + "?q=rosa").then().body("data", hasSize(3));        // por contacto
        as(pyme).get(PLACES + "?kind=XX").then().statusCode(400).body("error.field", is("kind"));
    }

    @Test
    void laUsadaMasRecientementeVaPrimeroAlPublicar() {
        Session pyme = register("PYME", "Places Uso");
        String a = as(pyme).body(place("Primera", "LIMA")).post(PLACES).then().extract().path("data.id");
        String b = as(pyme).body(place("Segunda", "LIMA")).post(PLACES).then().extract().path("data.id");
        as(pyme).get(PLACES).then().body("data[0].label", is("Primera"));      // sin uso: por nombre
        as(pyme).body(Map.of("originCity", "LIMA", "originAddress", "Av. Industrial 450", "destinationCity", "JULIACA",
                        "destinationAddress", "Jr. X 1", "cargoType", "GENERAL", "cargoDescription", "Cajas",
                        "weightKg", 500, "pickupDate", LocalDate.now().plusDays(2).toString(), "originPlaceId", b))
                .post("/api/v1/shipment-requests").then().statusCode(201);
        as(pyme).get(PLACES).then().body("data[0].id", is(b), "data[0].lastUsedAt", notNullValue(),
                "data[1].id", is(a));
    }

    @Test
    void cadaOrganizacionVeSoloLasSuyas() {
        Session a = register("PYME", "Places Org A");
        Session b = register("PYME", "Places Org B");
        String id = as(a).body(place("Privada de A", "LIMA")).post(PLACES).then().statusCode(201).extract().path("data.id");
        as(b).get(PLACES).then().body("data", hasSize(0));
        as(b).get(PLACES + "/" + id).then().statusCode(404);
        as(b).body(place("Robada", "LIMA")).patch(PLACES + "/" + id).then().statusCode(404);
        as(b).post(PLACES + "/" + id + "/archive").then().statusCode(404);
        // Publicar con el id de otra organización no la toca ni falla
        as(b).body(Map.of("originCity", "LIMA", "originAddress", "Av. B 1", "destinationCity", "JULIACA",
                        "destinationAddress", "Jr. B 2", "cargoType", "GENERAL", "cargoDescription", "Cajas",
                        "weightKg", 500, "pickupDate", LocalDate.now().plusDays(2).toString(), "originPlaceId", id))
                .post("/api/v1/shipment-requests").then().statusCode(201);
        as(a).get(PLACES + "/" + id).then().body("data.lastUsedAt", nullValue());
        // Un transportista no usa la libreta de la PYME
        as(register("CARRIER", "Places Carrier")).get(PLACES).then().statusCode(403);
    }
}
