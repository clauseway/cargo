package org.clauseway.cargo;

// ABOUTME: The shipping domain facade — an immutable fact base over one
// ABOUTME: transaction: booking, routing and handling land as facts, reads are solves.

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
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.clauseway.cargo.rules.History;
import org.clauseway.cargo.rules.Itinerary;
import org.clauseway.cargo.rules.Progress;
import org.clauseway.cargo.rules.Routing;
import org.clauseway.cargo.rules.Voyages;
import org.clauseway.functional.Nothing;
import org.clauseway.functional.tuples.Tuple5;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.vavr.control.Try;

public final class Shipping implements AutoCloseable {

	private final Transaction db;
	private final History history;
	private final Itinerary itinerary;
	private final Routing routing;
	private final Progress progress;

	private Shipping(Transaction db) {
		this.db = db;
		this.history = new History(db);
		this.itinerary = new Itinerary(db);
		this.routing = new Routing(db, itinerary, new Voyages(db));
		this.progress = new Progress(db, history, itinerary, routing);
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

	// -- commands: denials first, then the facts ---------------------------

	/** Booking: the cargo exists, with its origin and its first requirement. */
	public Try<Shipping> bookCargo(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return command(specificationDenials(origin, destination), () -> with(
				cargo(db, lval(trackingId), lval(origin)),
				routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline))));
	}

	/** The specification is a value: the old one goes whole, the new one lands; the cargo's origin stays. */
	public Try<Shipping> specifyNewRoute(String trackingId, String origin, String destination,
			Instant arrivalDeadline) {
		Unifiable<String> reason = lvar();
		List<String> denials = values(Query.of(routing.reroutingDenial(lval(trackingId), lval(origin),
				lval(destination), reason)).solve(reason));
		return command(denials, () -> {
			List<Literal> spent = routeSpecificationOf(trackingId)
					.map(old -> routeSpecification(db, lval(trackingId), lval(old.getOrigin()),
							lval(old.getDestination()), lval(old.getArrivalDeadline())))
					.map(Collections::singletonList).orElse(Collections.emptyList());
			return new Shipping(db.retracting(spent).asserting(
					routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline))));
		});
	}

	/** Registering a handling: HandlingEventFactory's checks, then the fact. */
	public Try<Shipping> registerHandling(String trackingId, HandlingType type, String location,
			Optional<String> voyageNumber, Instant completionTime, Instant registrationTime) {
		return command(registrationDenials(trackingId, type, location, voyageNumber),
				() -> with(handlingEvent(db, lval(trackingId), lval(type), lval(location), LOption.of(voyageNumber),
						lval(completionTime), lval(registrationTime))));
	}

	/** Every rule the registration would violate, by name; empty means allowed. */
	public List<String> registrationDenials(String trackingId, HandlingType type, String location,
			Optional<String> voyageNumber) {
		Unifiable<String> reason = lvar();
		return values(Query.of(history.registrationDenial(lval(trackingId), lval(type), lval(location),
				LOption.of(voyageNumber), reason)).solve(reason));
	}

	/** Every rule the specification would violate, by name; empty means allowed. */
	public List<String> specificationDenials(String origin, String destination) {
		Unifiable<String> reason = lvar();
		return values(Query.of(routing.specificationDenial(lval(origin), lval(destination), reason)).solve(reason));
	}

	private static Try<Shipping> command(List<String> denials, Supplier<Shipping> effect) {
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(effect.get());
	}

	private static <T> List<T> values(Stream<Reified<T>> answers) {
		return answers.map(Reified::get).collect(Collectors.toList());
	}

	/** The itinerary is a value: the old legs go whole, the new ones land in order. */
	public Shipping assignToRoute(String trackingId, List<Leg> legs) {
		List<Literal> spent = legLiterals(trackingId, itineraryOf(trackingId));
		return new Shipping(db.retracting(spent).asserting(legLiterals(trackingId, legs)));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			Instant completionTime, Instant registrationTime) {
		return with(handlingEvent(db, lval(trackingId), lval(type), lval(location), none(),
				lval(completionTime), lval(registrationTime)));
	}

	public Shipping withHandlingEvent(String trackingId, HandlingType type, String location,
			String voyageNumber, Instant completionTime, Instant registrationTime) {
		return with(handlingEvent(db, lval(trackingId), lval(type), lval(location), some(lval(voyageNumber)),
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
		LegColumns c = new LegColumns();
		return c.legs(Query.of(leg(db, lval(trackingId), index, c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime))
				.select(index, c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime).rows()
				.sorted(Comparator.comparing((Row row) -> row.get(index).get())))
				.collect(Collectors.toList());
	}

	/** The itineraries the schedule offers the cargo's specification. */
	public List<List<Leg>> routeCandidatesOf(String trackingId) {
		Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs = lvar();
		return Query.of(routing.candidate(lval(trackingId), legs)).solve(legs)
				.map(route -> route.get().elements()
						.map(leg -> leg.get())
						.map(leg -> new Leg(leg._1.get(), leg._2.get(), leg._3.get(), leg._4.get(), leg._5.get()))
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
	}

	public Optional<Leg> firstLegOf(String trackingId) {
		LegColumns c = new LegColumns();
		return c.legs(c.select(itinerary.firstLeg(lval(trackingId), c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime)))
				.findFirst();
	}

	public Optional<Leg> lastLegOf(String trackingId) {
		LegColumns c = new LegColumns();
		return c.legs(c.select(itinerary.lastLeg(lval(trackingId), c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime)))
				.findFirst();
	}

	public RoutingStatus routingStatusOf(String trackingId) {
		Unifiable<RoutingStatus> status = lvar();
		return first(routing.routingStatus(lval(trackingId), status), status)
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
	}

	/** The handlings the cargo's itinerary plans, in order; empty when it is not routed. */
	public List<HandlingActivity> planOf(String trackingId) {
		Unifiable<Integer> step = lvar();
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<LOption<String>> voyageNumber = lvar();
		return Query.of(itinerary.plannedHandling(lval(trackingId), step, type, location, voyageNumber))
				.select(step, type, location, voyageNumber).rows()
				.sorted(Comparator.comparing((Row row) -> row.get(step).get()))
				.map(row -> new HandlingActivity(row.get(type).get(), row.get(location).get(),
						row.get(voyageNumber).get().toOptional()))
				.collect(Collectors.toList());
	}

	/** Whether the cargo's itinerary accounts for the handling. */
	public boolean isExpected(String trackingId, HandlingEvent event) {
		return holds(itinerary.expected(lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber())));
	}

	/** The cargo's delivery, every field derived now from the facts. */
	public Delivery deliveryOf(String trackingId) {
		Unifiable<String> id = lval(trackingId);
		Unifiable<TransportStatus> transport = lvar();
		Unifiable<String> location = lvar();
		Unifiable<String> voyageNumber = lvar();
		Unifiable<Instant> eta = lvar();
		return new Delivery(
				routingStatusOf(trackingId),
				first(progress.transportStatus(id, transport), transport)
						.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId)),
				first(progress.lastKnownLocation(id, location), location),
				first(progress.currentVoyage(id, voyageNumber), voyageNumber),
				holds(progress.misdirected(id)),
				first(progress.eta(id, eta), eta),
				nextExpectedActivityOf(trackingId),
				holds(progress.unloadedAtDestination(id)));
	}

	private Optional<HandlingActivity> nextExpectedActivityOf(String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<LOption<String>> voyageNumber = lvar();
		return Query.of(progress.nextExpectedActivity(lval(trackingId), type, location, voyageNumber))
				.select(type, location, voyageNumber).rows()
				.map(row -> new HandlingActivity(row.get(type).get(), row.get(location).get(),
						row.get(voyageNumber).get().toOptional()))
				.findFirst();
	}

	/** The first answer for one variable of a goal, if any. */
	private static <T> Optional<T> first(Literal goal, Unifiable<T> out) {
		return Query.of(goal).solve(out).findFirst().map(Reified::get);
	}

	/** Whether a ground goal has an answer at all. */
	private static boolean holds(Literal goal) {
		return Query.of(goal).solve(lval(true)).findAny().isPresent();
	}

	/** The variables of one leg's columns and the row-to-value reading over them. */
	private static final class LegColumns {
		final Unifiable<String> voyageNumber = lvar();
		final Unifiable<String> load = lvar();
		final Unifiable<String> unload = lvar();
		final Unifiable<Instant> loadTime = lvar();
		final Unifiable<Instant> unloadTime = lvar();

		Stream<Row> select(Literal legs) {
			return Query.of(legs).select(voyageNumber, load, unload, loadTime, unloadTime).rows();
		}

		Stream<Leg> legs(Stream<Row> rows) {
			return rows.map(row -> new Leg(row.get(voyageNumber).get(), row.get(load).get(), row.get(unload).get(),
					row.get(loadTime).get(), row.get(unloadTime).get()));
		}
	}

	/** The cargo's handling events, distinct, in completion order. */
	public List<HandlingEvent> handlingHistoryOf(String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<LOption<String>> voyageNumber = lvar();
		Unifiable<Instant> completed = lvar();
		return events(Query.of(handlingEvent(db, lval(trackingId), type, location, voyageNumber, completed, lvar()))
				.select(type, location, voyageNumber, completed).rows(), type, location, voyageNumber, completed)
				.sorted(Comparator.comparing(HandlingEvent::getCompletionTime))
				.collect(Collectors.toList());
	}

	/** The most recently completed handling of the cargo, if any. */
	public Optional<HandlingEvent> lastEventOf(String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<LOption<String>> voyageNumber = lvar();
		Unifiable<Instant> completed = lvar();
		return events(Query.of(history.lastEvent(lval(trackingId), type, location, voyageNumber, completed))
				.select(type, location, voyageNumber, completed).rows(), type, location, voyageNumber, completed)
				.findFirst();
	}

	private static Stream<HandlingEvent> events(Stream<Row> rows, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return rows.map(row -> new HandlingEvent(row.get(type).get(), row.get(location).get(),
				row.get(voyageNumber).get().toOptional(), row.get(completed).get()));
	}
}
