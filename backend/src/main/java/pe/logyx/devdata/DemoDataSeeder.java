package pe.logyx.devdata;

import pe.logyx.incident.domain.model.IncidentCategory;
import pe.logyx.documents.domain.model.DocumentType;
import io.quarkus.arc.Arc;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.documents.application.DocumentService;
import pe.logyx.documents.application.UploadDocumentCommand;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.incident.application.IncidentService;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.negotiation.application.NegotiationService;
import pe.logyx.reputation.application.ReviewService;
import pe.logyx.fleet.application.AddDriverCommand;
import pe.logyx.fleet.application.AddVehicleCommand;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.AuthResult;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.application.RegisterCommand;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.marketplace.application.*;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shipment.application.AssignDriverUseCase;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.TrackStopsUseCase;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Datos demo para desarrollo (solo perfil {@code dev}; no existe en test ni prod).
 *
 * <p>Usa los casos de uso reales, autenticado como cada usuario demo: se emite su
 * JWT, se instala como identidad de un contexto de petición y todo corre con
 * RLS exactamente como una llamada HTTP. Así el seed respeta las reglas de negocio.
 * Es idempotente: si {@code pyme@logyx.pe} existe, no hace nada.
 *
 * <p>Fase 2: operador {@code operator@logyx.pe}, un envío entregado con foto/firma/guía, calificaciones en ambas
 * direcciones y una incidencia abierta; chats, una contra-oferta aceptada y otra pendiente; dos solicitudes con SLA
 * vencido (sin ofertas hace más de 24 h) y las notificaciones que generan esos eventos.
 */
@IfBuildProfile("dev")
@ApplicationScoped
public class DemoDataSeeder {

    public static final String PASSWORD = "Logyx2026!";
    public static final String PYME_EMAIL = "pyme@logyx.pe";
    public static final String CARRIER_EMAIL = "carrier@logyx.pe";
    public static final String CARRIER2_EMAIL = "carrier2@logyx.pe";
    public static final String DRIVER_EMAIL = "driver@logyx.pe";
    public static final String OPERATOR_EMAIL = "operator@logyx.pe";

    @Inject AuthService auth;
    @Inject UserAccounts accounts;
    @Inject FleetService fleet;
    @Inject PublishRequestUseCase publish;
    @Inject PlaceBidUseCase placeBid;
    @Inject AcceptBidUseCase acceptBid;
    @Inject AssignDriverUseCase assignDriver;
    @Inject TrackStopsUseCase tracking;
    @Inject ShipmentQueries shipments;
    @Inject NegotiationService negotiation;
    @Inject DocumentService documents;
    @Inject ReviewService reviews;
    @Inject IncidentService incidents;
    @Inject EntityManager em;
    @Inject CurrentIdentityAssociation identity;
    @Inject JWTParser jwtParser;

    void onStart(@Observes StartupEvent event) {
        if (accounts.byEmail(PYME_EMAIL).isPresent()) {
            Log.info("Datos demo ya presentes; no se vuelven a crear");
            return;
        }
        seed();
        Log.infof("Datos demo creados: %s, %s, %s, %s, %s (contraseña %s)",
                PYME_EMAIL, CARRIER_EMAIL, CARRIER2_EMAIL, DRIVER_EMAIL, OPERATOR_EMAIL, PASSWORD);
    }

    private void seed() {
        // ---------- Organizaciones (RUCs válidos por módulo 11)
        AuthResult pyme = auth.register(new RegisterCommand(OrgType.PYME, "Comercial Altiplano SAC", "20601234565",
                "Juliaca", "951234567", "María Mamani Quispe", PYME_EMAIL, PASSWORD));
        AuthResult carrier = auth.register(new RegisterCommand(OrgType.CARRIER, "Transportes Sur Andino SRL",
                "20602345671", "Arequipa", "959876543", "Carlos Huamán Ccori", CARRIER_EMAIL, PASSWORD));
        AuthResult carrier2 = auth.register(new RegisterCommand(OrgType.CARRIER, "Logística Titicaca EIRL",
                "20603456786", "Puno", "951112233", "Rosa Condori Apaza", CARRIER2_EMAIL, PASSWORD));

        // Operador LOGYX: no hay registro público, lo crea la plataforma
        AuthResult operator = auth.provisionOperator("LOGYX Operaciones SAC", "20604567891", "Lucía Ramos Torres",
                OPERATOR_EMAIL, PASSWORD);

        // ---------- Flotas
        record Fleet(UUID medium, UUID large, UUID reefer, UUID driver) {}
        Fleet sur = as(carrier, () -> new Fleet(
                vehicle("ASA-101", Vehicle.Type.TRUCK_MEDIUM, 8000, 40),
                vehicle("ASA-202", Vehicle.Type.TRUCK_LARGE, 20000, 80),
                vehicle("ASA-303", Vehicle.Type.REFRIGERATED, 6000, 30),
                fleet.addDriver(new AddDriverCommand("Juan Pari Mamani", DRIVER_EMAIL, PASSWORD, "Q45678912"))
                        .driver().id()));
        record Fleet2(UUID small, UUID platform) {}
        Fleet2 titicaca = as(carrier2, () -> new Fleet2(
                vehicle("LTT-401", Vehicle.Type.TRUCK_SMALL, 4000, 20),
                vehicle("LTT-502", Vehicle.Type.PLATFORM, 15000, 60)));

        // ---------- Envío ya entregado con evidencia, calificaciones e incidencia abierta (post-entrega)
        RequestSummary delivered = as(pyme, () -> request("AREQUIPA", "Av. Alfonso Ugarte 300, Cercado",
                "JULIACA", "Jr. Mariano Núñez 210, Juliaca", CargoType.GENERAL, "Cemento en bolsas de 42.5 kg",
                7000, 0));
        UUID deliveredBid = as(carrier, () -> bid(delivered, sur.large(), "0.95", "Unidad con toldo, salida inmediata"));
        UUID deliveredShipment = as(pyme, () -> acceptBid.accept(deliveredBid).shipmentId());
        as(carrier, () -> assignDriver.assign(deliveredShipment, sur.driver()));
        as(lookup(DRIVER_EMAIL), () -> {
            var stops = shipments.detail(deliveredShipment).stops();
            UUID pickup = stops.get(0).id(), delivery = stops.get(1).id();
            tracking.arrive(deliveredShipment, pickup);
            tracking.complete(deliveredShipment, pickup);
            tracking.arrive(deliveredShipment, delivery);
            upload(deliveredShipment, DocumentType.DELIVERY_PHOTO, delivery, "entrega-juliaca.png",
                    DemoFiles.photo("Entrega en Juliaca — 140 bolsas"));
            upload(deliveredShipment, DocumentType.SIGNATURE, delivery, "firma-receptor.png", DemoFiles.signature());
            return tracking.complete(deliveredShipment, delivery);
        });
        as(pyme, () -> {
            upload(deliveredShipment, DocumentType.REMISSION_GUIDE, null, "guia-remision-EG07-000123.pdf",
                    DemoFiles.pdf("Guia de remision EG07-000123"));
            return reviews.submit(deliveredShipment, 5, 4, 5, "Llegó antes de lo previsto; dos bolsas rotas, "
                    + "lo vemos en la incidencia.");
        });
        as(carrier, () -> reviews.submit(deliveredShipment, 5, 5, 4, "Carga lista a la hora y bien declarada."));
        UUID incidentId = as(pyme, () -> incidents.report(deliveredShipment, IncidentCategory.CARGO_DAMAGE,
                "Dos bolsas de cemento llegaron rotas en la descarga.").incident().id());
        as(carrier, () -> incidents.comment(incidentId, "Revisamos el estibado; proponemos reponer las dos bolsas."));
        as(operator, () -> incidents.comment(incidentId, "Recibido. Revisamos la evidencia con ambas partes."));

        // ---------- Solicitudes publicadas en el corredor
        List<RequestSummary> r = as(pyme, () -> {
            List<RequestSummary> list = new ArrayList<>();
            list.add(request("LIMA", "Av. Argentina 2450, Callao", "JULIACA", "Jr. San Román 456, Juliaca",
                    CargoType.GENERAL, "Abarrotes en cajas (arroz, azúcar, aceite)", 6000, 2));
            list.add(request("AREQUIPA", "Parque Industrial, Calle 3 Mz. B", "PUNO", "Av. El Sol 820, Puno",
                    CargoType.PERISHABLE, "Lácteos refrigerados (yogur y queso fresco)", 4500, 1));
            list.add(request("LIMA", "Av. Industrial 1450, Ate", "CUSCO", "Av. de la Cultura 1200, Cusco",
                    CargoType.FRAGILE, "Electrodomésticos embalados", 3000, 4));
            list.add(request("TACNA", "Zofra Tacna, Lote 18", "AREQUIPA", "Av. Ejército 710, Yanahuara",
                    CargoType.GENERAL, "Repuestos automotrices", 2500, 3));
            list.add(request("JULIACA", "Salida a Puno km 3", "LIMA", "Av. Nicolás Ayllón 3200, Ate",
                    CargoType.BULK, "Quinua orgánica en sacos de 50 kg", 12000, 5));
            list.add(request("CUSCO", "Av. Huayruropata 1520, Wanchaq", "AREQUIPA", "Calle Mercaderes 214",
                    CargoType.GENERAL, "Artesanía textil en fardos", 1800, 6));
            list.add(request("PUNO", "Jr. Lima 380, Puno", "TACNA", "Parque Industrial de Tacna, Mz. C",
                    CargoType.HAZARDOUS, "Fertilizante nitrogenado (UN 2067) en sacos", 9000, 7));
            list.add(request("LIMA", "Av. Separadora Industrial 560, Ate", "AREQUIPA", "Variante de Uchumayo km 5",
                    CargoType.OVERSIZED, "Maquinaria agrícola (tractor compacto)", 7500, 9));
            return list;
        });

        // ---------- Ofertas
        UUID winning = as(carrier, () -> {
            UUID id = bid(r.get(0), sur.medium(), "0.97", "Salida 6 a. m., llegada en 48 h");
            bid(r.get(1), sur.reefer(), "1.00", "Cadena de frío garantizada");
            bid(r.get(4), sur.large(), "0.92", null);
            return id;
        });
        record Bids2(UUID tacna, UUID quinua) {}
        Bids2 bids2 = as(carrier2, () -> {
            bid(r.get(0), titicaca.platform(), "1.05", null);
            UUID tacna = bid(r.get(3), titicaca.small(), "0.90", "Disponible desde mañana");
            UUID quinua = bid(r.get(4), titicaca.platform(), "0.95", null);
            return new Bids2(tacna, quinua);
        });

        // ---------- Negociación: chats y contra-ofertas
        UUID surOrg = carrier.user().organization().id();
        UUID titicacaOrg = carrier2.user().organization().id();
        UUID lacteos = r.get(1).request().id();
        as(carrier, () -> negotiation.post(lacteos, surOrg,
                "Buenos días, ¿la carga debe ir entre 2 y 4 °C todo el trayecto?"));
        as(pyme, () -> negotiation.post(lacteos, surOrg, "Sí, entre 2 y 4 °C. El recojo es a las 7:00 en el parque industrial."));
        as(carrier, () -> negotiation.post(lacteos, surOrg,
                "Perfecto, el ASA-303 tiene registrador de temperatura; le compartimos el reporte al entregar."));
        as(carrier2, () -> negotiation.post(r.get(2).request().id(), titicacaOrg,
                "¿Los electrodomésticos van paletizados y con esquineros?"));
        // contra-oferta aceptada (Tacna → Arequipa) y otra pendiente (quinua a Lima)
        UUID accepted = as(pyme, () -> negotiation.propose(bids2.tacna(), discount(r.get(3), "0.85"),
                "¿Podemos cerrar en este monto? Tenemos carga semanal en la ruta.").id());
        as(carrier2, () -> negotiation.accept(accepted));
        as(pyme, () -> negotiation.propose(bids2.quinua(), discount(r.get(4), "0.88"), "Es nuestro presupuesto."));

        // ---------- SLA (RN10): dos solicitudes sin ofertas publicadas hace más de 24 h
        backdate(r.get(6).request().id(), 30);
        backdate(r.get(7).request().id(), 27);

        // ---------- Adjudicación y envío en tránsito
        UUID shipmentId = as(pyme, () -> acceptBid.accept(winning).shipmentId());
        as(carrier, () -> assignDriver.assign(shipmentId, sur.driver()));
        as(lookup(DRIVER_EMAIL), () -> {
            UUID pickup = shipments.detail(shipmentId).stops().getFirst().id();
            tracking.arrive(shipmentId, pickup);
            return tracking.complete(shipmentId, pickup);
        });
    }

    // ------------------------------------------------------------ helpers

    private void upload(UUID shipmentId, DocumentType type, UUID stopId, String name, byte[] content) {
        documents.upload(new UploadDocumentCommand(shipmentId, type, stopId, name, content.length,
                () -> new ByteArrayInputStream(content)));
    }

    private static BigDecimal discount(RequestSummary request, String factor) {
        return request.request().pricing().suggestedPrice().multiply(new BigDecimal(factor))
                .setScale(0, RoundingMode.HALF_UP);
    }

    /** Simula una solicitud publicada hace {@code hours} horas (solo dev, como dueño del esquema). */
    private void backdate(UUID requestId, int hours) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE shipment_requests SET created_at = now() - make_interval(hours => ?1) WHERE id = ?2")
                .setParameter(1, hours).setParameter(2, requestId).executeUpdate());
    }

    private UUID vehicle(String plate, Vehicle.Type type, int kg, int m3) {
        return fleet.addVehicle(new AddVehicleCommand(plate, type, BigDecimal.valueOf(kg), BigDecimal.valueOf(m3))).id();
    }

    private RequestSummary request(String from, String fromAddress, String to, String toAddress, CargoType cargo,
                                   String description, int kg, int daysAhead) {
        return publish.publish(new PublishRequestCommand(from, fromAddress, to, toAddress, cargo, description,
                BigDecimal.valueOf(kg), null, LocalDate.now().plusDays(daysAhead)));
    }

    /** Oferta a {@code factor} × precio sugerido (nunca bajo el piso RN03). */
    private UUID bid(RequestSummary request, UUID vehicleId, String factor, String notes) {
        BigDecimal amount = request.request().pricing().suggestedPrice()
                .multiply(new BigDecimal(factor)).setScale(0, RoundingMode.HALF_UP);
        return placeBid.placeBid(request.request().id(), new PlaceBidCommand(vehicleId, amount, notes)).bid().id();
    }

    private AuthResult lookup(String email) {
        return auth.login(email, PASSWORD);
    }

    /** Ejecuta {@code work} autenticado como el usuario del token, con RLS activo. */
    private <T> T as(AuthResult user, Supplier<T> work) {
        var rc = Arc.container().requestContext();
        rc.activate();
        try {
            JsonWebToken jwt = jwtParser.parse(user.accessToken());
            identity.setIdentity(QuarkusSecurityIdentity.builder()
                    .setPrincipal(jwt).addRoles(jwt.getGroups()).build());
            return work.get();
        } catch (io.smallrye.jwt.auth.principal.ParseException e) {
            throw new IllegalStateException("No se pudo leer el token demo", e);
        } finally {
            rc.terminate();
        }
    }
}
