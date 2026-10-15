package org.clauseway.shipping;

// ABOUTME: The certified write: a commit proves the reads its decision stood on
// ABOUTME: are unmoved — dddsample locks the Cargo aggregate, the port pins the reads.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.shipping.Hours.hour;
import static org.clauseway.shipping.RoutingStatus.MISROUTED;
import static org.clauseway.shipping.RoutingStatus.NOT_ROUTED;
import static org.clauseway.shipping.RoutingStatus.ROUTED;

import java.util.Arrays;
import java.util.List;
import org.clauseway.functional.Nothing;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.vavr.control.Try;
import org.junit.Test;

public class CertifiedWriteTest {

	private static final String STOCKHOLM = "SESTO";
	private static final String HAMBURG = "DEHAM";
	private static final String MELBOURNE = "AUMEL";

	private static Shipping open(SharedDatabase store, String id) {
		return Shipping.over(Transaction.recording(store.open(id)));
	}

	/** Voyage 0123 Stockholm, Hamburg, Melbourne; cargo XYZ booked Stockholm to Melbourne; committed. */
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

	/**
	 * The race dddsample locks against: one party picks a route for the
	 * specification, another changes the destination. The route was chosen
	 * by reading the specification, so its commit is refused once the
	 * specification has moved — and nothing of it lands.
	 */
	@Test
	public void aRouteChosenForASpecificationTheWorldHasLeftIsRefused() {
		SharedDatabase store = bookedStore();
		Shipping routing = open(store, "route");
		List<Leg> chosen = routing.tracking().routeCandidatesOf("XYZ").get(0);
		Shipping routed = routing.assignToRoute("XYZ", chosen).get();

		assertThat(open(store, "reroute").specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)).get()
				.commit().isSuccess()).isTrue();

		Try<Nothing> refused = routed.commit();
		assertThat(refused.isFailure()).isTrue();
		assertThat(refused.getCause()).isInstanceOf(Transaction.Conflict.class);
		assertThat(open(store, "check").tracking().routingStatusOf("XYZ")).isEqualTo(NOT_ROUTED);
	}

	/**
	 * A route assigned without reading the specification stood on nothing
	 * the other party moved: both commits land, and the status derived from
	 * the two facts says what happened. There is no stored status to go stale.
	 */
	@Test
	public void aRouteAssignedBlindLandsBesideTheNewSpecificationAsMisrouted() {
		SharedDatabase store = bookedStore();
		Shipping routed = open(store, "route").assignToRoute("XYZ", Arrays.asList(
				new Leg("0123", STOCKHOLM, HAMBURG, hour(1), hour(2)),
				new Leg("0123", HAMBURG, MELBOURNE, hour(3), hour(4)))).get();

		assertThat(open(store, "reroute").specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)).get()
				.commit().isSuccess()).isTrue();

		assertThat(routed.commit().isSuccess()).isTrue();
		assertThat(open(store, "check").tracking().routingStatusOf("XYZ")).isEqualTo(MISROUTED);
	}

	/** Two parties re-specify the same cargo: each read the specification it replaces; the second commit is refused. */
	@Test
	public void twoReroutingsTheSecondIsRefused() {
		SharedDatabase store = bookedStore();
		Shipping toHamburg = open(store, "a").specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)).get();
		Shipping toMelbourneSooner = open(store, "b").specifyNewRoute("XYZ", STOCKHOLM, MELBOURNE, hour(60)).get();

		assertThat(toHamburg.commit().isSuccess()).isTrue();
		Try<Nothing> refused = toMelbourneSooner.commit();
		assertThat(refused.isFailure()).isTrue();
		assertThat(refused.getCause()).isInstanceOf(Transaction.Conflict.class);
		assertThat(open(store, "check").tracking().routeSpecificationOf("XYZ"))
				.contains(new RouteSpecification(STOCKHOLM, HAMBURG, hour(50)));
	}

	/** Two parties book one tracking id: each read its absence; the second commit is refused. */
	@Test
	public void twoBookingsOfOneTrackingIdTheSecondIsRefused() {
		SharedDatabase store = SharedDatabase.empty();
		assertThat(SampleLocations.declaredIn(open(store, "seed")).commit().isSuccess()).isTrue();
		Shipping toMelbourne = open(store, "a").bookCargo("XYZ", STOCKHOLM, MELBOURNE, hour(100)).get();
		Shipping toHamburg = open(store, "b").bookCargo("XYZ", STOCKHOLM, HAMBURG, hour(50)).get();

		assertThat(toMelbourne.commit().isSuccess()).isTrue();
		Try<Nothing> refused = toHamburg.commit();
		assertThat(refused.isFailure()).isTrue();
		assertThat(refused.getCause()).isInstanceOf(Transaction.Conflict.class);
		assertThat(open(store, "check").tracking().routeSpecificationOf("XYZ"))
				.contains(new RouteSpecification(STOCKHOLM, MELBOURNE, hour(100)));
	}

	/** The same route, chosen after the world settled, lands as routed. */
	@Test
	public void theRefusedPartyReSolvesAndLands() {
		SharedDatabase store = bookedStore();
		Shipping stale = open(store, "route");
		List<Leg> chosen = stale.tracking().routeCandidatesOf("XYZ").get(0);
		assertThat(open(store, "reroute").specifyNewRoute("XYZ", STOCKHOLM, HAMBURG, hour(50)).get()
				.commit().isSuccess()).isTrue();
		assertThat(stale.assignToRoute("XYZ", chosen).get().commit().isFailure()).isTrue();

		Shipping fresh = open(store, "again");
		List<Leg> rechosen = fresh.tracking().routeCandidatesOf("XYZ").get(0);
		assertThat(rechosen).containsExactly(new Leg("0123", STOCKHOLM, HAMBURG, hour(1), hour(2)));
		assertThat(fresh.assignToRoute("XYZ", rechosen).get().commit().isSuccess()).isTrue();
		assertThat(open(store, "check").tracking().routingStatusOf("XYZ")).isEqualTo(ROUTED);
	}
}
