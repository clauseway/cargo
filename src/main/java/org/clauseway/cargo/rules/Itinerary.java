package org.clauseway.cargo.rules;

// ABOUTME: The itinerary read as rules: its first and last legs by index, and
// ABOUTME: which handling it accounts for — dddsample's Itinerary.isExpected.

import static org.clauseway.cargo.Schema.leg;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import org.clauseway.cargo.HandlingType;
import org.clauseway.logic.finitedomain.Ints;
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

	/** The itinerary's last leg: one with no leg at the next index. */
	public Literal lastLeg(Unifiable<String> trackingId, Unifiable<String> voyageNumber,
			Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		Unifiable<Integer> index = lvar();
		Unifiable<Integer> next = lvar();
		return Literal.relation(Itinerary.class, "lastLeg")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(leg(db, trackingId, index, voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime)
						.and(Ints.addo(index, lval(1), next))
						.and(exclude(leg(db, trackingId, next, projected(), projected(), projected(),
								projected(), projected()))));
	}

	/**
	 * The handling the itinerary accounts for — a receipt at the first leg's
	 * port, a load or unload at a leg's port on that leg's voyage, a claim at
	 * the last leg's port, customs anywhere; and everything, for a cargo with
	 * no itinerary.
	 */
	public Literal expected(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber) {
		return Literal.relation(Itinerary.class, "expected")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(exclude(anyLeg(trackingId))
						.or(type.unifies(HandlingType.RECEIVE)
								.and(firstLeg(trackingId, lvar(), location, lvar(), lvar(), lvar())))
						.or(type.unifies(HandlingType.LOAD)
								.and(leg(db, trackingId, lvar(), voyageNumber, location, lvar(), lvar(), lvar())))
						.or(type.unifies(HandlingType.UNLOAD)
								.and(leg(db, trackingId, lvar(), voyageNumber, lvar(), location, lvar(), lvar())))
						.or(type.unifies(HandlingType.CLAIM)
								.and(lastLeg(trackingId, lvar(), lvar(), location, lvar(), lvar())))
						.or(type.unifies(HandlingType.CUSTOMS)));
	}
}
