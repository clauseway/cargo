package org.clauseway.cargo;

// ABOUTME: A handling that is expected to happen: the kind, the port, and the
// ABOUTME: voyage for the carrier-side kinds — an event minus its time.

import java.util.Optional;
import lombok.Value;

@Value
public class HandlingActivity {
	HandlingType type;
	String location;
	Optional<String> voyageNumber;
}
