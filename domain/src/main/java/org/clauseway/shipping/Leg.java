package org.clauseway.shipping;

// ABOUTME: One leg of an itinerary: a voyage boarded at a location and time
// ABOUTME: and left at another — a value, compared whole.

import java.time.Instant;
import lombok.Value;

@Value
public class Leg {
	String voyageNumber;
	String loadLocation;
	String unloadLocation;
	Instant loadTime;
	Instant unloadTime;
}
