package org.clauseway.shipping.cargo;

// ABOUTME: The customer's requirement for a cargo: where from, where to, and
// ABOUTME: by when — the specification an itinerary does or does not satisfy.

import java.time.Instant;
import lombok.Value;

@Value
public class RouteSpecificationDTO {
	String origin;
	String destination;
	Instant arrivalDeadline;
}
