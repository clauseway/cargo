package org.clauseway.shipping.cargo;

// ABOUTME: The Cargo aggregate root, the door Java calls: its relations package-private and written by its
// ABOUTME: commands alone, its parts read as values, its delivery refreshed by every command.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.Reads.values;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.solving.Answer;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.relations.Question;
import org.clauseway.pldb.relations.Relation;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.Command;
import org.clauseway.shipping.DeliveryDTO;
import org.clauseway.shipping.HandlingEvent;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Leg;
import org.clauseway.shipping.RouteSpecification;
import org.clauseway.vavr.control.Try;

public final class Cargo {

	private final Transaction db;

	/** The aggregate over one transaction value. */
	public Cargo(Transaction db) {
		this.db = db;
	}

	// -- the essential state: four relations, this package's alone; the delivery is its own ---------

	/** A booked cargo, by tracking id; the origin never changes after booking. */
	static Literal cargo(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> origin) {
		return Literal.relation(Cargo.class, "cargo")
				.arg("trackingId", trackingId).indexed()
				.arg("origin", origin)
				.from(db);
	}

	/** The cargo's current requirement; replaced whole when the customer re-specifies. */
	static Literal routeSpecification(AnswerSource db, Unifiable<String> trackingId,
			Unifiable<String> origin, Unifiable<String> destination, Unifiable<Instant> arrivalDeadline) {
		return Literal.relation(Cargo.class, "routeSpecification")
				.arg("trackingId", trackingId).indexed()
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("arrivalDeadline", arrivalDeadline)
				.from(db);
	}

	/** One leg of the cargo's itinerary; the index is the itinerary's order. */
	static Literal leg(AnswerSource db, Unifiable<String> trackingId, Unifiable<Integer> index,
			Unifiable<String> voyageNumber, Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		return Literal.relation(Cargo.class, "leg")
				.arg("trackingId", trackingId).indexed()
				.arg("index", index)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.from(db);
	}

	/** The handling the cargo was last shown — Delivery.lastEvent; replaced when an inspection brings a later one. */
	static Literal lastHandling(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(Cargo.class, "lastHandling")
				.arg("trackingId", trackingId).indexed()
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.from(db);
	}

	// -- the parts, as values ----------------------------------------------

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

	public Optional<Leg> firstLegOf(String trackingId) {
		LegColumns c = new LegColumns();
		return c.legs(c.select(CargoRules.firstLeg(db, lval(trackingId), c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime)))
				.findFirst();
	}

	public Optional<Leg> lastLegOf(String trackingId) {
		LegColumns c = new LegColumns();
		return c.legs(c.select(CargoRules.lastLeg(db, lval(trackingId), c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime)))
				.findFirst();
	}

	/** The cargo's delivery as its commands last stored it. */
	public DeliveryDTO deliveryOf(String trackingId) {
		Delivery row = Delivery.fresh(lval(trackingId));
		return row.select(Delivery.stored(db, row))
				.map(row::value)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
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

	// -- the denials, by name; empty means allowed ---------------------------

	private List<String> bookingDenials(String trackingId, String origin, String destination) {
		return reasons(reason -> CargoRules.bookingDenial(db, lval(trackingId), lval(origin), lval(destination), reason));
	}

	private List<String> reroutingDenials(String trackingId, String origin, String destination) {
		return reasons(reason -> CargoRules.reroutingDenial(db, lval(trackingId), lval(origin), lval(destination), reason));
	}

	/** The denial every command on a cargo that is not booked gets. */
	private List<String> bookedDenials(String trackingId) {
		return reasons(reason -> CargoRules.routingDenial(db, lval(trackingId), reason));
	}

	private List<String> routingDenials(String trackingId, List<Leg> legs) {
		Stream<String> booked = bookedDenials(trackingId).stream();
		Stream<String> shape = legs.isEmpty() ? Stream.of("empty itinerary") : Stream.empty();
		Stream<String> parts = legs.stream().flatMap(leg -> reasons(reason -> CargoRules.legDenial(db,
				lval(leg.getVoyageNumber()), lval(leg.getLoadLocation()), lval(leg.getUnloadLocation()), reason)).stream());
		return Stream.concat(booked, Stream.concat(shape, parts)).distinct().collect(Collectors.toList());
	}

	/** Every reason a denial rule gives. */
	private static List<String> reasons(Function<Unifiable<String>, Literal> denial) {
		Unifiable<String> reason = lvar();
		return values(denial.apply(reason), reason);
	}

	// -- the commands: each refused by name, or its facts landed and the delivery refreshed -------------

	/** Booking: the cargo exists, with its origin, its first requirement and its first delivery. */
	public Try<Transaction> book(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return Command.guarded(bookingDenials(trackingId, origin, destination), () -> Delivery.refresh(db.asserting(
				cargo(db, lval(trackingId), lval(origin)),
				routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline))), trackingId));
	}

	/** Cargo.specifyNewRoute. The specification is a value: the old one goes whole, the new one lands, the same one is nothing; the cargo's origin stays. */
	public Try<Transaction> specify(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return Command.guarded(reroutingDenials(trackingId, origin, destination), () -> {
			RouteSpecification next = new RouteSpecification(origin, destination, arrivalDeadline);
			Optional<RouteSpecification> current = routeSpecificationOf(trackingId);
			if (current.filter(next::equals).isPresent()) {
				return db;
			}
			return Delivery.refresh(db
					.retracting(rows(current.map(old -> specified(db, trackingId, old))))
					.asserting(specified(db, trackingId, next)), trackingId);
		});
	}

	/** Cargo.assignToRoute. The itinerary is a value: the old legs go, the new ones land in order; a leg both have at one index is neither. */
	public Try<Transaction> route(String trackingId, List<Leg> legs) {
		return Command.guarded(routingDenials(trackingId, legs), () -> {
			List<Leg> current = itineraryOf(trackingId);
			return Delivery.refresh(db
					.retracting(legsDiffering(db, trackingId, current, legs))
					.asserting(legsDiffering(db, trackingId, legs, current)), trackingId);
		});
	}

	/** CargoInspectionService.inspectCargo, the cargo's half: shown the handling side's latest event, it keeps it and re-derives its delivery. */
	public Try<Transaction> inspect(String trackingId, HandlingEvent latest) {
		return Command.guarded(bookedDenials(trackingId), () -> Delivery.refresh(db
				.retracting(lastHandlingRows(db, trackingId))
				.asserting(shown(db, trackingId, latest)), trackingId));
	}

	/** The inspection of a cargo the handling side has nothing for: it keeps no handling and re-derives its delivery. */
	public Try<Transaction> inspect(String trackingId) {
		return Command.guarded(bookedDenials(trackingId), () -> Delivery.refresh(db
				.retracting(lastHandlingRows(db, trackingId)), trackingId));
	}

	// -- the facts and rows the commands land and retract --------------------

	/** The cargo specified as given, as a fact. */
	private static Literal specified(AnswerSource db, String trackingId, RouteSpecification spec) {
		return routeSpecification(db, lval(trackingId), lval(spec.getOrigin()), lval(spec.getDestination()),
				lval(spec.getArrivalDeadline()));
	}

	/** The legs of one itinerary the other does not have at the same index, as facts. */
	private static List<Literal> legsDiffering(AnswerSource db, String trackingId, List<Leg> legs, List<Leg> other) {
		return IntStream.range(0, legs.size())
				.filter(i -> i >= other.size() || !legs.get(i).equals(other.get(i)))
				.mapToObj(i -> leg(db, lval(trackingId), lval(i), lval(legs.get(i).getVoyageNumber()),
						lval(legs.get(i).getLoadLocation()), lval(legs.get(i).getUnloadLocation()),
						lval(legs.get(i).getLoadTime()), lval(legs.get(i).getUnloadTime())))
				.collect(Collectors.toList());
	}

	/** A handling as the cargo keeps it, as a fact. */
	private static Literal shown(AnswerSource db, String trackingId, HandlingEvent event) {
		return lastHandling(db, lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber()), lval(event.getCompletionTime()));
	}

	/** The handling the cargo holds, as a retraction takes it. */
	private static List<Answer<Relation>> lastHandlingRows(AnswerSource db, String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<LOption<String>> voyageNumber = lvar();
		Unifiable<Instant> completed = lvar();
		return rows(lastHandling(db, lval(trackingId), type, location, voyageNumber, completed),
				lastHandling(null, lval(trackingId), type, location, voyageNumber, completed));
	}

	/** The question's answers in the template's shape, as rows a write door takes. */
	static List<Answer<Relation>> rows(Literal question, Literal template) {
		return new BreadthFirstScheduler<>(Question.select(question, template)).get();
	}

	/** A fact that may be absent, as the list a write door takes. */
	private static List<Literal> rows(Optional<Literal> fact) {
		return fact.map(Collections::singletonList).orElse(Collections.emptyList());
	}
}
