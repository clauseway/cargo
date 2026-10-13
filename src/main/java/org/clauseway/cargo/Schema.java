package org.clauseway.cargo;

// ABOUTME: The shipping schema as function-shaped relations — dddsample's three
// ABOUTME: aggregates flattened to facts, names stated once, lists as index columns.

import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Schema {

	/** A port, by UN/LOCODE. */
	public static Literal location(AnswerSource db, Unifiable<String> unlocode, Unifiable<String> name) {
		return Literal.relation(Schema.class, "location")
				.arg("unlocode", unlocode).indexed()
				.arg("name", name)
				.from(db);
	}

	/** A voyage, by number; its schedule is the carrier movements naming it. */
	public static Literal voyage(AnswerSource db, Unifiable<String> voyageNumber) {
		return Literal.relation(Schema.class, "voyage")
				.arg("voyageNumber", voyageNumber).indexed()
				.from(db);
	}

	/** One sailing of a voyage: from a port at a time, to a port at a time. */
	public static Literal carrierMovement(AnswerSource db, Unifiable<String> voyageNumber,
			Unifiable<String> departureLocation, Unifiable<String> arrivalLocation,
			Unifiable<Instant> departureTime, Unifiable<Instant> arrivalTime) {
		return Literal.relation(Schema.class, "carrierMovement")
				.arg("voyageNumber", voyageNumber).indexed()
				.arg("departureLocation", departureLocation).indexed()
				.arg("arrivalLocation", arrivalLocation).indexed()
				.arg("departureTime", departureTime)
				.arg("arrivalTime", arrivalTime)
				.from(db);
	}

	/** A booked cargo, by tracking id; the origin never changes after booking. */
	public static Literal cargo(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> origin) {
		return Literal.relation(Schema.class, "cargo")
				.arg("trackingId", trackingId).indexed()
				.arg("origin", origin)
				.from(db);
	}

	/** The cargo's current requirement; replaced whole when the customer re-specifies. */
	public static Literal routeSpecification(AnswerSource db, Unifiable<String> trackingId,
			Unifiable<String> origin, Unifiable<String> destination, Unifiable<Instant> arrivalDeadline) {
		return Literal.relation(Schema.class, "routeSpecification")
				.arg("trackingId", trackingId).indexed()
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("arrivalDeadline", arrivalDeadline)
				.from(db);
	}

	/** One leg of the cargo's itinerary; the index is the itinerary's order. */
	public static Literal leg(AnswerSource db, Unifiable<String> trackingId, Unifiable<Integer> index,
			Unifiable<String> voyageNumber, Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		return Literal.relation(Schema.class, "leg")
				.arg("trackingId", trackingId).indexed()
				.arg("index", index)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.from(db);
	}

	/**
	 * A handling event as reported — what happened to the cargo, where, when
	 * it happened and when the report arrived; the port-side kinds have no
	 * voyage. A handling reported twice is two rows; every rule projects the
	 * registration time away, so it is one event.
	 */
	public static Literal handlingEvent(AnswerSource db, Unifiable<String> trackingId,
			Unifiable<HandlingType> type, Unifiable<String> location, Unifiable<LOption<String>> voyageNumber,
			Unifiable<Instant> completionTime, Unifiable<Instant> registrationTime) {
		return Literal.relation(Schema.class, "handlingEvent")
				.arg("trackingId", trackingId).indexed()
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completionTime", completionTime)
				.arg("registrationTime", registrationTime)
				.from(db);
	}
}
