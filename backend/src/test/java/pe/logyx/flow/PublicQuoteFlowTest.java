package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static pe.logyx.support.Api.*;

/** Cotizador público (sin JWT) y comisión: mismos números que el cálculo autenticado, IGV desglosado. */
@QuarkusTest
class PublicQuoteFlowTest {

    private static io.restassured.response.ValidatableResponse quote(Map<String, Object> body) {
        return given().contentType("application/json").body(body).post("/api/v1/public/pricing/quote").then();
    }

    @Test
    void cotizaSinCuentaConElMismoCalculoQueElCotizadorAutenticado() {
        Map<String, Object> body = Map.of("originCity", "LIMA", "destinationCity", "JULIACA", "weightKg", 3000,
                "cargoType", "GENERAL");
        var pub = quote(body).statusCode(200).extract();

        Session pyme = register("PYME", "Cotiza Pyme");
        var auth = as(pyme).body(body).post("/api/v1/pricing/calculate").then().statusCode(200).extract();
        for (String f : new String[]{"distanceKm", "fuelCost", "transportCost", "tollCost", "handlingFee",
                "suggestedPrice", "priceFloor"}) {
            Object a = auth.path("data." + f);
            Object b = pub.path("data." + f);
            assertEquals(a, b, f);
        }

        // km por carretera (no línea recta): Lima – Juliaca ≈ 1 300 km
        float km = pub.path("data.distanceKm");
        org.junit.jupiter.api.Assertions.assertTrue(km > 1200 && km < 1500, "km " + km);
        // IGV 18 % desglosado del sugerido (incluido en el precio) y la comisión vigente
        float suggested = pub.path("data.suggestedPrice");
        float net = pub.path("data.suggestedNetOfVat");
        float vat = pub.path("data.suggestedVat");
        org.junit.jupiter.api.Assertions.assertEquals(suggested, net + vat, 0.011f);
        org.junit.jupiter.api.Assertions.assertEquals(suggested / 1.18f, net, 0.011f);
        float vatRate = pub.path("data.vatRate");
        org.junit.jupiter.api.Assertions.assertEquals(0.18f, vatRate, 1e-6f);
        float rate = as(pyme).get("/api/v1/pricing/commission").then().statusCode(200).extract().path("data.commissionRate");
        float pubRate = pub.path("data.commissionRate");
        org.junit.jupiter.api.Assertions.assertEquals(rate, pubRate, 1e-6f);
    }

    @Test
    void validaLosDatosSinFiltrarNada() {
        quote(Map.of("originCity", "LIMA", "destinationCity", "LIMA", "weightKg", 1000, "cargoType", "GENERAL"))
                .statusCode(400).body("error.code", is("LOGYX-PRC-002"));
        quote(Map.of("originCity", "LIMA", "destinationCity", "BOGOTA", "weightKg", 1000, "cargoType", "GENERAL"))
                .statusCode(400).body("error.code", is("LOGYX-PRC-001"));
        quote(Map.of("originCity", "LIMA", "destinationCity", "CUSCO", "weightKg", 0, "cargoType", "GENERAL"))
                .statusCode(400).body("error.code", is("LOGYX-VAL-001")).body("error.field", is("weightKg"));
        quote(Map.of("originCity", "LIMA", "destinationCity", "CUSCO", "weightKg", 90000, "cargoType", "GENERAL"))
                .statusCode(400).body("error.field", is("weightKg"));
        quote(Map.of("originCity", "LIMA", "destinationCity", "CUSCO", "weightKg", 1000)).statusCode(400)
                .body("error.field", is("cargoType"));
        quote(Map.of("originCity", "LIMA", "destinationCity", "CUSCO", "weightKg", 1000, "cargoType", "NADA"))
                .statusCode(400);
    }

    @Test
    void laQuotaDeTipoDeCargaCambiaElPrecio() {
        float general = quote(Map.of("originCity", "LIMA", "destinationCity", "AREQUIPA", "weightKg", 5000,
                "cargoType", "GENERAL")).statusCode(200).extract().path("data.suggestedPrice");
        float hazardous = quote(Map.of("originCity", "LIMA", "destinationCity", "AREQUIPA", "weightKg", 5000,
                "cargoType", "HAZARDOUS")).statusCode(200).extract().path("data.suggestedPrice");
        org.junit.jupiter.api.Assertions.assertTrue(hazardous > general);
    }

    @Test
    void comisionYTerminosSonPublicosYElDesgloseEsParaAutenticados() {
        given().get("/api/v1/public/pricing/commission").then().statusCode(200)
                .body("data.paidBy", is("CARRIER")).body("data.vatRate", num(0.18))
                .body("data.commissionRate", greaterThanOrEqualTo(0f));
        given().get("/api/v1/public/legal").then().statusCode(200)
                .body("data.termsVersion", notNullValue()).body("data.termsStatus", is("DRAFT"));
        given().get("/api/v1/pricing/commission").then().statusCode(401);

        Session carrier = register("CARRIER", "Neto Carrier");
        var r = as(carrier).get("/api/v1/pricing/commission?amount=1180").then().statusCode(200).extract();
        float rate = r.path("data.commissionRate");
        float net = r.path("data.breakdown.carrierNet");
        float commission = r.path("data.breakdown.commission");
        float commissionVat = r.path("data.breakdown.commissionVat");
        org.junit.jupiter.api.Assertions.assertEquals(1180f, net + commission + commissionVat, 0.011f);
        // la comisión es sobre el valor de venta sin IGV (1 000) y lleva su propio IGV (18 %)
        org.junit.jupiter.api.Assertions.assertEquals(1000f * rate, commission, 0.011f);
        org.junit.jupiter.api.Assertions.assertEquals(commission * 0.18f, commissionVat, 0.011f);
        as(carrier).get("/api/v1/pricing/commission?amount=0").then().statusCode(400);
        as(carrier).get("/api/v1/pricing/commission").then().statusCode(200).body("data.breakdown", nullValue());
    }
}
