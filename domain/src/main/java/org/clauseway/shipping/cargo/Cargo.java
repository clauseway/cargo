package org.clauseway.shipping.cargo;

// ABOUTME: The Cargo aggregate root, the door Java calls: its relations package-private and written by its
// ABOUTME: commands alone, its parts read as values, its delivery row derived by the rules and stored here.

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
import org.clauseway.shipping.HandlingActivity;
import org.clauseway.shipping.HandlingEvent;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Leg;
import org.clauseway.shipping.RouteSpecification;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.TransportStatus;
import org.clauseway.vavr.control.Try;

public final class Cargo {

	private final Transaction db;

	/** The aggregate over one transaction value. */
	public Cargo(Transaction db) {
		this.db = db;
	}

	// -- the essential state: five relations, this package's alone ---------

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

	/** The delivery as stored: one row per cargo, the absent fields as none; replaced whole by every command. */
	static Literal delivery(AnswerSource db, Unifiable<String> trackingId, Unifiable<RoutingStatus> routingStatus,
			Unifiable<TransportStatus> transportStatus, Unifiable<LOption<String>> lastKnownLocation,
			Unifiable<LOption<String>> currentVoyage, Unifiable<Boolean> misdirected, Unifiable<LOption<Instant>> eta,
			Unifiable<LOption<HandlingType>> nextType, Unifiable<LOption<String>> nextLocation,
			Unifiable<LOption<String>> nextVoyage, Unifiable<Boolean> unloadedAtDestination) {
		return Literal.relation(Cargo.class, "delivery")
				.arg("trackingId", trackingId).indexed()
				.arg("routingStatus", routingStatus)
				.arg("transportStatus", transportStatus)
				.arg("lastKnownLocation", lastKnownLocation)
				.arg("currentVoyage", currentVoyage)
				.arg("misdirected", misdirected)
				.arg("eta", eta)
				.arg("nextType", nextType)
				.arg("nextLocation", nextLocation)
				.arg("nextVoyage", nextVoyage)
				.arg("unloadedAtDestination", unloadedAtDestination)
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
		DeliveryColumns c = new DeliveryColumns();
		return c.select(c.delivery(db, lval(trackingId)))
				.map(c::delivery)
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

	/** The variables of one delivery row's columns, shared between a question and its template, and the row-to-value reading over them. */
	private static final class DeliveryColumns {
		final Unifiable<RoutingStatus> routingStatus = lvar();
		final Unifiable<TransportStatus> transportStatus = lvar();
		final Unifiable<LOption<String>> lastKnownLocation = lvar();
		final Unifiable<LOption<String>> currentVoyage = lvar();
		final Unifiable<Boolean> misdirected = lvar();
		final Unifiable<LOption<Instant>> eta = lvar();
		final Unifiable<LOption<HandlingType>> nextType = lvar();
		final Unifiable<LOption<String>> nextLocation = lvar();
		final Unifiable<LOption<String>> nextVoyage = lvar();
		final Unifiable<Boolean> unloadedAtDestination = lvar();

		Stream<Row> select(Literal delivery) {
			return Query.of(delivery).select(routingStatus, transportStatus, lastKnownLocation, currentVoyage, misdirected, eta,
					nextType, nextLocation, nextVoyage, unloadedAtDestination).rows();
		}

		DeliveryDTO delivery(Row row) {
			return new DeliveryDTO(
					row.get(routingStatus).get(),
					row.get(transportStatus).get(),
					row.get(lastKnownLocation).get().toOptional(),
					row.get(currentVoyage).get().toOptional(),
					row.get(misdirected).get(),
					row.get(eta).get().toOptional(),
					row.get(nextType).get().toOptional().map(type -> new HandlingActivity(type,
							row.get(nextLocation).get().toOptional().get(),
							row.get(nextVoyage).get().toOptional())),
					row.get(unloadedAtDestination).get());
		}

		private Literal calculated(AnswerSource db, Unifiable<String> trackingId) {
			return CargoRules.calculated(db, trackingId, routingStatus, transportStatus, lastKnownLocation, currentVoyage,
					misdirected, eta, nextType, nextLocation, nextVoyage, unloadedAtDestination);
		}

		private Literal delivery(AnswerSource db, Unifiable<String> trackingId) {
			return Cargo.delivery(db, trackingId, routingStatus, transportStatus, lastKnownLocation, currentVoyage,
					misdirected, eta, nextType, nextLocation, nextVoyage, unloadedAtDestination);
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

	// -- the commands: each refused by name, or its facts landed and the delivery re-derived -------------

	/** Booking: the cargo exists, with its origin, its first requirement and its first delivery. */
	public Try<Transaction> book(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return Command.guarded(bookingDenials(trackingId, origin, destination), () -> delivered(trackingId, db.asserting(
				cargo(db, lval(trackingId), lval(origin)),
				routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline)))));
	}

	/** Cargo.specifyNewRoute. The specification is a value: the old one goes whole, the new one lands, the same one is nothing; the cargo's origin stays. */
	public Try<Transaction> specify(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return Command.guarded(reroutingDenials(trackingId, origin, destination), () -> {
			RouteSpecification next = new RouteSpecification(origin, destination, arrivalDeadline);
			Optional<RouteSpecification> current = routeSpecificationOf(trackingId);
			if (current.filter(next::equals).isPresent()) {
				return db;
			}
			return delivered(trackingId, db
					.retracting(rows(current.map(old -> specified(db, trackingId, old))))
					.asserting(specified(db, trackingId, next)));
		});
	}

	/** Cargo.assignToRoute. The itinerary is a value: the old legs go, the new ones land in order; a leg both have at one index is neither. */
	public Try<Transaction> route(String trackingId, List<Leg> legs) {
		return Command.guarded(routingDenials(trackingId, legs), () -> {
			List<Leg> current = itineraryOf(trackingId);
			return delivered(trackingId, db
					.retracting(legsDiffering(db, trackingId, current, legs))
					.asserting(legsDiffering(db, trackingId, legs, current)));
		});
	}

	/** CargoInspectionService.inspectCargo, the cargo's half: shown the handling side's latest event, it keeps it and re-derives its delivery. */
	public Try<Transaction> inspect(String trackingId, HandlingEvent latest) {
		return Command.guarded(bookedDenials(trackingId), () -> delivered(trackingId, db
				.retracting(lastHandlingRows(db, trackingId))
				.asserting(shown(db, trackingId, latest))));
	}

	/** The inspection of a cargo the handling side has nothing for: it keeps no handling and re-derives its delivery. */
	public Try<Transaction> inspect(String trackingId) {
		return Command.guarded(bookedDenials(trackingId), () -> delivered(trackingId, db
				.retracting(lastHandlingRows(db, trackingId))));
	}

	/**
	 * Every command's last step: the stored delivery row leaves and the row the
	 * rules calculate over the staged state lands; an unchanged one cancels to nothing.
	 */
	private static Transaction delivered(String trackingId, Transaction staged) {
		return staged
				.retracting(storedRows(staged, trackingId))
				.asserting(calculatedRows(staged, trackingId));
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

	/** The delivery row the cargo holds, as a retraction takes it. */
	private static List<Answer<Relation>> storedRows(AnswerSource db, String trackingId) {
		DeliveryColumns c = new DeliveryColumns();
		return rows(c.delivery(db, lval(trackingId)), c.delivery(null, lval(trackingId)));
	}

	/** Delivery.derivedFrom: the delivery the rules calculate now, as the row to land. */
	private static List<Answer<Relation>> calculatedRows(AnswerSource db, String trackingId) {
		DeliveryColumns c = new DeliveryColumns();
		return rows(c.calculated(db, lval(trackingId)), c.delivery(null, lval(trackingId)));
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
	private static List<Answer<Relation>> rows(Literal question, Literal template) {
		return new BreadthFirstScheduler<>(Question.select(question, template)).get();
	}

	/** A fact that may be absent, as the list a write door takes. */
	private static List<Literal> rows(Optional<Literal> fact) {
		return fact.map(Collections::singletonList).orElse(Collections.emptyList());
	}
}
