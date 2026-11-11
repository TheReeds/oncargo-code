package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.port.out.AuctionRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.pricing.application.PricingService;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shipment.application.CreateShipmentCommand;
import pe.logyx.shipment.application.CreateShipmentUseCase;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Contrato directo sin subasta: el transportista confirma una reserva de su viaje publicado (módulo trip) y se
 * genera el envío por el mismo camino que una oferta aceptada. Como {@code shipments.request_id} y {@code bid_id}
 * son obligatorios, se materializan una solicitud ya adjudicada ({@code MATCHED}), su subasta cerrada
 * ({@code ACCEPTED}) y una oferta aceptada por el precio de la reserva; así el envío, el seguimiento, la
 * reputación y las incidencias funcionan sin cambios.
 *
 * <p>SystemTx justificado: quien confirma es el transportista, pero la solicitud pertenece a la PYME
 * ({@code requests_owner} exige {@code requester_org_id = app_org()}) y la oferta al transportista; ninguna
 * transacción de usuario puede escribir ambas filas bajo RLS. La reserva ya validó las partes y el precio.
 */
@ApplicationScoped
public class DirectContractUseCase {

    public record Contract(UUID requestId, UUID shipmentId) {}

    @Inject ShipmentRequestRepository requests;
    @Inject AuctionRepository auctions;
    @Inject BidRepository bids;
    @Inject CreateShipmentUseCase shipments;
    @Inject PricingService pricing;
    @Inject SystemTx system;

    public Contract create(DirectContractCommand cmd) {
        PriceQuote quote = pricing.quote(cmd.originCity(), cmd.destinationCity(), cmd.weightKg(), cmd.cargoType());
        return system.call(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            ShipmentRequest r = new ShipmentRequest(UUID.randomUUID(), cmd.requesterOrgId(), cmd.createdBy(),
                    new ShipmentRequest.Cargo(cmd.originCity(), cmd.originAddress(), cmd.destinationCity(),
                            cmd.destinationAddress(), cmd.cargoType(), cmd.cargoDescription(), cmd.weightKg(), null,
                            cmd.pickupDate()),
                    new ShipmentRequest.Pricing(quote.distanceKm(), quote.suggestedPrice(), quote.priceFloor()),
                    ShipmentRequest.Status.MATCHED, cmd.carrierOrgId(), now);
            requests.save(r);

            Auction a = Auction.open(r.id(), now, now, 1);
            a.close(Auction.CloseReason.ACCEPTED, now);
            auctions.save(a);

            Bid b = new Bid(UUID.randomUUID(), a.id(), r.id(), cmd.requesterOrgId(), cmd.carrierOrgId(),
                    cmd.vehicleId(), cmd.vehicleType(), cmd.vehiclePlate(), cmd.agreedPrice(),
                    "Reserva en viaje publicado", Bid.Status.ACCEPTED, now, false);
            bids.save(b);

            UUID shipmentId = shipments.createFromAcceptedBid(new CreateShipmentCommand(
                    r.id(), b.id(), cmd.requesterOrgId(), cmd.carrierOrgId(), cmd.vehicleId(), cmd.agreedPrice(),
                    cmd.originCity(), cmd.originAddress(), cmd.destinationCity(), cmd.destinationAddress()));
            return new Contract(r.id(), shipmentId);
        });
    }
}
