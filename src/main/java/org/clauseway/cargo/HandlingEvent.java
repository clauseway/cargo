package org.clauseway.cargo;

// ABOUTME: One handling of a cargo: what was done, where, on which voyage if
// ABOUTME: any, and when — its identity; registrations of it are separate facts.

import java.time.Instant;
import lombok.Value;

@Value
public class HandlingEvent {
	HandlingType type;
	String location;
	String voyageNumber;
	Instant completionTime;
}
