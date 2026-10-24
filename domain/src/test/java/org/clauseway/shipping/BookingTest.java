package org.clauseway.shipping;

// ABOUTME: Booking and re-routing: the route specification's own guard, and
// ABOUTME: dddsample's testRoutingStatus — a new specification re-judges the route.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.shipping.Hours.hour;
import static org.clauseway.shipping.Refused.denials;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.Cargo;
import org.junit.Test;
import org.clauseway.shipping.cargo.dto.LegDto;
import org.clauseway.shipping.cargo.dto.RouteSpecificationDto;
import org.clauseway.shipping.cargo.RoutingStatus;

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
		assertThat(denials(s.bookCargo("XYZ", STOCKHOLM, STOCKHOLM, hour(100))))
				.containsExactly("origin and destination are the same");
	}

	/** BookingServiceImpl looks both ports up; an unknown one has no Location, and the specification cannot be made. */
	@Test
	public void bookingNeedsKnownPorts() {
		Shipping s = SampleLocations.declaredIn(Shipping.empty());
		assertThat(denials(s.bookCargo("XYZ", "ZZZZZ", "YYYYY", hour(100))))
				.containsExactlyInAnyOrder("unknown origin", "unknown destination");
	}

	@Test
	public void reroutingNeedsKnownPorts() {
		Shipping s = open(bookedStore(), "reroute");
		assertThat(denials(s.specifyNewRoute("XYZ", STOCKHOLM, "ZZZZZ", hour(50)))).containsExactly("unknown destination");
	}

	/** Itinerary's constructor: an itinerary has at least one leg. */
	@Test
	public void anItineraryHasAtLeastOneLeg() {
		Shipping s = open(bookedStore(), "route");
		assertThat(denials(s.assignToRoute("XYZ", Collections.emptyList()))).containsExactly("empty itinerary");
	}

	@Test
	public void onlyABookedCargoCanBeRouted() {
		List<LegDto> legs = Arrays.asList(new LegDto("0123", STOCKHOLM, HAMBURG, hour(1), hour(2)));
		Shipping s = open(bookedStore(), "route");
		assertThat(denials(s.assignToRoute("NOEXIST", legs))).containsExactly("unknown cargo");
	}

	/** A leg is made of a Voyage and two Locations; none of them can be made from a number or a code nothing is known by. */
	@Test
	public void aLegNamesAKnownVoyageAndKnownPorts() {
		List<LegDto> legs = Arrays.asList(
				new LegDto("NOPE", STOCKHOLM, "ZZZZZ", hour(1), hour(2)),
				new LegDto("0123", "YYYYY", HAMBURG, hour(3), hour(4)));
		Shipping s = open(bookedStore(), "route");
		assertThat(denials(s.assignToRoute("XYZ", legs)))
				.containsExactlyInAnyOrder("unknown voyage", "unknown unload port", "unknown load port");
	}

	/** Cargo.assignToRoute swaps the itinerary value; a leg both routes share is neither spent nor new. */
	@Test
	public void aRouteSharingALegWithTheCurrentOneReplacesIt() {
		SharedDatabase store = bookedStore();
		LegDto first = new LegDto("0123", STOCKHOLM, HAMBURG, hour(1), hour(2));
		assertThat(open(store, "route").assignToRoute("XYZ", Arrays.asList(
				first, new LegDto("0123", HAMBURG, MELBOURNE, hour(3), hour(4)))).get().commit().isSuccess()).isTrue();
		assertThat(open(store, "reroute").assignToRoute("XYZ", Arrays.asList(first)).get().commit().isSuccess()).isTrue();
		assertThat(open(store, "check").tracking().itineraryOf("XYZ")).containsExactly(first);
	}

	/** Cargo.specifyNewRoute with the specification the cargo already has: the same world. */
	@Test
	public void theSameSpecificationAgainChangesNothing() {
		SharedDatabase store = bookedStore();
		assertThat(open(store, "again").specifyNewRoute("XYZ", STOCKHOLM, MELBOURNE, hour(100)).get().commit().isSuccess()).isTrue();
		assertThat(open(store, "check").tracking().routeSpecificationOf("XYZ"))
				.contains(new RouteSpecificationDto(STOCKHOLM, MELBOURNE, hour(100)));
	}

	/** The root guards its own writes: booking a taken id is refused with no facade in between. */
	@Test
	public void theRootRefusesASecondBookingWithoutTheFacade() {
		Transaction db = Transaction.recording(bookedStore().open("direct"));
		Cargo cargo = new Cargo(db);
		assertThat(cargo.book("XYZ", HAMBURG, MELBOURNE, hour(100)).getCause()).hasMessage("already booked");
	}

	/** CargoFactory asks the repository for a fresh tracking id; here the id is given, so a second booking of it is refused. */
	@Test
	public void aTrackingIdIsBookedOnce() {
		Shipping s = open(bookedStore(), "again");
		assertThat(denials(s.bookCargo("XYZ", HAMBURG, MELBOURNE, hour(100)))).containsExactly("already booked");
	}

	@Test
	public void aNewSpecificationReplacesTheOld() {
		Shipping s = open(bookedStore(), "reroute")
				.specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)).get();
		assertThat(s.tracking().routeSpecificationOf("XYZ")).contains(new RouteSpecificationDto(STOCKHOLM, HAMBURG, hour(50)));
	}

	@Test
	public void routingStatusFollowsTheSpecification() {
		SharedDatabase store = bookedStore();
		assertThat(open(store, "route")
				.assignToRoute("XYZ", Arrays.asList(
						new LegDto("0123", STOCKHOLM, HAMBURG, hour(1), hour(2)),
						new LegDto("0123", HAMBURG, MELBOURNE, hour(3), hour(4)))).get()
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
	 * The engine's rule, received: a fact this transaction staged and takes back
	 * cancels out — booking and re-specifying in one transaction leaves the
	 * second specification alone (ledger entry 4).
	 */
	@Test
	public void reSpecifyingBeforeTheBookingIsCommittedReplacesTheSpecification() {
		Shipping booked = SampleLocations.declaredIn(Shipping.empty()).bookCargo("XYZ", STOCKHOLM, MELBOURNE, hour(100)).get();
		assertThat(booked.specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)).get().tracking().routeSpecificationOf("XYZ"))
				.contains(new RouteSpecificationDto(STOCKHOLM, HAMBURG, hour(50)));
	}
}
