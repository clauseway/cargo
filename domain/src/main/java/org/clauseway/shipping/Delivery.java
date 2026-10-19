package org.clauseway.shipping;

// ABOUTME: The actual transportation of a cargo as it last derived it from its route
// ABOUTME: specification, its itinerary and the handling it was shown; stored by the cargo's commands.

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
