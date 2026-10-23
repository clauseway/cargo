package org.clauseway.shipping;

// ABOUTME: The facts round-trip: what booking, routing and handling state lands
// ABOUTME: as relations and reads back whole, legs in itinerary order.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.clauseway.shipping.Hours.hour;

import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;
import org.clauseway.shipping.handling.HandlingEventDTO;
import org.clauseway.shipping.handling.HandlingType;
import org.clauseway.shipping.cargo.LegDTO;
import org.clauseway.shipping.cargo.RouteSpecificationDTO;

public class ShippingTest {

	private static final LegDTO STOCKHOLM_HELSINKI = new LegDTO("V100", "SESTO", "FIHEL", hour(1), hour(5));
	private static final LegDTO HELSINKI_HAMBURG = new LegDTO("V200", "FIHEL", "DEHAM", hour(10), hour(30));

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
		assertThat(booked.tracking().routeSpecificationOf("ABC"))
				.contains(new RouteSpecificationDTO("SESTO", "DEHAM", hour(100)));
		assertThat(booked.tracking().routeSpecificationOf("XYZ")).isEmpty();
	}

	@Test
	public void anUnroutedCargoHasNoLegs() {
		Shipping booked = world().bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get();
		assertThat(booked.tracking().itineraryOf("ABC")).isEmpty();
	}

	@Test
	public void legsReadBackInItineraryOrder() {
		Shipping routed = world()
				.bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get()
				.assignToRoute("ABC", Arrays.asList(STOCKHOLM_HELSINKI, HELSINKI_HAMBURG)).get();
		assertThat(routed.tracking().itineraryOf("ABC")).containsExactly(STOCKHOLM_HELSINKI, HELSINKI_HAMBURG);
	}

	@Test
	public void handlingEventsReadBackByCargo() {
		Shipping handled = world()
				.bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get()
				.withHandlingEvent("ABC", HandlingType.RECEIVE, "SESTO", hour(0), hour(0))
				.withHandlingEvent("ABC", HandlingType.LOAD, "SESTO", "V100", hour(1), hour(2));
		assertThat(handled.tracking().handlingHistoryOf("ABC")).containsExactly(
				new HandlingEventDTO(HandlingType.RECEIVE, "SESTO", Optional.empty(), hour(0)),
				new HandlingEventDTO(HandlingType.LOAD, "SESTO", Optional.of("V100"), hour(1)));
		assertThat(handled.tracking().handlingHistoryOf("XYZ")).isEmpty();
	}

	/** The HandlingEvent constructors' guard: a carrier-side kind names a voyage, a port-side kind never does. */
	@Test
	public void aLoadLandsOnlyWithAVoyage() {
		assertThatThrownBy(() -> world().withHandlingEvent("ABC", HandlingType.LOAD, "SESTO", hour(1), hour(1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Voyage is required for event type LOAD");
	}

	@Test
	public void aReceiptLandsOnlyWithoutAVoyage() {
		assertThatThrownBy(() -> world().withHandlingEvent("ABC", HandlingType.RECEIVE, "SESTO", "V100", hour(1), hour(1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Voyage is not allowed with event type RECEIVE");
	}
}
