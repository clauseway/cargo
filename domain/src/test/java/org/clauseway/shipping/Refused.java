package org.clauseway.shipping;

// ABOUTME: Reads the denials off a refused command's outcome, so a test
// ABOUTME: asserts on the rules the refusal names.

import java.util.List;
import org.clauseway.vavr.control.Try;

final class Refused {

	private Refused() {
	}

	static List<String> denials(Try<?> outcome) {
		return ((Refusal) outcome.getCause()).getDenials();
	}
}
