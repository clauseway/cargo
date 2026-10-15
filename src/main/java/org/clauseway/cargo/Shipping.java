package org.clauseway.cargo;

// ABOUTME: The shipping domain facade — an immutable fact base over one
// ABOUTME: transaction: booking, routing and handling land as facts; reads are its Tracking.

import static org.clauseway.cargo.Schema.carrierMovement;
import static org.clauseway.cargo.Schema.cargo;
import static org.clauseway.cargo.Schema.handlingEvent;
import static org.clauseway.cargo.Schema.leg;
import static org.clauseway.cargo.Schema.location;
import static org.clauseway.cargo.Schema.routeSpecification;
import static org.clauseway.cargo.Schema.voyage;
import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.clauseway.cargo.rules.History;
import org.clauseway.cargo.rules.Itinerary;
import org.clauseway.cargo.rules.Progress;
import org.clauseway.cargo.rules.Routing;
import org.clauseway.cargo.rules.Voyages;
import org.clauseway.functional.Nothing;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.vavr.control.Try;

public final class Shipping implements AutoCloseable {

	private final Transaction db;
	private final Tracking tracking;

	private Shipping(Transaction db) {
		this.db = db;
		History history = new History(db);
		Itinerary itinerary = new Itinerary(db);
		Routing routing = new Routing(db, itinerary, new Voyages(db));
		this.tracking = new Tracking(db, history, itinerary, routing,
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
		return with(location(db, lval(unlocode), lval(name)));
	}

	public Shipping withVoyage(String voyageNumber) {
		return with(voyage(db, lval(voyageNumber)));
	}

	public Shipping withCarrierMovement(String voyageNumber, String departureLocation,
			String arrivalLocation, Instant departureTime, Instant arrivalTime) {
		return with(carrierMovement(db, lval(voyageNumber), lval(departureLocation),
				lval(arrivalLocation), lval(departureTime), lval(arrivalTime)));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			Instant completionTime, Instant registrationTime) {
		return with(handling(trackingId, type, location, none(), completionTime, registrationTime));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			String voyageNumber, Instant completionTime, Instant registrationTime) {
		return with(handling(trackingId, type, location, some(lval(voyageNumber)), completionTime, registrationTime));
	}

	// -- the commands: denials first, then the facts -----------------------

	/** Booking: the cargo exists, with its origin and its first requirement. */
	public Try<Shipping> bookCargo(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return command(tracking.bookingDenials(trackingId, origin, destination), () -> with(
				cargo(db, lval(trackingId), lval(origin)),
				routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline))));
	}

	/** The specification is a value: the old one goes whole, the new one lands; the cargo's origin stays. */
	public Try<Shipping> specifyNewRoute(String trackingId, String origin, String destination,
			Instant arrivalDeadline) {
		return command(tracking.reroutingDenials(trackingId, origin, destination), () -> {
			List<Literal> spent = tracking.routeSpecificationOf(trackingId)
					.map(old -> routeSpecification(db, lval(trackingId), lval(old.getOrigin()),
							lval(old.getDestination()), lval(old.getArrivalDeadline())))
					.map(Collections::singletonList).orElse(Collections.emptyList());
			return new Shipping(db.retracting(spent).asserting(
					routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline))));
		});
	}

	/** The itinerary is a value: the old legs go whole, the new ones land in order. */
	public Try<Shipping> assignToRoute(String trackingId, List<Leg> legs) {
		return command(tracking.routingDenials(trackingId, legs), () -> {
			List<Literal> spent = legLiterals(trackingId, tracking.itineraryOf(trackingId));
			return new Shipping(db.retracting(spent).asserting(legLiterals(trackingId, legs)));
		});
	}

	/** Registering a handling: HandlingEventFactory's checks, then the fact. */
	public Try<Shipping> registerHandling(String trackingId, HandlingType type, String location,
			Optional<String> voyageNumber, Instant completionTime, Instant registrationTime) {
		return command(tracking.registrationDenials(trackingId, type, location, voyageNumber),
				() -> with(handling(trackingId, type, location, LOption.of(voyageNumber), completionTime,
						registrationTime)));
	}

	private static Try<Shipping> command(List<String> denials, Supplier<Shipping> effect) {
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(effect.get());
	}

	private Shipping with(Literal... rows) {
		return new Shipping(db.asserting(rows));
	}

	private Literal handling(String trackingId, HandlingType type, String location,
			Unifiable<LOption<String>> voyageNumber, Instant completionTime, Instant registrationTime) {
		return handlingEvent(db, lval(trackingId), lval(type), lval(location), voyageNumber,
				lval(completionTime), lval(registrationTime));
	}

	private List<Literal> legLiterals(String trackingId, List<Leg> legs) {
		return IntStream.range(0, legs.size())
				.mapToObj(i -> leg(db, lval(trackingId), lval(i), lval(legs.get(i).getVoyageNumber()),
						lval(legs.get(i).getLoadLocation()), lval(legs.get(i).getUnloadLocation()),
						lval(legs.get(i).getLoadTime()), lval(legs.get(i).getUnloadTime())))
				.collect(Collectors.toList());
	}
}
