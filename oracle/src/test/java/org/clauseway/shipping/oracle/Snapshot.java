package org.clauseway.shipping.oracle;

// ABOUTME: One delivery as both implementations can state it: dddsample's
// ABOUTME: Delivery and the facade's, read into the same plain fields for comparison.

import java.time.Instant;
import java.util.Optional;
import se.citerus.dddsample.domain.model.location.Location;
import se.citerus.dddsample.domain.model.voyage.Voyage;

record Snapshot(String routingStatus, String transportStatus, Optional<String> lastKnownLocation,
		Optional<String> currentVoyage, boolean misdirected, Optional<Instant> eta,
		Optional<Activity> nextExpectedActivity, boolean unloadedAtDestination) {

	record Activity(String type, String location, Optional<String> voyage) {
	}

	static Snapshot of(se.citerus.dddsample.domain.model.cargo.Delivery d) {
		return new Snapshot(
				d.routingStatus().name(),
				d.transportStatus().name(),
				d.lastKnownLocation() == Location.UNKNOWN ? Optional.empty()
						: Optional.of(d.lastKnownLocation().unLocode().idString()),
				d.currentVoyage() == Voyage.NONE ? Optional.empty()
						: Optional.of(d.currentVoyage().voyageNumber().idString()),
				d.isMisdirected(),
				Optional.ofNullable(d.estimatedTimeOfArrival()),
				Optional.ofNullable(d.nextExpectedActivity()).map(a -> new Activity(
						a.type().name(),
						a.location().unLocode().idString(),
						a.voyage() == null ? Optional.empty() : Optional.of(a.voyage().voyageNumber().idString()))),
				d.isUnloadedAtDestination());
	}

	static Snapshot of(org.clauseway.shipping.DeliveryDTO d) {
		return new Snapshot(
				d.getRoutingStatus().name(),
				d.getTransportStatus().name(),
				d.getLastKnownLocation(),
				d.getCurrentVoyage(),
				d.isMisdirected(),
				d.getEta(),
				d.getNextExpectedActivity().map(a -> new Activity(
						a.getType().name(), a.getLocation(), a.getVoyageNumber())),
				d.isUnloadedAtDestination());
	}
}
