package org.clauseway.shipping;

// ABOUTME: The shipping domain facade — an immutable fact base over one transaction:
// ABOUTME: each command checks its denials, then hands its aggregate the write; reads are its Tracking.

import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.clauseway.functional.Nothing;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.cargo.Itinerary;
import org.clauseway.shipping.cargo.Progress;
import org.clauseway.shipping.cargo.Routing;
import org.clauseway.shipping.handling.History;
import org.clauseway.shipping.location.Locations;
import org.clauseway.shipping.voyage.Voyages;
import org.clauseway.vavr.control.Try;

public final class Shipping implements AutoCloseable {

	private final Transaction db;
	private final Tracking tracking;

	private Shipping(Transaction db) {
		this.db = db;
		Locations locations = new Locations(db);
		Voyages voyages = new Voyages(db);
		Cargo cargo = new Cargo(db);
		History history = new History(db, cargo, locations, voyages);
		Itinerary itinerary = new Itinerary(db);
		Routing routing = new Routing(db, itinerary, voyages, locations);
		this.tracking = new Tracking(cargo, history, itinerary, routing,
				new Progress(db, history, itinerary, routing));
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
		return new Shipping(History.register(db, trackingId, type, location, none(), completionTime, registrationTime));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			String voyageNumber, Instant completionTime, Instant registrationTime) {
		checkVoyage(type, true);
		return new Shipping(History.register(db, trackingId, type, location, some(lval(voyageNumber)),
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

	// -- the commands: denials first, then the aggregate's write -------------

	/** Booking: the cargo exists, with its origin and its first requirement. */
	public Try<Shipping> bookCargo(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return command(tracking.bookingDenials(trackingId, origin, destination),
				() -> new Shipping(Cargo.book(db, trackingId, origin, destination, arrivalDeadline)));
	}

	/** Cargo.specifyNewRoute: the current specification gives way to the one given. */
	public Try<Shipping> specifyNewRoute(String trackingId, String origin, String destination,
			Instant arrivalDeadline) {
		return command(tracking.reroutingDenials(trackingId, origin, destination),
				() -> new Shipping(Cargo.specify(db, trackingId, tracking.routeSpecificationOf(trackingId),
						new RouteSpecification(origin, destination, arrivalDeadline))));
	}

	/** Cargo.assignToRoute: the current itinerary gives way to the legs given. */
	public Try<Shipping> assignToRoute(String trackingId, List<Leg> legs) {
		return command(tracking.routingDenials(trackingId, legs),
				() -> new Shipping(Cargo.route(db, trackingId, tracking.itineraryOf(trackingId), legs)));
	}

	/** Registering a handling: HandlingEventFactory's checks, then the fact. */
	public Try<Shipping> registerHandling(String trackingId, HandlingType type, String location,
			Optional<String> voyageNumber, Instant completionTime, Instant registrationTime) {
		return command(tracking.registrationDenials(trackingId, type, location, voyageNumber),
				() -> new Shipping(History.register(db, trackingId, type, location, LOption.of(voyageNumber),
						completionTime, registrationTime)));
	}

	private static Try<Shipping> command(List<String> denials, Supplier<Shipping> effect) {
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(effect.get());
	}
}
