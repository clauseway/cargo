package org.clauseway.shipping.cargo.dto;

// ABOUTME: A handling that is expected to happen: the kind, the port, and the
// ABOUTME: voyage for the carrier-side kinds — an event minus its time.

import java.util.Optional;
import lombok.Value;
import org.clauseway.shipping.handling.HandlingType;

@Value
public class HandlingActivityDto {
	HandlingType type;
	String location;
	Optional<String> voyageNumber;
}
