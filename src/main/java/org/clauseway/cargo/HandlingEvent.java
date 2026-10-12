package org.clauseway.cargo;

// ABOUTME: One registered handling of a cargo: what was done, where, on which
// ABOUTME: voyage if any, when it happened and when it was reported.

import java.time.Instant;
import lombok.Value;

@Value
public class HandlingEvent {
	HandlingType type;
	String location;
	String voyageNumber;
	Instant completionTime;
	Instant registrationTime;
}
