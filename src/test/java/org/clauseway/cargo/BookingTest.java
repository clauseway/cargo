package org.clauseway.cargo;

// ABOUTME: Booking and re-routing: the route specification's own guard, and
// ABOUTME: dddsample's testRoutingStatus — a new specification re-judges the route.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.clauseway.cargo.Hours.hour;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.junit.Test;

public class BookingTest {

	private static final String STOCKHOLM = "SESTO";
	private static final String HAMBURG = "DEHAM";
	private static final String MELBOURNE = "AUMEL";

	private static Shipping open(SharedDatabase store, String id) {
		return Shipping.over(Transaction.recording(store.open(id)));
	}

	/** Voyage 0123 and cargo XYZ booked Stockholm to Melbourne, committed. */
	private static SharedDatabase bookedStore() {
		SharedDatabase store = SharedDatabase.empty();
		assertThat(SampleLocations.declaredIn(open(store, "seed"))
				.withVoyage("0123")
				.withCarrierMovement("0123", STOCKHOLM, HAMBURG, hour(1), hour(2))
				.withCarrierMovement("0123", HAMBURG, MELBOURNE, hour(3), hour(4))
				.bookCargo("XYZ", STOCKHOLM, MELBOURNE, hour(100)).get()
				.commit().isSuccess()).isTrue();
		return store;
	}

	@Test
	public void originAndDestinationCannotBeTheSame() {
		Shipping s = SampleLocations.declaredIn(Shipping.empty());
		assertThat(s.bookCargo("XYZ", STOCKHOLM, STOCKHOLM, hour(100)).isFailure()).isTrue();
		assertThat(s.tracking().bookingDenials("XYZ", STOCKHOLM, STOCKHOLM))
				.containsExactly("origin and destination are the same");
	}

	/** BookingServiceImpl looks both ports up; an unknown one has no Location, and the specification cannot be made. */
	@Test
	public void bookingNeedsKnownPorts() {
		Shipping s = SampleLocations.declaredIn(Shipping.empty());
		assertThat(s.bookCargo("XYZ", "ZZZZZ", MELBOURNE, hour(100)).isFailure()).isTrue();
		assertThat(s.tracking().bookingDenials("XYZ", "ZZZZZ", "YYYYY"))
				.containsExactlyInAnyOrder("unknown origin", "unknown destination");
	}

	@Test
	public void reroutingNeedsKnownPorts() {
		Shipping s = open(bookedStore(), "reroute");
		assertThat(s.specifyNewRoute("XYZ", STOCKHOLM, "ZZZZZ", hour(50)).isFailure()).isTrue();
		assertThat(s.tracking().reroutingDenials("XYZ", STOCKHOLM, "ZZZZZ")).containsExactly("unknown destination");
	}

	/** Itinerary's constructor: an itinerary has at least one leg. */
	@Test
	public void anItineraryHasAtLeastOneLeg() {
		Shipping s = open(bookedStore(), "route");
		assertThat(s.assignToRoute("XYZ", Collections.emptyList()).isFailure()).isTrue();
		assertThat(s.tracking().routingDenials("XYZ", Collections.emptyList())).containsExactly("empty itinerary");
	}

	@Test
	public void onlyABookedCargoCanBeRouted() {
		List<Leg> legs = Arrays.asList(new Leg("0123", STOCKHOLM, HAMBURG, hour(1), hour(2)));
		Shipping s = open(bookedStore(), "route");
		assertThat(s.assignToRoute("NOEXIST", legs).isFailure()).isTrue();
		assertThat(s.tracking().routingDenials("NOEXIST", legs)).containsExactly("unknown cargo");
	}

	/** A leg is made of a Voyage and two Locations; none of them can be made from a number or a code nothing is known by. */
	@Test
	public void aLegNamesAKnownVoyageAndKnownPorts() {
		List<Leg> legs = Arrays.asList(
				new Leg("NOPE", STOCKHOLM, "ZZZZZ", hour(1), hour(2)),
				new Leg("0123", "YYYYY", HAMBURG, hour(3), hour(4)));
		Shipping s = open(bookedStore(), "route");
		assertThat(s.assignToRoute("XYZ", legs).isFailure()).isTrue();
		assertThat(s.tracking().routingDenials("XYZ", legs))
				.containsExactlyInAnyOrder("unknown voyage", "unknown unload port", "unknown load port");
	}

	/** CargoFactory asks the repository for a fresh tracking id; here the id is given, so a second booking of it is refused. */
	@Test
	public void aTrackingIdIsBookedOnce() {
		Shipping s = open(bookedStore(), "again");
		assertThat(s.bookCargo("XYZ", HAMBURG, MELBOURNE, hour(100)).isFailure()).isTrue();
		assertThat(s.tracking().bookingDenials("XYZ", HAMBURG, MELBOURNE)).containsExactly("already booked");
	}

	@Test
	public void aNewSpecificationReplacesTheOld() {
		Shipping s = open(bookedStore(), "reroute")
				.specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)).get();
		assertThat(s.tracking().routeSpecificationOf("XYZ")).contains(new RouteSpecification(STOCKHOLM, HAMBURG, hour(50)));
	}

	@Test
	public void routingStatusFollowsTheSpecification() {
		SharedDatabase store = bookedStore();
		assertThat(open(store, "route")
				.assignToRoute("XYZ", Arrays.asList(
						new Leg("0123", STOCKHOLM, HAMBURG, hour(1), hour(2)),
						new Leg("0123", HAMBURG, MELBOURNE, hour(3), hour(4)))).get()
				.commit().isSuccess()).isTrue();
		assertThat(open(store, "check").tracking().routingStatusOf("XYZ")).isEqualTo(RoutingStatus.ROUTED);

		Shipping respecified = open(store, "reroute")
				.specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(100)).get();
		assertThat(respecified.tracking().routingStatusOf("XYZ")).isEqualTo(RoutingStatus.MISROUTED);
	}

	@Test
	public void onlyABookedCargoCanBeRerouted() {
		assertThat(Shipping.empty().specifyNewRoute("NOEXIST", STOCKHOLM, HAMBURG, hour(50)).isFailure()).isTrue();
	}

	/**
	 * The engine's rule, received: a fact this transaction asserted cannot be
	 * retracted by it before a commit — booking and re-specifying in one
	 * transaction is refused, not cancelled out (ledger entry 4).
	 */
	@Test
	public void reSpecifyingBeforeTheBookingIsCommittedIsRefused() {
		Shipping booked = SampleLocations.declaredIn(Shipping.empty()).bookCargo("XYZ", STOCKHOLM, MELBOURNE, hour(100)).get();
		assertThatThrownBy(() -> booked.specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("opposite polarity");
	}
}
