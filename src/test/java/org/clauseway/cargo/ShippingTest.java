package org.clauseway.cargo;

// ABOUTME: The facts round-trip: what booking, routing and handling state lands
// ABOUTME: as relations and reads back whole, legs in itinerary order.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.Hours.hour;

import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

public class ShippingTest {

	private static final Leg STOCKHOLM_HELSINKI = new Leg("V100", "SESTO", "FIHEL", hour(1), hour(5));
	private static final Leg HELSINKI_HAMBURG = new Leg("V200", "FIHEL", "DEHAM", hour(10), hour(30));

	private static Shipping world() {
		return Shipping.empty()
				.withLocation("SESTO", "Stockholm")
				.withLocation("FIHEL", "Helsinki")
				.withLocation("DEHAM", "Hamburg")
				.withVoyage("V100")
				.withCarrierMovement("V100", "SESTO", "FIHEL", hour(1), hour(5))
				.withVoyage("V200")
				.withCarrierMovement("V200", "FIHEL", "DEHAM", hour(10), hour(30));
	}

	@Test
	public void bookingStatesTheRouteSpecification() {
		Shipping booked = world().bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get();
		assertThat(booked.routeSpecificationOf("ABC"))
				.contains(new RouteSpecification("SESTO", "DEHAM", hour(100)));
		assertThat(booked.routeSpecificationOf("XYZ")).isEmpty();
	}

	@Test
	public void anUnroutedCargoHasNoLegs() {
		Shipping booked = world().bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get();
		assertThat(booked.itineraryOf("ABC")).isEmpty();
	}

	@Test
	public void legsReadBackInItineraryOrder() {
		Shipping routed = world()
				.bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get()
				.assignToRoute("ABC", Arrays.asList(STOCKHOLM_HELSINKI, HELSINKI_HAMBURG));
		assertThat(routed.itineraryOf("ABC")).containsExactly(STOCKHOLM_HELSINKI, HELSINKI_HAMBURG);
	}

	@Test
	public void handlingEventsReadBackByCargo() {
		Shipping handled = world()
				.bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get()
				.withHandlingEvent("ABC", HandlingType.RECEIVE, "SESTO", hour(0), hour(0))
				.withHandlingEvent("ABC", HandlingType.LOAD, "SESTO", "V100", hour(1), hour(2));
		assertThat(handled.handlingHistoryOf("ABC")).containsExactly(
				new HandlingEvent(HandlingType.RECEIVE, "SESTO", Optional.empty(), hour(0)),
				new HandlingEvent(HandlingType.LOAD, "SESTO", Optional.of("V100"), hour(1)));
		assertThat(handled.handlingHistoryOf("XYZ")).isEmpty();
	}
}
