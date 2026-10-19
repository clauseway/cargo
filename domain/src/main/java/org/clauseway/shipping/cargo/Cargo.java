package org.clauseway.shipping.cargo;

// ABOUTME: The Cargo aggregate root: the booking, its route specification, its itinerary and its
// ABOUTME: delivery as relations written here alone, read as rules and values, changed by commands that guard themselves.

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
import lombok.Getter;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.Command;
import org.clauseway.shipping.HandlingEvent;
import org.clauseway.shipping.Leg;
import org.clauseway.shipping.RouteSpecification;
import org.clauseway.shipping.Schema;
import org.clauseway.shipping.location.Locations;
import org.clauseway.shipping.voyage.Voyages;
import org.clauseway.vavr.control.Try;

public final class Cargo {

	private final Transaction db;
	@Getter
	private final Itinerary itinerary;
	@Getter
	private final Routing routing;
	@Getter
	private final Delivery delivery;

	/** The aggregate over one transaction value: its rules, and the rules they read, bound to it. */
	public Cargo(Transaction db) {
		this.db = db;
		this.itinerary = new Itinerary(db);
		this.routing = new Routing(db, itinerary, new Voyages(db), new Locations(db));
		this.delivery = new Delivery(db, itinerary, routing);
	}

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

	/** The aggregate on a SQL source: the deadline and the legs' times as timestamps, and the delivery's encodings. */
	public static SerializableSource onSql(SerializableSource source) {
		return Delivery.onSql(source
				.withCodec(routeSpecification(null, lvar(), lvar(), lvar(), Schema.INSTANT.arg()))
				.withCodec(leg(null, lvar(), lvar(), lvar(), lvar(), lvar(), Schema.INSTANT.arg(), Schema.INSTANT.arg())));
	}

	// -- the read face: what other aggregates and the tracking may see ------

	/** The cargo is booked. */
	public Literal known(Unifiable<String> trackingId) {
		return Literal.relation(Cargo.class, "known")
				.arg("trackingId", trackingId)
				.solving(cargo(db, trackingId, lvar()));
	}

	/** The cargo's current route specification. */
	public Literal specification(Unifiable<String> trackingId, Unifiable<String> origin,
			Unifiable<String> destination, Unifiable<Instant> arrivalDeadline) {
		return Literal.relation(Cargo.class, "specification")
				.arg("trackingId", trackingId)
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("arrivalDeadline", arrivalDeadline)
				.solving(routeSpecification(db, trackingId, origin, destination, arrivalDeadline));
	}

	/** The cargo's itinerary, leg by leg, each with its index. */
	public Literal itinerary(Unifiable<String> trackingId, Unifiable<Integer> index,
			Unifiable<String> voyageNumber, Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		return Literal.relation(Cargo.class, "itinerary")
				.arg("trackingId", trackingId)
				.arg("index", index)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(leg(db, trackingId, index, voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime));
	}

	// -- the parts, as values ----------------------------------------------

	public Optional<RouteSpecification> routeSpecificationOf(String trackingId) {
		Unifiable<String> origin = lvar();
		Unifiable<String> destination = lvar();
		Unifiable<Instant> deadline = lvar();
		return Query.of(specification(lval(trackingId), origin, destination, deadline))
				.select(origin, destination, deadline).rows()
				.map(row -> new RouteSpecification(row.get(origin).get(), row.get(destination).get(),
						row.get(deadline).get()))
				.findFirst();
	}

	/** The cargo's legs in itinerary order; empty when it is not routed. */
	public List<Leg> itineraryOf(String trackingId) {
		Unifiable<Integer> index = lvar();
		LegColumns c = new LegColumns();
		return c.legs(Query.of(itinerary(lval(trackingId), index, c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime))
				.select(index, c.voyageNumber, c.load, c.unload, c.loadTime, c.unloadTime).rows()
				.sorted(Comparator.comparing((Row row) -> row.get(index).get())))
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
		return reasons(reason -> routing.bookingDenial(lval(trackingId), lval(origin), lval(destination), reason));
	}

	private List<String> reroutingDenials(String trackingId, String origin, String destination) {
		return reasons(reason -> routing.reroutingDenial(lval(trackingId), lval(origin), lval(destination), reason));
	}

	/** The denial every command on a cargo that is not booked gets. */
	private List<String> bookedDenials(String trackingId) {
		return reasons(reason -> routing.routingDenial(lval(trackingId), reason));
	}

	private List<String> routingDenials(String trackingId, List<Leg> legs) {
		Stream<String> booked = bookedDenials(trackingId).stream();
		Stream<String> shape = legs.isEmpty() ? Stream.of("empty itinerary") : Stream.empty();
		Stream<String> parts = legs.stream().flatMap(leg -> reasons(reason -> routing.legDenial(
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
	public Try<Transaction> inspect(String trackingId, Optional<HandlingEvent> latest) {
		return Command.guarded(bookedDenials(trackingId), () -> delivered(trackingId, db
				.retracting(delivery.lastHandlingRows(trackingId))
				.asserting(rows(latest.map(event -> Delivery.shown(db, trackingId, event))))));
	}

	/**
	 * Every command's last step: the stored delivery row leaves and the row the
	 * rules calculate over the staged state lands; an unchanged one cancels to nothing.
	 */
	private Transaction delivered(String trackingId, Transaction staged) {
		return staged
				.retracting(delivery.storedRows(trackingId))
				.asserting(new Cargo(staged).delivery.calculatedRows(trackingId));
	}

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

	/** A fact that may be absent, as the list a write door takes. */
	private static List<Literal> rows(Optional<Literal> fact) {
		return fact.map(Collections::singletonList).orElse(Collections.emptyList());
	}
}
