package org.clauseway.shipping.cargo.dto;

// ABOUTME: The customer's requirement for a cargo: where from, where to, and
// ABOUTME: by when — the specification an itinerary does or does not satisfy.

import java.time.Instant;
import lombok.Value;

@Value
public class RouteSpecificationDto {
	String origin;
	String destination;
	Instant arrivalDeadline;
}
