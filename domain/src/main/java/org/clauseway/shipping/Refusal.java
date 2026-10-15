package org.clauseway.shipping;

// ABOUTME: A command refused: every rule it would violate, named together,
// ABOUTME: carried as the failure of the command's outcome.

import java.util.List;
import lombok.Getter;

@Getter
public final class Refusal extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	private final List<String> denials;

	Refusal(List<String> denials) {
		super(String.join("; ", denials));
		this.denials = denials;
	}
}
