package org.clauseway.cargo.oracle;

// ABOUTME: dddsample's domain model driven in memory: a Cargo per tracking id,
// ABOUTME: its handling events kept aside and re-derived after each, as the inspection does.

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.clauseway.cargo.HandlingType;
import org.clauseway.cargo.Leg;
import se.citerus.dddsample.domain.model.cargo.Cargo;
import se.citerus.dddsample.domain.model.cargo.Itinerary;
import se.citerus.dddsample.domain.model.cargo.RouteSpecification;
import se.citerus.dddsample.domain.model.cargo.TrackingId;
import se.citerus.dddsample.domain.model.handling.HandlingEvent;
import se.citerus.dddsample.domain.model.handling.HandlingHistory;
import se.citerus.dddsample.domain.model.location.Location;
import se.citerus.dddsample.domain.model.location.UnLocode;
import se.citerus.dddsample.domain.model.voyage.Voyage;
import se.citerus.dddsample.domain.model.voyage.VoyageNumber;
import se.citerus.dddsample.infrastructure.sampledata.SampleLocations;
import se.citerus.dddsample.infrastructure.sampledata.SampleVoyages;

final class Theirs {

	private final Map<String, Cargo> cargos = new HashMap<>();
	private final Map<String, List<HandlingEvent>> events = new HashMap<>();

	static Location location(String unlocode) {
		Location found = SampleLocations.getAll().stream()
				.filter(l -> l.unLocode().idString().equals(unlocode)).findFirst().orElse(null);
		if (found == null) {
			throw new IllegalArgumentException("no sample location " + unlocode);
		}
		return found;
	}

	static Voyage voyage(String number) {
		Voyage found = SampleVoyages.lookup(new VoyageNumber(number));
		if (found == null) {
			throw new IllegalArgumentException("no sample voyage " + number);
		}
		return found;
	}

	void book(String trackingId, String origin, String destination, Instant deadline) {
		cargos.put(trackingId, new Cargo(new TrackingId(trackingId),
				new RouteSpecification(location(origin), location(destination), deadline)));
		events.put(trackingId, new ArrayList<>());
	}

	void assignToRoute(String trackingId, List<Leg> legs) {
		cargos.get(trackingId).assignToRoute(new Itinerary(legs.stream()
				.map(l -> new se.citerus.dddsample.domain.model.cargo.Leg(voyage(l.getVoyageNumber()),
						location(l.getLoadLocation()), location(l.getUnloadLocation()), l.getLoadTime(), l.getUnloadTime()))
				.collect(Collectors.toList())));
	}

	void specifyNewRoute(String trackingId, String origin, String destination, Instant deadline) {
		cargos.get(trackingId).specifyNewRoute(
				new RouteSpecification(location(origin), location(destination), deadline));
	}

	/** HandlingEventServiceImpl stores the event; CargoInspectionServiceImpl re-derives the delivery from the history. */
	void handle(String trackingId, HandlingType type, String location, Optional<String> voyageNumber,
			Instant completion, Instant registration) {
		Cargo cargo = cargos.get(trackingId);
		HandlingEvent.Type kind = HandlingEvent.Type.valueOf(type.name());
		HandlingEvent event = voyageNumber
				.map(v -> new HandlingEvent(cargo, completion, registration, kind, location(location), voyage(v)))
				.orElseGet(() -> new HandlingEvent(cargo, completion, registration, kind, location(location)));
		events.get(trackingId).add(event);
		cargo.deriveDeliveryProgress(new HandlingHistory(events.get(trackingId)));
	}

	Snapshot delivery(String trackingId) {
		return Snapshot.of(cargos.get(trackingId).delivery());
	}
}
