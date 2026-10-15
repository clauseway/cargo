package org.clauseway.shipping;

// ABOUTME: The tests' clock: hour offsets from one fixed instant, so every
// ABOUTME: ordering between scenario timestamps reads as a small integer.

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class Hours {

	private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

	private Hours() {
	}

	static Instant hour(int offset) {
		return EPOCH.plus(offset, ChronoUnit.HOURS);
	}
}
