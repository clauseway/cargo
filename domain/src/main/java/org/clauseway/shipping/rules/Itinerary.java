package org.clauseway.shipping.rules;

// ABOUTME: The itinerary read as rules: its first and last legs by index, and
// ABOUTME: the sequence of handlings it plans — which is what it accounts for.

import static org.clauseway.shipping.Schema.leg;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import org.clauseway.shipping.HandlingType;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;

public final class Itinerary {

	private final AnswerSource db;

	public Itinerary(AnswerSource db) {
		this.db = db;
	}

	/** The cargo is routed: it has at least one leg. */
	public Literal anyLeg(Unifiable<String> trackingId) {
		return Literal.relation(Itinerary.class, "anyLeg")
				.arg("trackingId", trackingId)
				.solving(leg(db, trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar()));
	}

	/** The itinerary's first leg: the one at index 0. */
	public Literal firstLeg(Unifiable<String> trackingId, Unifiable<String> voyageNumber,
			Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		return Literal.relation(Itinerary.class, "firstLeg")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(leg(db, trackingId, lval(0), voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime));
	}

	/** The index of the itinerary's last leg: one with no leg at the next index. */
	public Literal lastIndex(Unifiable<String> trackingId, Unifiable<Integer> index) {
		Unifiable<Integer> next = lvar();
		return Literal.relation(Itinerary.class, "lastIndex")
				.arg("trackingId", trackingId)
				.arg("index", index)
				.solving(leg(db, trackingId, index, lvar(), lvar(), lvar(), lvar(), lvar())
						.and(Ints.addo(index, lval(1), next))
						.and(exclude(leg(db, trackingId, next, projected(), projected(), projected(),
								projected(), projected()))));
	}

	/** The itinerary's last leg. */
	public Literal lastLeg(Unifiable<String> trackingId, Unifiable<String> voyageNumber,
			Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		Unifiable<Integer> index = lvar();
		return Literal.relation(Itinerary.class, "lastLeg")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(leg(db, trackingId, index, voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime)
						.and(lastIndex(trackingId, index)));
	}

	/**
	 * The sequence of handlings the itinerary plans: a receipt at the first
	 * leg's port (step 0); for the leg at index i, a load at its port on its
	 * voyage (step 2i+1) and an unload where it lands (step 2i+2); and a
	 * claim where the last leg lands. Customs is not planned; it may happen
	 * anywhere.
	 */
	public Literal plannedHandling(Unifiable<String> trackingId, Unifiable<Integer> step,
			Unifiable<HandlingType> type, Unifiable<String> location, Unifiable<LOption<String>> voyageNumber) {
		Unifiable<Integer> index = lvar();
		Unifiable<Integer> twice = lvar();
		Unifiable<String> legVoyage = lvar();
		return Literal.relation(Itinerary.class, "plannedHandling")
				.arg("trackingId", trackingId)
				.arg("step", step)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(type.unifies(HandlingType.RECEIVE)
						.and(firstLeg(trackingId, lvar(), location, lvar(), lvar(), lvar()))
						.and(step.unifies(0))
						.and(voyageNumber.unifies(none()))
						.or(type.unifies(HandlingType.LOAD)
								.and(leg(db, trackingId, index, legVoyage, location, lvar(), lvar(), lvar()))
								.and(voyageNumber.unifies(some(legVoyage)))
								.and(Ints.multo(index, lval(2), twice))
								.and(Ints.addo(twice, lval(1), step)))
						.or(type.unifies(HandlingType.UNLOAD)
								.and(leg(db, trackingId, index, legVoyage, lvar(), location, lvar(), lvar()))
								.and(voyageNumber.unifies(some(legVoyage)))
								.and(Ints.multo(index, lval(2), twice))
								.and(Ints.addo(twice, lval(2), step)))
						.or(type.unifies(HandlingType.CLAIM)
								.and(leg(db, trackingId, index, lvar(), lvar(), location, lvar(), lvar()))
								.and(lastIndex(trackingId, index))
								.and(Ints.multo(index, lval(2), twice))
								.and(Ints.addo(twice, lval(3), step))
								.and(voyageNumber.unifies(none()))));
	}

	/**
	 * The handling the itinerary accounts for: a planned one, customs
	 * anywhere; and everything, for a cargo with no itinerary.
	 */
	public Literal expected(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber) {
		return Literal.relation(Itinerary.class, "expected")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(exclude(anyLeg(trackingId))
						.or(plannedHandling(trackingId, lvar(), type, location, voyageNumber))
						.or(type.unifies(HandlingType.CUSTOMS)));
	}
}
