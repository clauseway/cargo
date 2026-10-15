package org.clauseway.cargo;

// ABOUTME: dddsample's HandlingHistoryTest: a re-registered event is the same
// ABOUTME: event, and the most recently completed one is the history's last.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.Hours.hour;

import java.util.Optional;
import org.junit.Test;

public class HandlingHistoryTest {

	private static final HandlingEvent EVENT_1 = new HandlingEvent(HandlingType.LOAD, "SESTO", Optional.of("X25"), hour(5));
	private static final HandlingEvent EVENT_2 = new HandlingEvent(HandlingType.UNLOAD, "DEHAM", Optional.of("X25"), hour(10));

	/** Event 1 registered twice, at different times; event 2 registered between. */
	private static Shipping history() {
		return Shipping.empty()
				.withLocation("SESTO", "Stockholm")
				.withLocation("DEHAM", "Hamburg")
				.withVoyage("X25")
				.bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get()
				.withHandlingEvent("ABC", HandlingType.UNLOAD, "DEHAM", "X25", hour(10), hour(150))
				.withHandlingEvent("ABC", HandlingType.LOAD, "SESTO", "X25", hour(5), hour(100))
				.withHandlingEvent("ABC", HandlingType.LOAD, "SESTO", "X25", hour(5), hour(200));
	}

	@Test
	public void distinctEventsByCompletionTime() {
		assertThat(history().tracking().handlingHistoryOf("ABC")).containsExactly(EVENT_1, EVENT_2);
	}

	@Test
	public void mostRecentlyCompletedEvent() {
		assertThat(history().tracking().lastEventOf("ABC")).contains(EVENT_2);
	}

	@Test
	public void anUnhandledCargoHasNoLastEvent() {
		Shipping booked = SampleLocations.declaredIn(Shipping.empty()).bookCargo("ABC", "SESTO", "DEHAM", hour(100)).get();
		assertThat(booked.tracking().lastEventOf("ABC")).isEmpty();
	}
}
