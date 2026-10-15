package org.clauseway.shipping.oracle;

// ABOUTME: The cargo facade driven over a shared in-memory store, one committed
// ABOUTME: transaction per command, with dddsample's sample locations and voyages as facts.

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Leg;
import org.clauseway.shipping.Shipping;
import org.clauseway.shipping.Tracking;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.vavr.control.Try;
import se.citerus.dddsample.domain.model.location.Location;
import se.citerus.dddsample.domain.model.voyage.CarrierMovement;
import se.citerus.dddsample.domain.model.voyage.Voyage;
import se.citerus.dddsample.infrastructure.sampledata.SampleLocations;
import se.citerus.dddsample.infrastructure.sampledata.SampleVoyages;

final class Ours {

	private final SharedDatabase store = SharedDatabase.empty();
	private int transactions = 0;

	Ours() {
		Shipping seed = open();
		for (Location l : SampleLocations.getAll()) {
			seed = seed.withLocation(l.unLocode().idString(), l.name());
		}
		for (Voyage v : SampleVoyages.getAll()) {
			String number = v.voyageNumber().idString();
			seed = seed.withVoyage(number);
			for (CarrierMovement m : v.schedule().carrierMovements()) {
				seed = seed.withCarrierMovement(number, m.departureLocation().unLocode().idString(),
						m.arrivalLocation().unLocode().idString(), m.departureTime(), m.arrivalTime());
			}
		}
		landed(seed.commit());
	}

	private Shipping open() {
		return Shipping.over(Transaction.recording(store.open("oracle-" + transactions++)));
	}

	private void commit(Function<Shipping, Try<Shipping>> command) {
		landed(command.apply(open()).get().commit());
	}

	private static void landed(Try<?> outcome) {
		if (outcome.isFailure()) {
			throw new IllegalStateException(outcome.getCause());
		}
	}

	Tracking tracking() {
		return open().tracking();
	}

	void book(String trackingId, String origin, String destination, Instant deadline) {
		commit(s -> s.bookCargo(trackingId, origin, destination, deadline));
	}

	void assignToRoute(String trackingId, List<Leg> legs) {
		commit(s -> s.assignToRoute(trackingId, legs));
	}

	void specifyNewRoute(String trackingId, String origin, String destination, Instant deadline) {
		commit(s -> s.specifyNewRoute(trackingId, origin, destination, deadline));
	}

	void handle(String trackingId, HandlingType type, String location, Optional<String> voyageNumber,
			Instant completion, Instant registration) {
		commit(s -> s.registerHandling(trackingId, type, location, voyageNumber, completion, registration));
	}

	Snapshot delivery(String trackingId) {
		return Snapshot.of(tracking().deliveryOf(trackingId));
	}
}
