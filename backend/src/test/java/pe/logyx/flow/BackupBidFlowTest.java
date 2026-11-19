package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api.Session;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/** Segunda oferta (2.9, V21): las ofertas que RN05 rechaza quedan de respaldo y se pueden adjudicar si el envío se cae en 24 h. */
@QuarkusTest
class BackupBidFlowTest {

    @Inject SystemTx systemTx;
    @Inject EntityManager em;

    /** PYME con una solicitud, dos transportistas que ofertan y la adjudicación al primero. */
    private record Scenario(Session pyme, Session first, Session second, String requestId, String firstBid,
                            String secondBid, String shipmentId) {}

    private Scenario awarded(String tag) {
        Session pyme = register("PYME", "Respaldo Pyme " + tag);
        Session first = register("CARRIER", "Primero SRL " + tag);
        Session second = register("CARRIER", "Segundo SRL " + tag);
        String requestId = publish(pyme, 2000).statusCode(201).extract().path("data.id");
        String firstBid = bid(first, requestId, vehicle(first, plate(), 8000), 100);
        String secondBid = bid(second, requestId, vehicle(second, plate(), 8000), 300);
        String shipmentId = as(pyme).post("/api/v1/bids/" + firstBid + "/accept").then().statusCode(200)
                .extract().path("data.shipmentId");
        return new Scenario(pyme, first, second, requestId, firstBid, secondBid, shipmentId);
    }

    private static String backups(String requestId) { return "/api/v1/shipment-requests/" + requestId + "/backup-bids"; }

    @Test
    void laSegundaOfertaQuedaDeRespaldoPeroNoCorrespondeMientrasElEnvioSigaVivo() {
        Scenario s = awarded("vivo");
        as(s.pyme()).get(backups(s.requestId())).then().statusCode(200).body("data.eligible", is(false),
                "data.blockerCode", is("LOGYX-BCK-001"), "data.items", hasSize(1),
                "data.items[0].rank", is(1), "data.items[0].state", is("AVAILABLE"),
                "data.items[0].bid.id", is(s.secondBid()), "data.items[0].bid.carrierName", containsString("Segundo SRL"));
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/request").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-001"));
        // La oferta rechazada sigue figurando como rechazada para quien la hizo (el respaldo no cambia su estado)
        as(s.pyme()).get("/api/v1/shipment-requests/" + s.requestId() + "/bids").then()
                .body("data.find { it.id == '" + s.secondBid() + "' }.status", is("REJECTED"));
    }

    @Test
    void elTransportistaDeRespaldoNoSabeQueLoEsHastaQueLePidenConfirmar() {
        Scenario s = awarded("sellado");
        as(s.second()).get("/api/v1/bids/backup-requests").then().statusCode(200).body("data", hasSize(0));
        as(s.second()).post("/api/v1/bids/" + s.secondBid() + "/backup/confirm").then().statusCode(404);
        as(s.second()).get("/api/v1/shipment-requests/" + s.requestId() + "/backup-bids").then().statusCode(403);
        as(s.first()).get("/api/v1/bids/backup-requests").then().body("data", hasSize(0));
        // Una PYME ajena no ve las ofertas de respaldo
        as(register("PYME", "Respaldo Ajena")).get(backups(s.requestId())).then().statusCode(404);
    }

    @Test
    void siElEnvioSeCancelaLaPymeAdjudicaALaSegundaOfertaConLaConfirmacionDelTransportista() {
        Scenario s = awarded("flujo");
        as(s.pyme()).body(Map.of("reason", "El transportista no llegó al recojo")).post("/api/v1/shipments/" + s.shipmentId() + "/cancel")
                .then().statusCode(200);
        as(s.pyme()).get(backups(s.requestId())).then().body("data.eligible", is(true), "data.blockerCode", nullValue(),
                "data.deadline", notNullValue(), "data.items", hasSize(1));

        // 1) La PYME pide confirmar → aviso al transportista, que ya ve el pedido (y solo el suyo)
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/request").then().statusCode(200)
                .body("data.state", is("REQUESTED"));
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/request").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-005"));
        as(s.second()).get("/api/v1/notifications").then().body("data.find { it.type == 'BACKUP_REQUESTED' }.relatedId",
                is(s.secondBid()));
        as(s.second()).get("/api/v1/bids/backup-requests").then().body("data", hasSize(1),
                "data[0].bidId", is(s.secondBid()), "data[0].state", is("REQUESTED"),
                "data[0].requesterName", containsString("Respaldo Pyme"), "data[0].requestCode", startsWith("OC-"));
        // Sin confirmación no se adjudica
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/award").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-004"));
        // Solo el transportista de esa oferta responde
        as(s.first()).post("/api/v1/bids/" + s.secondBid() + "/backup/confirm").then().statusCode(404);
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/confirm").then().statusCode(403);

        // 2) Confirma → aviso a la PYME
        as(s.second()).post("/api/v1/bids/" + s.secondBid() + "/backup/confirm").then().statusCode(200)
                .body("data.state", is("CONFIRMED"));
        as(s.pyme()).get("/api/v1/notifications").then().body("data.find { it.type == 'BACKUP_RESPONDED' }.title",
                is("Confirmó la segunda oferta"));
        as(s.second()).post("/api/v1/bids/" + s.secondBid() + "/backup/decline").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-005"));

        // 3) La PYME adjudica: nuevo envío, la solicitud vuelve a MATCHED y la oferta queda aceptada
        String newShipment = as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/award").then().statusCode(200)
                .body("data.acceptedBid.status", is("ACCEPTED")).extract().path("data.shipmentId");
        assertNotEquals(s.shipmentId(), newShipment);
        as(s.pyme()).get("/api/v1/shipment-requests/" + s.requestId()).then().body("data.status", is("MATCHED"),
                "data.cancelReason", nullValue());
        as(s.pyme()).get("/api/v1/shipments/" + newShipment).then().statusCode(200)
                .body("data.status", is("PENDING"), "data.carrierName", containsString("Segundo SRL"));
        as(s.second()).get("/api/v1/shipments/" + newShipment).then().statusCode(200);
        as(s.first()).get("/api/v1/shipments/" + newShipment).then().statusCode(404);
        as(s.second()).get("/api/v1/notifications").then().body("data.findAll { it.type == 'BID_ACCEPTED' }", hasSize(1));
        // Ya adjudicada de nuevo: no se repite
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/award").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-001"));
    }

    @Test
    void siElTransportistaDeclinaLaPymeNoPuedePedirleOtraVez() {
        Scenario s = awarded("declina");
        as(s.pyme()).body(Map.of("reason", "No se presentó")).post("/api/v1/shipments/" + s.shipmentId() + "/cancel").then().statusCode(200);
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/request").then().statusCode(200);
        as(s.second()).post("/api/v1/bids/" + s.secondBid() + "/backup/decline").then().statusCode(200)
                .body("data.state", is("DECLINED"));
        as(s.pyme()).get("/api/v1/notifications").then().body("data.find { it.type == 'BACKUP_RESPONDED' }.title",
                is("No puede llevar la carga"));
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/request").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-005"));
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/award").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-004"));
    }

    @Test
    void fueraDeLas24HNoSePuedeElegirOtraOferta() {
        Scenario s = awarded("tarde");
        // El envío se creó hace 30 h: cancelarlo ahora queda fuera del plazo
        systemTx.run(() -> em.createNativeQuery("UPDATE shipments SET created_at = now() - interval '30 hours' WHERE id = ?1")
                .setParameter(1, UUID.fromString(s.shipmentId())).executeUpdate());
        as(s.pyme()).body(Map.of("reason", "Cambio de planes")).post("/api/v1/shipments/" + s.shipmentId() + "/cancel").then().statusCode(200);
        as(s.pyme()).get(backups(s.requestId())).then().body("data.eligible", is(false),
                "data.blockerCode", is("LOGYX-BCK-002"));
        as(s.pyme()).post("/api/v1/bids/" + s.secondBid() + "/backup/request").then().statusCode(409)
                .body("error.code", is("LOGYX-BCK-002"));
    }

    @Test
    void alAdjudicarSeGuardaUnRespaldoPorOfertaRechazada() {
        // Cada oferta que RN05 rechaza queda en bid_backups (la tabla la escribe la PYME bajo su RLS)
        Scenario s = awarded("rank");
        long n = systemTx.call(() -> ((Number) em.createNativeQuery("SELECT count(*) FROM bid_backups WHERE request_id = ?1")
                .setParameter(1, UUID.fromString(s.requestId())).getSingleResult()).longValue());
        assertEquals(1, n);
    }
}
