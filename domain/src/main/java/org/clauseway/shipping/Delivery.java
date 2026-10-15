package org.clauseway.shipping;

// ABOUTME: The actual transportation of a cargo as derived from its handling
// ABOUTME: history, itinerary and route specification; nothing here is stored.

import java.time.Instant;
import java.util.Optional;
import lombok.Value;

@Value
public class Delivery {
	RoutingStatus routingStatus;
	TransportStatus transportStatus;
	Optional<String> lastKnownLocation;
	Optional<String> currentVoyage;
	boolean misdirected;
	Optional<Instant> eta;
	Optional<HandlingActivity> nextExpectedActivity;
	boolean unloadedAtDestination;
}
