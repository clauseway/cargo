package org.clauseway.shipping.cargo.dto;

// ABOUTME: The cargo's delivery as the tracking reads it off the stored row: a plain value
// ABOUTME: with every field of dddsample's Delivery except the last event and the clock.

import java.time.Instant;
import java.util.Optional;
import lombok.Value;
import org.clauseway.shipping.cargo.RoutingStatus;
import org.clauseway.shipping.cargo.TransportStatus;

@Value
public class DeliveryDto {
	RoutingStatus routingStatus;
	TransportStatus transportStatus;
	Optional<String> lastKnownLocation;
	Optional<String> currentVoyage;
	boolean misdirected;
	Optional<Instant> eta;
	Optional<HandlingActivityDto> nextExpectedActivity;
	boolean unloadedAtDestination;
}
