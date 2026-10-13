package org.clauseway.cargo;

// ABOUTME: dddsample's ItineraryTest: which handling events an itinerary
// ABOUTME: expects — the right port on the right voyage, in the right role.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.HandlingType.CLAIM;
import static org.clauseway.cargo.HandlingType.CUSTOMS;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static org.clauseway.cargo.HandlingType.UNLOAD;
import static org.clauseway.cargo.Hours.hour;

import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

public class ItineraryTest {

	private static final String SHANGHAI = "CNSHA";
	private static final String ROTTERDAM = "NLRTM";
	private static final String GOTHENBURG = "SEGOT";
	private static final String NEWYORK = "USNYC";
	private static final String HELSINKI = "FIHEL";
	private static final String HANGZHOU = "CNHGH";
	private static final String STOCKHOLM = "SESTO";
	private static final String VOYAGE = "0123";
	private static final String WRONG_VOYAGE = "666";

	private static Shipping booked() {
		return Shipping.empty()
				.withVoyage(VOYAGE)
				.withCarrierMovement(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2))
				.withCarrierMovement(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4))
				.withVoyage(WRONG_VOYAGE)
				.withCarrierMovement(WRONG_VOYAGE, NEWYORK, STOCKHOLM, hour(1), hour(2))
				.withCarrierMovement(WRONG_VOYAGE, STOCKHOLM, HELSINKI, hour(3), hour(4))
				.bookCargo("CARGO1", SHANGHAI, GOTHENBURG, hour(10)).get();
	}

	private static Shipping routed() {
		return booked().assignToRoute("CARGO1", Arrays.asList(
				new Leg(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2)),
				new Leg(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4))));
	}

	private static HandlingEvent event(HandlingType type, String location) {
		return new HandlingEvent(type, location, Optional.empty(), hour(5));
	}

	private static HandlingEvent event(HandlingType type, String location, String voyage) {
		return new HandlingEvent(type, location, Optional.of(voyage), hour(5));
	}

	@Test
	public void cargoOnTrack() {
		Shipping s = routed();
		assertThat(s.isExpected("CARGO1", event(RECEIVE, SHANGHAI))).isTrue();
		assertThat(s.isExpected("CARGO1", event(LOAD, SHANGHAI, VOYAGE))).isTrue();
		assertThat(s.isExpected("CARGO1", event(UNLOAD, ROTTERDAM, VOYAGE))).isTrue();
		assertThat(s.isExpected("CARGO1", event(LOAD, ROTTERDAM, VOYAGE))).isTrue();
		assertThat(s.isExpected("CARGO1", event(UNLOAD, GOTHENBURG, VOYAGE))).isTrue();
		assertThat(s.isExpected("CARGO1", event(CLAIM, GOTHENBURG))).isTrue();
	}

	@Test
	public void customsChangesNothing() {
		assertThat(routed().isExpected("CARGO1", event(CUSTOMS, GOTHENBURG))).isTrue();
	}

	@Test
	public void receivedAtTheWrongLocation() {
		assertThat(routed().isExpected("CARGO1", event(RECEIVE, HANGZHOU))).isFalse();
	}

	@Test
	public void loadedOntoTheWrongShipAtTheRightLocation() {
		assertThat(routed().isExpected("CARGO1", event(LOAD, ROTTERDAM, WRONG_VOYAGE))).isFalse();
	}

	@Test
	public void unloadedFromTheWrongShipAtTheWrongLocation() {
		assertThat(routed().isExpected("CARGO1", event(UNLOAD, HELSINKI, WRONG_VOYAGE))).isFalse();
	}

	@Test
	public void claimedShortOfTheDestination() {
		assertThat(routed().isExpected("CARGO1", event(CLAIM, ROTTERDAM))).isFalse();
	}

	@Test
	public void anUnroutedCargoExpectsEverything() {
		assertThat(booked().isExpected("CARGO1", event(RECEIVE, HANGZHOU))).isTrue();
		assertThat(booked().isExpected("CARGO1", event(UNLOAD, HELSINKI, WRONG_VOYAGE))).isTrue();
	}

	/** The sequence of handlings the itinerary plans, in order — the one invariant behind isExpected and the next activity. */
	@Test
	public void thePlannedHandlings() {
		assertThat(routed().planOf("CARGO1")).containsExactly(
				new HandlingActivity(RECEIVE, SHANGHAI, Optional.empty()),
				new HandlingActivity(LOAD, SHANGHAI, Optional.of(VOYAGE)),
				new HandlingActivity(UNLOAD, ROTTERDAM, Optional.of(VOYAGE)),
				new HandlingActivity(LOAD, ROTTERDAM, Optional.of(VOYAGE)),
				new HandlingActivity(UNLOAD, GOTHENBURG, Optional.of(VOYAGE)),
				new HandlingActivity(CLAIM, GOTHENBURG, Optional.empty()));
		assertThat(booked().planOf("CARGO1")).isEmpty();
	}

	@Test
	public void theFirstAndLastLegs() {
		Shipping s = routed();
		assertThat(s.firstLegOf("CARGO1")).contains(new Leg(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2)));
		assertThat(s.lastLegOf("CARGO1")).contains(new Leg(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4)));
		assertThat(booked().firstLegOf("CARGO1")).isEmpty();
		assertThat(booked().lastLegOf("CARGO1")).isEmpty();
	}
}
