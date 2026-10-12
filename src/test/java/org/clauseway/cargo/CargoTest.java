package org.clauseway.cargo;

// ABOUTME: dddsample's CargoTest: the delivery derived from the handling
// ABOUTME: history — where the cargo is, whether it strayed, what comes next.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.HandlingType.CLAIM;
import static org.clauseway.cargo.HandlingType.CUSTOMS;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static org.clauseway.cargo.HandlingType.UNLOAD;
import static org.clauseway.cargo.Hours.hour;
import static org.clauseway.cargo.Shipping.NO_VOYAGE;

import java.util.Arrays;
import org.junit.Test;

public class CargoTest {

	private static final String STOCKHOLM = "SESTO";
	private static final String HAMBURG = "DEHAM";
	private static final String HONGKONG = "CNHKG";
	private static final String MELBOURNE = "AUMEL";
	private static final String SHANGHAI = "CNSHA";
	private static final String ROTTERDAM = "NLRTM";
	private static final String GOTHENBURG = "SEGOT";
	private static final String HANGZHOU = "CNHGH";
	private static final String TOKYO = "JNTKO";
	private static final String NEWYORK = "USNYC";
	private static final String VOYAGE = "0123";

	/** Voyage 0123: Stockholm, Hamburg, Hong Kong, Melbourne. */
	private static Shipping world() {
		return Shipping.empty()
				.withVoyage(VOYAGE)
				.withCarrierMovement(VOYAGE, STOCKHOLM, HAMBURG, hour(1), hour(2))
				.withCarrierMovement(VOYAGE, HAMBURG, HONGKONG, hour(3), hour(4))
				.withCarrierMovement(VOYAGE, HONGKONG, MELBOURNE, hour(5), hour(6));
	}

	private static Shipping bookedStockholmMelbourne() {
		return world().bookCargo("XYZ", STOCKHOLM, MELBOURNE, hour(100));
	}

	/** Two legs on voyage 0123 through the midpoint, well inside the deadline. */
	private static Shipping withItinerary(String origin, String midpoint, String destination) {
		return world()
				.bookCargo("CARGO1", origin, destination, hour(100))
				.assignToRoute("CARGO1", Arrays.asList(
						new Leg(VOYAGE, origin, midpoint, hour(1), hour(2)),
						new Leg(VOYAGE, midpoint, destination, hour(3), hour(4))));
	}

	private static Shipping handled(Shipping s, String trackingId, int hour, HandlingType type, String location) {
		return s.withHandlingEvent(trackingId, type, location, hour(hour), hour(hour));
	}

	private static Shipping handled(Shipping s, String trackingId, int hour, HandlingType type, String location,
			String voyage) {
		return s.withHandlingEvent(trackingId, type, location, voyage, hour(hour), hour(hour));
	}

	// -- construction -----------------------------------------------------

	@Test
	public void construction() {
		Delivery d = bookedStockholmMelbourne().deliveryOf("XYZ");
		assertThat(d.getRoutingStatus()).isEqualTo(RoutingStatus.NOT_ROUTED);
		assertThat(d.getTransportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
		assertThat(d.getLastKnownLocation()).isEmpty();
		assertThat(d.getCurrentVoyage()).isEmpty();
		assertThat(d.isMisdirected()).isFalse();
		assertThat(d.getEta()).isEmpty();
		assertThat(d.getNextExpectedActivity()).isEmpty();
		assertThat(d.isUnloadedAtDestination()).isFalse();
	}

	// -- last known location and transport status -----------------------

	@Test
	public void lastKnownLocationUnknownWhenNoEvents() {
		assertThat(bookedStockholmMelbourne().deliveryOf("XYZ").getLastKnownLocation()).isEmpty();
	}

	@Test
	public void lastKnownLocationReceived() {
		Delivery d = handled(bookedStockholmMelbourne(), "XYZ", 1, RECEIVE, STOCKHOLM).deliveryOf("XYZ");
		assertThat(d.getLastKnownLocation()).contains(STOCKHOLM);
		assertThat(d.getTransportStatus()).isEqualTo(TransportStatus.IN_PORT);
		assertThat(d.getCurrentVoyage()).isEmpty();
	}

	private static Shipping offHongKong() {
		Shipping s = bookedStockholmMelbourne();
		s = handled(s, "XYZ", 1, LOAD, STOCKHOLM, VOYAGE);
		s = handled(s, "XYZ", 2, UNLOAD, HAMBURG, VOYAGE);
		s = handled(s, "XYZ", 3, LOAD, HAMBURG, VOYAGE);
		return handled(s, "XYZ", 4, UNLOAD, HONGKONG, VOYAGE);
	}

	@Test
	public void lastKnownLocationUnloaded() {
		Delivery d = offHongKong().deliveryOf("XYZ");
		assertThat(d.getLastKnownLocation()).contains(HONGKONG);
		assertThat(d.getTransportStatus()).isEqualTo(TransportStatus.IN_PORT);
		assertThat(d.getCurrentVoyage()).isEmpty();
	}

	@Test
	public void lastKnownLocationLoaded() {
		Shipping s = bookedStockholmMelbourne();
		s = handled(s, "XYZ", 1, LOAD, STOCKHOLM, VOYAGE);
		s = handled(s, "XYZ", 2, UNLOAD, HAMBURG, VOYAGE);
		s = handled(s, "XYZ", 3, LOAD, HAMBURG, VOYAGE);
		Delivery d = s.deliveryOf("XYZ");
		assertThat(d.getLastKnownLocation()).contains(HAMBURG);
		assertThat(d.getTransportStatus()).isEqualTo(TransportStatus.ONBOARD_CARRIER);
		assertThat(d.getCurrentVoyage()).contains(VOYAGE);
	}

	@Test
	public void lastKnownLocationClaimed() {
		Shipping s = offHongKong();
		s = handled(s, "XYZ", 5, LOAD, HONGKONG, VOYAGE);
		s = handled(s, "XYZ", 7, UNLOAD, MELBOURNE, VOYAGE);
		s = handled(s, "XYZ", 9, CLAIM, MELBOURNE);
		Delivery d = s.deliveryOf("XYZ");
		assertThat(d.getLastKnownLocation()).contains(MELBOURNE);
		assertThat(d.getTransportStatus()).isEqualTo(TransportStatus.CLAIMED);
	}

	// -- unloaded at destination -----------------------------------------

	@Test
	public void isUnloadedAtFinalDestination() {
		Shipping s = withItinerary(HANGZHOU, TOKYO, NEWYORK);
		assertThat(s.deliveryOf("CARGO1").isUnloadedAtDestination()).isFalse();

		s = handled(s, "CARGO1", 1, RECEIVE, HANGZHOU);
		assertThat(s.deliveryOf("CARGO1").isUnloadedAtDestination()).as("unrelated event").isFalse();

		s = handled(s, "CARGO1", 2, UNLOAD, TOKYO, VOYAGE);
		assertThat(s.deliveryOf("CARGO1").isUnloadedAtDestination()).as("unload, not at destination").isFalse();

		s = handled(s, "CARGO1", 3, CUSTOMS, NEWYORK);
		assertThat(s.deliveryOf("CARGO1").isUnloadedAtDestination()).as("at destination, not an unload").isFalse();

		s = handled(s, "CARGO1", 4, UNLOAD, NEWYORK, VOYAGE);
		assertThat(s.deliveryOf("CARGO1").isUnloadedAtDestination()).isTrue();
	}

	// -- misdirection ----------------------------------------------------

	@Test
	public void aCargoWithNoItineraryIsNotMisdirected() {
		Shipping s = world().bookCargo("TRKID", SHANGHAI, GOTHENBURG, hour(100));
		assertThat(s.deliveryOf("TRKID").isMisdirected()).isFalse();
	}

	@Test
	public void aCargoWithNoHandlingIsNotMisdirected() {
		assertThat(withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG).deliveryOf("CARGO1").isMisdirected()).isFalse();
	}

	@Test
	public void theHappyPathIsNotMisdirected() {
		Shipping s = withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG);
		s = handled(s, "CARGO1", 10, RECEIVE, SHANGHAI);
		s = handled(s, "CARGO1", 30, LOAD, SHANGHAI, VOYAGE);
		s = handled(s, "CARGO1", 50, UNLOAD, ROTTERDAM, VOYAGE);
		s = handled(s, "CARGO1", 70, LOAD, ROTTERDAM, VOYAGE);
		s = handled(s, "CARGO1", 90, UNLOAD, GOTHENBURG, VOYAGE);
		s = handled(s, "CARGO1", 110, CLAIM, GOTHENBURG);
		s = handled(s, "CARGO1", 130, CUSTOMS, GOTHENBURG);
		assertThat(s.deliveryOf("CARGO1").isMisdirected()).isFalse();
	}

	@Test
	public void receivedOffTheItineraryIsMisdirected() {
		Shipping s = handled(withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG), "CARGO1", 10, RECEIVE, HANGZHOU);
		assertThat(s.deliveryOf("CARGO1").isMisdirected()).isTrue();
	}

	@Test
	public void loadedAtTheRightPortOnTheWrongVoyageIsMisdirected() {
		Shipping s = withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG);
		s = handled(s, "CARGO1", 10, RECEIVE, SHANGHAI);
		s = handled(s, "CARGO1", 30, LOAD, SHANGHAI, VOYAGE);
		s = handled(s, "CARGO1", 50, UNLOAD, ROTTERDAM, VOYAGE);
		s = handled(s, "CARGO1", 70, LOAD, ROTTERDAM, "666");
		assertThat(s.deliveryOf("CARGO1").isMisdirected()).isTrue();
	}

	@Test
	public void claimedAtTheMidpointIsMisdirected() {
		Shipping s = withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG);
		s = handled(s, "CARGO1", 10, RECEIVE, SHANGHAI);
		s = handled(s, "CARGO1", 30, LOAD, SHANGHAI, VOYAGE);
		s = handled(s, "CARGO1", 50, UNLOAD, ROTTERDAM, VOYAGE);
		s = handled(s, "CARGO1", 70, CLAIM, ROTTERDAM);
		assertThat(s.deliveryOf("CARGO1").isMisdirected()).isTrue();
	}

	// -- eta and next expected activity (dddsample leaves this test empty) --

	@Test
	public void anUnroutedCargoHasNoEtaAndNothingExpected() {
		Delivery d = world().bookCargo("TRKID", SHANGHAI, GOTHENBURG, hour(100)).deliveryOf("TRKID");
		assertThat(d.getEta()).isEmpty();
		assertThat(d.getNextExpectedActivity()).isEmpty();
	}

	@Test
	public void aRoutedCargoExpectsReceiptAtTheOriginAndArrivesWithTheLastLeg() {
		Delivery d = withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG).deliveryOf("CARGO1");
		assertThat(d.getEta()).contains(hour(4));
		assertThat(d.getNextExpectedActivity()).contains(new HandlingActivity(RECEIVE, SHANGHAI, NO_VOYAGE));
	}

	@Test
	public void theNextActivityFollowsTheItinerary() {
		Shipping s = withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG);

		s = handled(s, "CARGO1", 10, RECEIVE, SHANGHAI);
		assertThat(s.deliveryOf("CARGO1").getNextExpectedActivity())
				.contains(new HandlingActivity(LOAD, SHANGHAI, VOYAGE));

		s = handled(s, "CARGO1", 30, LOAD, SHANGHAI, VOYAGE);
		assertThat(s.deliveryOf("CARGO1").getNextExpectedActivity())
				.contains(new HandlingActivity(UNLOAD, ROTTERDAM, VOYAGE));

		s = handled(s, "CARGO1", 50, UNLOAD, ROTTERDAM, VOYAGE);
		assertThat(s.deliveryOf("CARGO1").getNextExpectedActivity())
				.contains(new HandlingActivity(LOAD, ROTTERDAM, VOYAGE));

		s = handled(s, "CARGO1", 70, LOAD, ROTTERDAM, VOYAGE);
		assertThat(s.deliveryOf("CARGO1").getNextExpectedActivity())
				.contains(new HandlingActivity(UNLOAD, GOTHENBURG, VOYAGE));

		s = handled(s, "CARGO1", 90, UNLOAD, GOTHENBURG, VOYAGE);
		assertThat(s.deliveryOf("CARGO1").getNextExpectedActivity())
				.contains(new HandlingActivity(CLAIM, GOTHENBURG, NO_VOYAGE));

		s = handled(s, "CARGO1", 110, CLAIM, GOTHENBURG);
		assertThat(s.deliveryOf("CARGO1").getNextExpectedActivity()).isEmpty();
	}

	@Test
	public void aMisdirectedCargoHasNoEtaAndNothingExpected() {
		Delivery d = handled(withItinerary(SHANGHAI, ROTTERDAM, GOTHENBURG), "CARGO1", 10, RECEIVE, HANGZHOU)
				.deliveryOf("CARGO1");
		assertThat(d.getEta()).isEmpty();
		assertThat(d.getNextExpectedActivity()).isEmpty();
	}
}
