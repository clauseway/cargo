package org.clauseway.shipping.cargo;

// ABOUTME: One leg of an itinerary: a voyage boarded at a location and time
// ABOUTME: and left at another — a value, compared whole.

import java.time.Instant;
import lombok.Value;

@Value
public class LegDTO {
	String voyageNumber;
	String loadLocation;
	String unloadLocation;
	Instant loadTime;
	Instant unloadTime;
}
