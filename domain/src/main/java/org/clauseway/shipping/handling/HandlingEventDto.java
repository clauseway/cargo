package org.clauseway.shipping.handling;

// ABOUTME: One handling of a cargo: what was done, where, on which voyage if
// ABOUTME: any, and when — its identity; registrations of it are separate facts.

import java.time.Instant;
import java.util.Optional;
import lombok.Value;

@Value
public class HandlingEventDto {
	HandlingType type;
	String location;
	Optional<String> voyageNumber;
	Instant completionTime;
}
