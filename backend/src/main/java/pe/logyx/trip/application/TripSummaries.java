package pe.logyx.trip.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.reputation.application.ReputationQueries;
import pe.logyx.trip.application.port.out.TripBookingRepository;
import pe.logyx.trip.domain.model.BookingStatus;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripBooking;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Arma {@link TripSummary} y {@link BookingSummary} (nombres de las organizaciones, Trust Score, conteos). */
@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class TripSummaries {

    @Inject OrganizationDirectory organizations;
    @Inject ReputationQueries reputation;
    @Inject TripBookingRepository bookings;

    /** {@code owner}: el transportista dueño ve además los conteos de reservas. */
    public List<TripSummary> trips(List<CarrierTrip> list, boolean owner) {
        if (list.isEmpty()) return List.of();
        Set<UUID> carrierIds = list.stream().map(CarrierTrip::carrierOrgId).collect(Collectors.toSet());
        Map<UUID, Organization> orgs = organizations.byIds(carrierIds);
        Map<UUID, BigDecimal> trust = reputation.trustScores(carrierIds);
        return list.stream().map(t -> {
            Long pending = null, confirmed = null;
            if (owner) {
                List<TripBooking> all = bookings.listByTrip(t.id());
                pending = all.stream().filter(b -> b.status() == BookingStatus.PENDING).count();
                confirmed = all.stream().filter(b -> b.status() == BookingStatus.CONFIRMED).count();
            }
            Organization o = orgs.get(t.carrierOrgId());
            return new TripSummary(t, o == null ? null : o.legalName(), trust.get(t.carrierOrgId()), pending, confirmed);
        }).toList();
    }

    public List<BookingSummary> bookings(List<TripBooking> list) {
        if (list.isEmpty()) return List.of();
        Set<UUID> ids = new HashSet<>();
        list.forEach(b -> { ids.add(b.requesterOrgId()); ids.add(b.carrierOrgId()); });
        Map<UUID, Organization> orgs = organizations.byIds(ids);
        return list.stream().map(b -> new BookingSummary(b, name(orgs.get(b.requesterOrgId())),
                name(orgs.get(b.carrierOrgId())))).toList();
    }

    private static String name(Organization o) {
        return o == null ? null : o.legalName();
    }
}
