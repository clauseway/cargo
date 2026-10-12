package org.clauseway.cargo;

// ABOUTME: The five kinds of handling a cargo undergoes; the two carrier-side
// ABOUTME: kinds name a voyage, the three port-side kinds never do.

public enum HandlingType {
	LOAD(true),
	UNLOAD(true),
	RECEIVE(false),
	CLAIM(false),
	CUSTOMS(false);

	private final boolean voyageRequired;

	HandlingType(boolean voyageRequired) {
		this.voyageRequired = voyageRequired;
	}

	public boolean requiresVoyage() {
		return voyageRequired;
	}
}
