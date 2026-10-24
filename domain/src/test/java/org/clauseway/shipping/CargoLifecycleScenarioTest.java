package org.clauseway.shipping;

// ABOUTME: dddsample's CargoLifecycleScenarioTest: one cargo from Hongkong to
// ABOUTME: Stockholm — booked, routed, misdirected at Tokyo, re-routed, claimed.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.shipping.handling.HandlingType.CLAIM;
import static org.clauseway.shipping.handling.HandlingType.LOAD;
import static org.clauseway.shipping.handling.HandlingType.RECEIVE;
import static org.clauseway.shipping.handling.HandlingType.UNLOAD;
import static org.clauseway.shipping.cargo.RoutingStatus.MISROUTED;
import static org.clauseway.shipping.cargo.RoutingStatus.NOT_ROUTED;
import static org.clauseway.shipping.cargo.RoutingStatus.ROUTED;
import static org.clauseway.shipping.cargo.TransportStatus.CLAIMED;
import static org.clauseway.shipping.cargo.TransportStatus.IN_PORT;
import static org.clauseway.shipping.cargo.TransportStatus.NOT_RECEIVED;
import static org.clauseway.shipping.cargo.TransportStatus.ONBOARD_CARRIER;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.junit.Test;
import org.clauseway.shipping.cargo.dto.DeliveryDto;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.handling.HandlingType;
import org.clauseway.shipping.cargo.dto.LegDto;

public class CargoLifecycleScenarioTest {

	private static final String HONGKONG = "CNHKG";
	private static final String MELBOURNE = "AUMEL";
	private static final String STOCKHOLM = "SESTO";
	private static final String HELSINKI = "FIHEL";
	private static final String CHICAGO = "USCHI";
	private static final String TOKYO = "JNTKO";
	private static final String HAMBURG = "DEHAM";
	private static final String ROTTERDAM = "NLRTM";
	private static final String NEWYORK = "USNYC";

	private static final String V100 = "V100";
	private static final String V200 = "V200";
	private static final String V300 = "V300";
	private static final String V400 = "V400";

	private static final String TRACKING_ID = "ABC";

	private final SharedDatabase store = SharedDatabase.empty();
	private int transactions = 0;

	private static Instant day(String date) {
		return Instant.parse(date + "T00:00:00Z");
	}

	private Shipping open() {
		return Shipping.over(Transaction.recording(store.open("step-" + transactions++)));
	}

	/** One use case is one transaction, as each of dddsample's service calls is. */
	private void commit(Function<Shipping, Shipping> useCase) {
		assertThat(useCase.apply(open()).commit().isSuccess()).isTrue();
	}

	private DeliveryDto delivery() {
		return open().tracking().deliveryOf(TRACKING_ID);
	}

	/** dddsample's sample locations and sample voyages V100 to V400. */
	private static Shipping world(Shipping s) {
		return s
				.withLocation(HONGKONG, "Hongkong")
				.withLocation(MELBOURNE, "Melbourne")
				.withLocation(STOCKHOLM, "Stockholm")
				.withLocation(HELSINKI, "Helsinki")
				.withLocation(CHICAGO, "Chicago")
				.withLocation(TOKYO, "Tokyo")
				.withLocation(HAMBURG, "Hamburg")
				.withLocation(ROTTERDAM, "Rotterdam")
				.withLocation(NEWYORK, "New York")
				.withVoyage(V100)
				.withCarrierMovement(V100, HONGKONG, TOKYO, day("2009-03-03"), day("2009-03-05"))
				.withCarrierMovement(V100, TOKYO, NEWYORK, day("2009-03-06"), day("2009-03-09"))
				.withVoyage(V200)
				.withCarrierMovement(V200, TOKYO, NEWYORK, day("2009-03-06"), day("2009-03-08"))
				.withCarrierMovement(V200, NEWYORK, CHICAGO, day("2009-03-10"), day("2009-03-14"))
				.withCarrierMovement(V200, CHICAGO, STOCKHOLM, day("2009-03-14"), day("2009-03-16"))
				.withVoyage(V300)
				.withCarrierMovement(V300, TOKYO, ROTTERDAM, day("2009-03-08"), day("2009-03-11"))
				.withCarrierMovement(V300, ROTTERDAM, HAMBURG, day("2009-03-11"), day("2009-03-12"))
				.withCarrierMovement(V300, HAMBURG, MELBOURNE, day("2009-03-14"), day("2009-03-18"))
				.withCarrierMovement(V300, MELBOURNE, TOKYO, day("2009-03-19"), day("2009-03-21"))
				.withVoyage(V400)
				.withCarrierMovement(V400, HAMBURG, STOCKHOLM, day("2009-03-14"), day("2009-03-15"))
				.withCarrierMovement(V400, STOCKHOLM, HELSINKI, day("2009-03-15"), day("2009-03-16"))
				.withCarrierMovement(V400, HELSINKI, HAMBURG, day("2009-03-20"), day("2009-03-22"));
	}

	/** The scenario's handling report aboard a voyage, completed and registered on the same day, then the inspection it triggers: two transactions, as the sample's message hop makes them. */
	private void handle(String date, String voyage, String location, HandlingType type) {
		commit(s -> s.registerHandling(TRACKING_ID, type, location, voyage, day(date), day(date)).get());
		commit(s -> s.inspectCargo(TRACKING_ID).get());
	}

	/** The scenario's port-side handling report, then the inspection it triggers. */
	private void handle(String date, String location, HandlingType type) {
		commit(s -> s.registerHandling(TRACKING_ID, type, location, day(date), day(date)).get());
		commit(s -> s.inspectCargo(TRACKING_ID).get());
	}

	@Test
	public void cargoFromHongkongToStockholm() {
		/* Test setup: A cargo should be shipped from Hongkong to Stockholm,
		   and it should arrive in no more than two weeks. */
		Instant arrivalDeadline = day("2009-03-18");
		commit(CargoLifecycleScenarioTest::world);

		/* Use case 1: booking
		   A new cargo is booked, and the unique tracking id is assigned to the cargo. */
		commit(s -> s.bookCargo(TRACKING_ID, HONGKONG, STOCKHOLM, arrivalDeadline).get());

		/* Use case 2: routing
		   The cargo, and thus the domain model, is responsible for determining the
		   status of the cargo, whether it is on the right track or not and so on. */
		assertThat(delivery().getTransportStatus()).isEqualTo(NOT_RECEIVED);
		assertThat(delivery().getRoutingStatus()).isEqualTo(NOT_ROUTED);
		assertThat(delivery().isMisdirected()).isFalse();
		assertThat(delivery().getEta()).isEmpty();
		assertThat(delivery().getNextExpectedActivity()).isEmpty();

		/* The scenario's routing stub offers one route from Hongkong; the cargo is
		   assigned to it, third leg as given. */
		commit(s -> s.assignToRoute(TRACKING_ID, Arrays.asList(
				new LegDto(V100, HONGKONG, NEWYORK, day("2009-03-03"), day("2009-03-09")),
				new LegDto(V200, NEWYORK, CHICAGO, day("2009-03-10"), day("2009-03-14")),
				new LegDto(V200, CHICAGO, STOCKHOLM, day("2009-03-07"), day("2009-03-11")))).get());

		assertThat(delivery().getTransportStatus()).isEqualTo(NOT_RECEIVED);
		assertThat(delivery().getRoutingStatus()).isEqualTo(ROUTED);
		assertThat(delivery().getEta()).isPresent();
		assertThat(delivery().getNextExpectedActivity())
				.contains(new HandlingActivityDto(RECEIVE, HONGKONG, Optional.empty()));

		/* Use case 3: handling
		   Handling begins: cargo is received in Hongkong. */
		handle("2009-03-01", HONGKONG, RECEIVE);

		assertThat(delivery().getTransportStatus()).isEqualTo(IN_PORT);
		assertThat(delivery().getLastKnownLocation()).contains(HONGKONG);

		// Next event: Load onto voyage V100 in Hongkong
		handle("2009-03-03", V100, HONGKONG, LOAD);

		assertThat(delivery().getCurrentVoyage()).contains(V100);
		assertThat(delivery().getLastKnownLocation()).contains(HONGKONG);
		assertThat(delivery().getTransportStatus()).isEqualTo(ONBOARD_CARRIER);
		assertThat(delivery().isMisdirected()).isFalse();
		assertThat(delivery().getNextExpectedActivity())
				.contains(new HandlingActivityDto(UNLOAD, NEWYORK, Optional.of(V100)));

		/* An attempt to register a handling event that is not valid because
		   there is no voyage with the specified voyage number, and there is no
		   location with the specified UN Locode either. Rejected; the delivery
		   is unaffected. */
		assertThat(open().registerHandling(TRACKING_ID, LOAD, "ZZZZZ", "XX000",
				day("2009-03-05"), day("2009-03-05")).isFailure()).isTrue();

		/* Cargo is now (incorrectly) unloaded in Tokyo. */
		handle("2009-03-05", V100, TOKYO, UNLOAD);

		assertThat(delivery().getCurrentVoyage()).isEmpty();
		assertThat(delivery().getLastKnownLocation()).contains(TOKYO);
		assertThat(delivery().getTransportStatus()).isEqualTo(IN_PORT);
		assertThat(delivery().isMisdirected()).isTrue();
		assertThat(delivery().getNextExpectedActivity()).isEmpty();

		/* Cargo needs to be rerouted. Specify a new route, this time from Tokyo
		   (where it was incorrectly unloaded) to Stockholm. */
		commit(s -> s.specifyNewRoute(TRACKING_ID, TOKYO, STOCKHOLM, arrivalDeadline).get());

		assertThat(delivery().getRoutingStatus()).isEqualTo(MISROUTED);
		assertThat(delivery().getNextExpectedActivity()).isEmpty();

		// Assign the stub's route from Tokyo
		commit(s -> s.assignToRoute(TRACKING_ID, Arrays.asList(
				new LegDto(V300, TOKYO, HAMBURG, day("2009-03-08"), day("2009-03-12")),
				new LegDto(V400, HAMBURG, STOCKHOLM, day("2009-03-14"), day("2009-03-15")))).get());

		assertThat(delivery().getRoutingStatus()).isEqualTo(ROUTED);

		// Load in Tokyo
		handle("2009-03-08", V300, TOKYO, LOAD);

		assertThat(delivery().getCurrentVoyage()).contains(V300);
		assertThat(delivery().getLastKnownLocation()).contains(TOKYO);
		assertThat(delivery().getTransportStatus()).isEqualTo(ONBOARD_CARRIER);
		assertThat(delivery().isMisdirected()).isFalse();
		assertThat(delivery().getNextExpectedActivity())
				.contains(new HandlingActivityDto(UNLOAD, HAMBURG, Optional.of(V300)));

		// Unload in Hamburg
		handle("2009-03-12", V300, HAMBURG, UNLOAD);

		assertThat(delivery().getCurrentVoyage()).isEmpty();
		assertThat(delivery().getLastKnownLocation()).contains(HAMBURG);
		assertThat(delivery().getTransportStatus()).isEqualTo(IN_PORT);
		assertThat(delivery().isMisdirected()).isFalse();
		assertThat(delivery().getNextExpectedActivity())
				.contains(new HandlingActivityDto(LOAD, HAMBURG, Optional.of(V400)));

		// Load in Hamburg
		handle("2009-03-14", V400, HAMBURG, LOAD);

		assertThat(delivery().getCurrentVoyage()).contains(V400);
		assertThat(delivery().getLastKnownLocation()).contains(HAMBURG);
		assertThat(delivery().getTransportStatus()).isEqualTo(ONBOARD_CARRIER);
		assertThat(delivery().isMisdirected()).isFalse();
		assertThat(delivery().getNextExpectedActivity())
				.contains(new HandlingActivityDto(UNLOAD, STOCKHOLM, Optional.of(V400)));

		// Unload in Stockholm
		handle("2009-03-15", V400, STOCKHOLM, UNLOAD);

		assertThat(delivery().getCurrentVoyage()).isEmpty();
		assertThat(delivery().getLastKnownLocation()).contains(STOCKHOLM);
		assertThat(delivery().getTransportStatus()).isEqualTo(IN_PORT);
		assertThat(delivery().isMisdirected()).isFalse();
		assertThat(delivery().getNextExpectedActivity())
				.contains(new HandlingActivityDto(CLAIM, STOCKHOLM, Optional.empty()));

		// Finally, cargo is claimed in Stockholm. This ends the cargo lifecycle from our perspective.
		handle("2009-03-16", STOCKHOLM, CLAIM);

		assertThat(delivery().getCurrentVoyage()).isEmpty();
		assertThat(delivery().getLastKnownLocation()).contains(STOCKHOLM);
		assertThat(delivery().getTransportStatus()).isEqualTo(CLAIMED);
		assertThat(delivery().isMisdirected()).isFalse();
		assertThat(delivery().getNextExpectedActivity()).isEmpty();
	}
}
