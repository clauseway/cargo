package org.clauseway.shipping.cargo;

// ABOUTME: The Cargo aggregate root: the booking, its route specification and its
// ABOUTME: itinerary as relations written here alone, read as rules, changed by its commands.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.Leg;
import org.clauseway.shipping.RouteSpecification;
import org.clauseway.shipping.Schema;

public final class Cargo {

	private final AnswerSource db;

	public Cargo(AnswerSource db) {
		this.db = db;
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

	/** The aggregate on a SQL source: the deadline and the legs' times as timestamps. */
	public static SerializableSource onSql(SerializableSource source) {
		return source
				.withCodec(routeSpecification(null, lvar(), lvar(), lvar(), Schema.INSTANT.arg()))
				.withCodec(leg(null, lvar(), lvar(), lvar(), lvar(), lvar(), Schema.INSTANT.arg(), Schema.INSTANT.arg()));
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

	// -- the commands: the facts each lands; the denials are the caller's ----

	/** Booking: the cargo exists, with its origin and its first requirement. */
	public static Transaction book(Transaction db, String trackingId, String origin, String destination,
			Instant arrivalDeadline) {
		return db.asserting(
				cargo(db, lval(trackingId), lval(origin)),
				routeSpecification(db, lval(trackingId), lval(origin), lval(destination), lval(arrivalDeadline)));
	}

	/** The specification is a value: the old one goes whole, the new one lands, the same one is nothing; the cargo's origin stays. */
	public static Transaction specify(Transaction db, String trackingId, Optional<RouteSpecification> current,
			RouteSpecification next) {
		if (current.filter(next::equals).isPresent()) {
			return db;
		}
		List<Literal> spent = current.map(old -> routeSpecificationOf(db, trackingId, old))
				.map(Collections::singletonList).orElse(Collections.emptyList());
		return db.retracting(spent).asserting(routeSpecificationOf(db, trackingId, next));
	}

	/** The itinerary is a value: the old legs go, the new ones land in order; a leg both have at one index is neither. */
	public static Transaction route(Transaction db, String trackingId, List<Leg> current, List<Leg> legs) {
		return db.retracting(legsDiffering(db, trackingId, current, legs))
				.asserting(legsDiffering(db, trackingId, legs, current));
	}

	private static Literal routeSpecificationOf(AnswerSource db, String trackingId, RouteSpecification spec) {
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
}
