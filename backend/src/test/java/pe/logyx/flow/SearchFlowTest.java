package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static pe.logyx.support.Api.*;

/** Búsqueda global (Ctrl+K): por código, referencia, ruta y contraparte, siempre bajo la RLS del usuario. */
@QuarkusTest
class SearchFlowTest {

    private static final String SEARCH = "/api/v1/search";

    private static String publish(Session pyme, String to, String reference) {
        Map<String, Object> m = new HashMap<>(Map.of("originCity", "LIMA", "originAddress", "Av. A 1",
                "destinationCity", to, "destinationAddress", "Jr. B 2", "cargoType", "GENERAL",
                "cargoDescription", "Mercadería", "weightKg", 900, "pickupDate", LocalDate.now().plusDays(3).toString()));
        if (reference != null) m.put("customerReference", reference);
        return as(pyme).body(m).post("/api/v1/shipment-requests").then().statusCode(201).extract().path("data.id");
    }

    private static String codeOf(Session s, String id) {
        return as(s).get("/api/v1/shipment-requests/" + id).path("data.code");
    }

    @Test
    void encuentraPorCodigoReferenciaYRuta() {
        Session pyme = register("PYME", "Search Pyme");
        String id = publish(pyme, "JULIACA", "PED-998877");
        publish(pyme, "PUNO", null);
        String code = codeOf(pyme, id);

        as(pyme).get(SEARCH + "?q=" + code).then().statusCode(200).body("data", hasSize(1),
                "data[0].type", is("REQUEST"), "data[0].id", is(id), "data[0].code", is(code),
                "data[0].title", is("Lima → Juliaca"), "data[0].href", is("/pyme/solicitudes/" + id));
        // Tal como se dicta: minúsculas, con espacio, sin prefijo
        as(pyme).get(SEARCH + "?q=" + code.toLowerCase().replace("-", " ")).then().body("data[0].id", is(id));
        as(pyme).get(SEARCH + "?q=" + code.substring(3)).then().body("data[0].id", is(id));
        as(pyme).get(SEARCH + "?q=ped-9988").then().body("data", hasSize(1), "data[0].id", is(id));
        as(pyme).get(SEARCH + "?q=lima juliaca").then().body("data", hasSize(1), "data[0].id", is(id));
        as(pyme).get(SEARCH + "?q=lima").then().body("data", hasSize(2));
        as(pyme).get(SEARCH + "?q=lima&types=SHIPMENT").then().body("data", hasSize(0));
    }

    @Test
    void validaLaConsulta() {
        Session pyme = register("PYME", "Search Valida");
        as(pyme).get(SEARCH + "?q=a").then().statusCode(400).body("error.field", is("q"));
        as(pyme).get(SEARCH).then().statusCode(400);
        as(pyme).get(SEARCH + "?q=lima&types=foo").then().statusCode(400).body("error.field", is("types"));
        given().get(SEARCH + "?q=lima").then().statusCode(401);
        // Comodines y SQL del usuario son texto
        as(pyme).get(SEARCH + "?q=%25%25").then().statusCode(200).body("data", hasSize(0));
        as(pyme).get(SEARCH + "?q=' or '1'='1").then().statusCode(200).body("data", hasSize(0));
    }

    @Test
    void soloDevuelveLoQueElUsuarioPuedeVer() {
        Session a = register("PYME", "Search Org A");
        Session b = register("PYME", "Search Org B");
        String idA = publish(a, "TACNA", "SECRETA-A1");
        String codeA = codeOf(a, idA);
        // Otra PYME no ve la solicitud, ni por código ni por referencia ni por ruta exacta
        as(b).get(SEARCH + "?q=" + codeA).then().body("data", hasSize(0));
        as(b).get(SEARCH + "?q=SECRETA-A1").then().body("data", hasSize(0));
        as(b).get(SEARCH + "?q=lima tacna").then().body("data", hasSize(0));
        // Un transportista ve la publicada por código (marketplace) pero no su referencia privada
        Session carrier = register("CARRIER", "Search Carrier");
        as(carrier).get(SEARCH + "?q=" + codeA).then().body("data", hasSize(1), "data[0].id", is(idA),
                "data[0].href", startsWith("/transportista/marketplace?origen=LIMA&destino=TACNA"));
        as(carrier).get(SEARCH + "?q=SECRETA-A1").then().body("data", hasSize(0));
    }

    @Test
    void enviosYContrapartesConRelacion() {
        var aw = Flows.awarded("Search Flujo");
        String code = codeOf(aw.pyme(), aw.requestId());
        // Envío: la PYME y el transportista lo hallan por el código de la solicitud
        as(aw.pyme()).get(SEARCH + "?q=" + code + "&types=SHIPMENT").then().body("data", hasSize(1),
                "data[0].id", is(aw.shipmentId()), "data[0].href", is("/pyme/envios?id=" + aw.shipmentId()));
        as(aw.carrier()).get(SEARCH + "?q=" + code + "&types=SHIPMENT").then().body("data", hasSize(1),
                "data[0].href", is("/transportista/envios?id=" + aw.shipmentId()));
        // Contrapartes: la PYME encuentra al transportista con el que tuvo una relación, y viceversa
        as(aw.pyme()).get(SEARCH + "?q=Carrier Search&types=COUNTERPART").then().body("data", hasSize(1),
                "data[0].type", is("COUNTERPART"), "data[0].subtitle", startsWith("Transportista"));
        as(aw.carrier()).get(SEARCH + "?q=Pyme Search&types=COUNTERPART").then().body("data", hasSize(1));
        // Sin relación no hay directorio: otra PYME no encuentra a ese transportista
        Session stranger = register("PYME", "Search Extraña");
        as(stranger).get(SEARCH + "?q=Carrier Search&types=COUNTERPART").then().body("data", hasSize(0));
        // El conductor no usa la búsqueda
        as(aw.driver()).get(SEARCH + "?q=" + code).then().statusCode(403);
    }

    @Test
    void elOperadorVeTodoYBuscaPorRuc() {
        Session pyme = register("PYME", "Search Operador Pyme");
        String id = publish(pyme, "ABANCAY", "REF-OP-1");
        String code = codeOf(pyme, id);
        var op = operatorSession();
        as(op).get(SEARCH + "?q=" + code).then().body("data[0].id", is(id), "data[0].href", is("/operador/solicitudes/" + id));
        as(op).get(SEARCH + "?q=REF-OP-1").then().body("data", hasSize(1));
        String ruc = as(pyme).get("/api/v1/organizations/me").path("data.ruc");
        as(op).get(SEARCH + "?q=" + ruc + "&types=COUNTERPART").then().body("data", hasSize(1),
                "data[0].id", is(pyme.orgId()));
    }

    private static Session operatorSession() {
        var auth = io.quarkus.arc.Arc.container().instance(pe.logyx.identity.application.AuthService.class).get();
        var r = auth.provisionOperator("Search Operador " + System.nanoTime() % 100000, newRuc(), "Op",
                "op" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

}
