package org.clauseway.shipping.cargo;

// ABOUTME: The cargo's delivery as the tracking reads it off the stored row: a plain value
// ABOUTME: with every field of dddsample's Delivery except the last event and the clock.

import java.time.Instant;
import java.util.Optional;
import lombok.Value;

@Value
public class DeliveryDTO {
	RoutingStatus routingStatus;
	TransportStatus transportStatus;
	Optional<String> lastKnownLocation;
	Optional<String> currentVoyage;
	boolean misdirected;
	Optional<Instant> eta;
	Optional<HandlingActivityDTO> nextExpectedActivity;
	boolean unloadedAtDestination;
}
