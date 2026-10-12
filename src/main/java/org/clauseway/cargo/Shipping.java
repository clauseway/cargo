package org.clauseway.cargo;

// ABOUTME: The shipping domain facade — an immutable fact base over one
// ABOUTME: transaction: booking, routing and handling land as facts, reads are solves.

import static org.clauseway.cargo.Schema.carrierMovement;
import static org.clauseway.cargo.Schema.cargo;
import static org.clauseway.cargo.Schema.handlingEvent;
import static org.clauseway.cargo.Schema.leg;
import static org.clauseway.cargo.Schema.location;
import static org.clauseway.cargo.Schema.registration;
import static org.clauseway.cargo.Schema.routeSpecification;
import static org.clauseway.cargo.Schema.voyage;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.clauseway.functional.Nothing;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.vavr.control.Try;

public final class Shipping implements AutoCloseable {

	/** The voyage of a port-side handling: none, as the empty number. */
	public static final String NO_VOYAGE = "";

	private final Transaction db;
	private final Rules rules;

	private Shipping(Transaction db) {
		this.db = db;
		this.rules = new Rules(db);
	}

	/** A private one-history world — the pure-value mode for tests and demos. */
	public static Shipping empty() {
		return over(Transaction.recording(SharedDatabase.empty().open("shipping")));
	}

	public static Shipping over(Transaction transaction) {
		return new Shipping(transaction);
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

	// -- the write side: facts and events --------------------------------

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

	/** Booking: the cargo exists, with its origin and its first requirement. */
	public Shipping bookCargo(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return with(cargo(db, lval(trackingId), lval(origin)),
				routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline)));
	}

	/** The itinerary is a value: the old legs go whole, the new ones land in order. */
	public Shipping assignToRoute(String trackingId, List<Leg> legs) {
		List<Literal> spent = legLiterals(trackingId, itineraryOf(trackingId));
		return new Shipping(db.retracting(spent).asserting(legLiterals(trackingId, legs)));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			Instant completionTime, Instant registrationTime) {
		return withHandlingEvent(trackingId, type, location, NO_VOYAGE, completionTime, registrationTime);
	}

	/** The event is one fact, the report of it another: a second report adds only a registration. */
	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			String voyageNumber, Instant completionTime, Instant registrationTime) {
		return with(handlingEvent(db, lval(trackingId), lval(type), lval(location), lval(voyageNumber),
						lval(completionTime)),
				registration(db, lval(trackingId), lval(type), lval(location), lval(voyageNumber),
						lval(completionTime), lval(registrationTime)));
	}

	private Shipping with(Literal... rows) {
		return new Shipping(db.asserting(rows));
	}

	private List<Literal> legLiterals(String trackingId, List<Leg> legs) {
		return IntStream.range(0, legs.size())
				.mapToObj(i -> leg(db, lval(trackingId), lval(i), lval(legs.get(i).getVoyageNumber()),
						lval(legs.get(i).getLoadLocation()), lval(legs.get(i).getUnloadLocation()),
						lval(legs.get(i).getLoadTime()), lval(legs.get(i).getUnloadTime())))
				.collect(Collectors.toList());
	}

	// -- the read side: relational queries -------------------------------

	public Optional<RouteSpecification> routeSpecificationOf(String trackingId) {
		Unifiable<String> origin = lvar();
		Unifiable<String> destination = lvar();
		Unifiable<Instant> deadline = lvar();
		return Query.of(routeSpecification(db, lval(trackingId), origin, destination, deadline))
				.select(origin, destination, deadline).rows()
				.map(row -> new RouteSpecification(row.get(origin).get(), row.get(destination).get(),
						row.get(deadline).get()))
				.findFirst();
	}

	/** The cargo's legs in itinerary order; empty when it is not routed. */
	public List<Leg> itineraryOf(String trackingId) {
		Unifiable<Integer> index = lvar();
		Unifiable<String> voyageNumber = lvar();
		Unifiable<String> load = lvar();
		Unifiable<String> unload = lvar();
		Unifiable<Instant> loadTime = lvar();
		Unifiable<Instant> unloadTime = lvar();
		return Query.of(leg(db, lval(trackingId), index, voyageNumber, load, unload, loadTime, unloadTime))
				.select(index, voyageNumber, load, unload, loadTime, unloadTime).rows()
				.sorted(Comparator.comparing((Row row) -> row.get(index).get()))
				.map(row -> new Leg(row.get(voyageNumber).get(), row.get(load).get(), row.get(unload).get(),
						row.get(loadTime).get(), row.get(unloadTime).get()))
				.collect(Collectors.toList());
	}

	/** The cargo's handling events, distinct, in completion order. */
	public List<HandlingEvent> handlingHistoryOf(String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<String> voyageNumber = lvar();
		Unifiable<Instant> completed = lvar();
		return events(Query.of(handlingEvent(db, lval(trackingId), type, location, voyageNumber, completed))
				.select(type, location, voyageNumber, completed).rows(), type, location, voyageNumber, completed)
				.sorted(Comparator.comparing(HandlingEvent::getCompletionTime))
				.collect(Collectors.toList());
	}

	/** The most recently completed handling of the cargo, if any. */
	public Optional<HandlingEvent> lastEventOf(String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<String> voyageNumber = lvar();
		Unifiable<Instant> completed = lvar();
		return events(Query.of(rules.lastEvent(lval(trackingId), type, location, voyageNumber, completed))
				.select(type, location, voyageNumber, completed).rows(), type, location, voyageNumber, completed)
				.findFirst();
	}

	private static Stream<HandlingEvent> events(Stream<Row> rows, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber, Unifiable<Instant> completed) {
		return rows.map(row -> new HandlingEvent(row.get(type).get(), row.get(location).get(),
				row.get(voyageNumber).get(), row.get(completed).get()));
	}
}
