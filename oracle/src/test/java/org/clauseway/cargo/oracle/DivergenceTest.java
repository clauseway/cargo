package org.clauseway.cargo.oracle;

// ABOUTME: The known divergences between dddsample and the port, each pinned
// ABOUTME: as both answers: the comparison masks them, this test keeps them honest.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static se.citerus.dddsample.application.util.DateUtils.toDate;

import java.util.List;
import java.util.Optional;
import org.clauseway.cargo.Leg;
import org.junit.Test;

public class DivergenceTest {

	private static final String HONGKONG = "CNHKG";
	private static final String MELBOURNE = "AUMEL";
	private static final String STOCKHOLM = "SESTO";
	private static final String TOKYO = "JNTKO";
	private static final String NEWYORK = "USNYC";

	/** Their Cargo.itinerary() answers an empty itinerary for none, and the empty one fails the specification. */
	@Test
	public void anUnroutedCargoInspectedAfterAHandlingIsMisroutedThereAndNotRoutedHere() {
		Worlds w = new Worlds();
		w.book("ABC", HONGKONG, STOCKHOLM, toDate("2009-03-18"));
		assertThat(w.theirs.delivery("ABC").routingStatus()).isEqualTo("NOT_ROUTED");
		assertThat(w.ours.delivery("ABC").routingStatus()).isEqualTo("NOT_ROUTED");

		w.handle("ABC", RECEIVE, HONGKONG, Optional.empty(), toDate("2009-03-01"));
		assertThat(w.theirs.delivery("ABC").routingStatus()).isEqualTo("MISROUTED");
		assertThat(w.ours.delivery("ABC").routingStatus()).isEqualTo("NOT_ROUTED");
		w.assertAgree("ABC");
	}

	/** Their updateOnRouting hands a null itinerary to the misdirection check once a handling exists. */
	@Test
	public void respecifyingAnUnroutedHandledCargoThrowsThereAndLandsHere() {
		Worlds w = new Worlds();
		w.book("ABC", HONGKONG, STOCKHOLM, toDate("2009-03-18"));
		w.handle("ABC", RECEIVE, HONGKONG, Optional.empty(), toDate("2009-03-01"));

		assertThatThrownBy(() -> w.theirs.specifyNewRoute("ABC", HONGKONG, MELBOURNE, toDate("2009-03-18")))
				.isInstanceOf(NullPointerException.class);
		w.ours.specifyNewRoute("ABC", HONGKONG, MELBOURNE, toDate("2009-03-18"));
		assertThat(w.ours.delivery("ABC").routingStatus()).isEqualTo("NOT_ROUTED");
	}

	/** Their next activity after a LOAD is read off the first leg loading at that port; ours off the leg the voyage matched. */
	@Test
	public void afterLoadingAtAPortTheItineraryCallsAtTwiceTheNextActivityDiffers() {
		Worlds w = new Worlds();
		w.book("ABC", HONGKONG, NEWYORK, toDate("2009-03-30"));
		w.assignToRoute("ABC", List.of(
				new Leg("V100", HONGKONG, TOKYO, toDate("2009-03-03"), toDate("2009-03-05")),
				new Leg("V300", TOKYO, MELBOURNE, toDate("2009-03-08"), toDate("2009-03-18")),
				new Leg("V300", MELBOURNE, TOKYO, toDate("2009-03-19"), toDate("2009-03-21")),
				new Leg("V200", TOKYO, NEWYORK, toDate("2009-03-22"), toDate("2009-03-25"))));
		w.handle("ABC", RECEIVE, HONGKONG, Optional.empty(), toDate("2009-03-01"));
		w.handle("ABC", LOAD, HONGKONG, Optional.of("V100"), toDate("2009-03-03"));
		w.handle("ABC", LOAD, TOKYO, Optional.of("V200"), toDate("2009-03-22"));

		assertThat(w.theirs.delivery("ABC").nextExpectedActivity())
				.contains(new Snapshot.Activity("UNLOAD", MELBOURNE, Optional.of("V300")));
		assertThat(w.ours.delivery("ABC").nextExpectedActivity())
				.contains(new Snapshot.Activity("UNLOAD", NEWYORK, Optional.of("V200")));
		w.assertAgree("ABC");
	}
}
