package org.clauseway.shipping;

// ABOUTME: The shipping domain facade — an immutable fact base over one transaction:
// ABOUTME: each command is its aggregate's, guarded there; reads are its Tracking.

import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;

import java.time.Instant;
import java.util.List;
import org.clauseway.functional.Nothing;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.handling.HandlingType;
import org.clauseway.shipping.handling.History;
import org.clauseway.shipping.location.Locations;
import org.clauseway.shipping.voyage.Voyages;
import org.clauseway.vavr.control.Try;
import org.clauseway.shipping.handling.HandlingEventDTO;
import org.clauseway.shipping.cargo.LegDTO;

public final class Shipping implements AutoCloseable {

	private final Transaction db;
	private final Cargo cargo;
	private final History history;
	private final Tracking tracking;

	private Shipping(Transaction db) {
		this.db = db;
		this.cargo = new Cargo(db);
		this.history = new History(db);
		this.tracking = new Tracking(db, cargo);
	}

	/** A private one-history world — the pure-value mode for tests and demos. */
	public static Shipping empty() {
		return over(Transaction.recording(SharedDatabase.empty().open("shipping")));
	}

	public static Shipping over(Transaction transaction) {
		return new Shipping(transaction);
	}

	/** The read face: every derived fact of this world, as values. */
	public Tracking tracking() {
		return tracking;
	}

	/**
	 * The write face: everything appended by this value's lineage lands,
	 * certified against the reads that justified it; a {@code Conflict}
	 * failure means the world moved — reopen and re-solve.
	 */
	public Try<Nothing> commit() {
		return Try.of(() -> {
			db.commit();
			return Nothing.nothing();
		});
	}

	@Override
	public void close() throws Exception {
		db.close();
	}

	// -- the facts: the schedule and the handlings, landed as given ---------

	public Shipping withLocation(String unlocode, String name) {
		return new Shipping(Locations.declare(db, unlocode, name));
	}

	public Shipping withVoyage(String voyageNumber) {
		return new Shipping(Voyages.declare(db, voyageNumber));
	}

	public Shipping withCarrierMovement(String voyageNumber, String departureLocation,
			String arrivalLocation, Instant departureTime, Instant arrivalTime) {
		return new Shipping(Voyages.schedule(db, voyageNumber, departureLocation, arrivalLocation,
				departureTime, arrivalTime));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			Instant completionTime, Instant registrationTime) {
		checkVoyage(type, false);
		return new Shipping(History.land(db, trackingId, type, location, none(), completionTime, registrationTime));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			String voyageNumber, Instant completionTime, Instant registrationTime) {
		checkVoyage(type, true);
		return new Shipping(History.land(db, trackingId, type, location, some(lval(voyageNumber)),
				completionTime, registrationTime));
	}

	/** The HandlingEvent constructors' guard: a carrier-side kind names a voyage, a port-side kind never does. */
	private static void checkVoyage(HandlingType type, boolean named) {
		if (type.requiresVoyage() && !named) {
			throw new IllegalArgumentException("Voyage is required for event type " + type);
		}
		if (!type.requiresVoyage() && named) {
			throw new IllegalArgumentException("Voyage is not allowed with event type " + type);
		}
	}

	// -- the commands: each the aggregate's own, refused there by name -------

	/** BookingService.bookNewCargo. */
	public Try<Shipping> bookCargo(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return cargo.book(trackingId, origin, destination, arrivalDeadline).map(Shipping::new);
	}

	/** BookingService.changeDestination. */
	public Try<Shipping> specifyNewRoute(String trackingId, String origin, String destination,
			Instant arrivalDeadline) {
		return cargo.specify(trackingId, origin, destination, arrivalDeadline).map(Shipping::new);
	}

	/** BookingService.assignCargoToRoute. */
	public Try<Shipping> assignToRoute(String trackingId, List<LegDTO> legs) {
		return cargo.route(trackingId, legs).map(Shipping::new);
	}

	/** HandlingEventService.registerHandlingEvent, a carrier-side handling aboard the voyage. */
	public Try<Shipping> registerHandling(String trackingId, HandlingType type, String location,
			String voyageNumber, Instant completionTime, Instant registrationTime) {
		return history.register(trackingId, type, location, voyageNumber, completionTime, registrationTime)
				.map(Shipping::new);
	}

	/** HandlingEventService.registerHandlingEvent, a port-side handling with no voyage. */
	public Try<Shipping> registerHandling(String trackingId, HandlingType type, String location,
			Instant completionTime, Instant registrationTime) {
		return history.register(trackingId, type, location, completionTime, registrationTime)
				.map(Shipping::new);
	}

	/** CargoInspectionService.inspectCargo: the handling side's latest event for the cargo, handed to it. */
	public Try<Shipping> inspectCargo(String trackingId) {
		return tracking.lastEventOf(trackingId)
				.map(latest -> cargo.inspect(trackingId, latest))
				.orElseGet(() -> cargo.inspect(trackingId))
				.map(Shipping::new);
	}
}
