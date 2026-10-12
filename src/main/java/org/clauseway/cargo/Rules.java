package org.clauseway.cargo;

// ABOUTME: The derived relations over the shipping facts — dddsample's
// ABOUTME: Delivery, Itinerary and RouteSpecification calculations as rules.

import static org.clauseway.cargo.Schema.handlingEvent;
import static org.clauseway.cargo.Schema.leg;
import static org.clauseway.cargo.Schema.routeSpecification;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;

final class Rules {

	private final AnswerSource db;

	Rules(AnswerSource db) {
		this.db = db;
	}

	/** Some handling of the cargo completed after the given time. */
	Literal later(Unifiable<String> trackingId, Unifiable<Instant> time) {
		Unifiable<Instant> completed = lvar();
		return Literal.relation(Rules.class, "later")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(handlingEvent(db, trackingId, lvar(), lvar(), lvar(), completed, lvar())
						.and(Instants.lss(time, completed)));
	}

	/** The cargo is routed: it has at least one leg. */
	Literal anyLeg(Unifiable<String> trackingId) {
		return Literal.relation(Rules.class, "anyLeg")
				.arg("trackingId", trackingId)
				.solving(leg(db, trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar()));
	}

	/** The itinerary's first leg: the one at index 0. */
	Literal firstLeg(Unifiable<String> trackingId, Unifiable<String> voyageNumber, Unifiable<String> loadLocation,
			Unifiable<String> unloadLocation, Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		return Literal.relation(Rules.class, "firstLeg")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(leg(db, trackingId, lval(0), voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime));
	}

	/** The itinerary's last leg: one with no leg at the next index. */
	Literal lastLeg(Unifiable<String> trackingId, Unifiable<String> voyageNumber, Unifiable<String> loadLocation,
			Unifiable<String> unloadLocation, Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		Unifiable<Integer> index = lvar();
		Unifiable<Integer> next = lvar();
		return Literal.relation(Rules.class, "lastLeg")
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
	 * Itinerary.isExpected: the handling the itinerary accounts for — a
	 * receipt at the first leg's port, a load or unload at a leg's port on
	 * that leg's voyage, a claim at the last leg's port, customs anywhere;
	 * and everything, for a cargo with no itinerary.
	 */
	Literal expected(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber) {
		return Literal.relation(Rules.class, "expected")
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

	/**
	 * RouteSpecification.isSatisfiedBy: the itinerary starts at the specified
	 * origin, ends at the specified destination, and lands before the deadline.
	 */
	Literal satisfied(Unifiable<String> trackingId) {
		Unifiable<String> origin = lvar();
		Unifiable<String> destination = lvar();
		Unifiable<Instant> deadline = lvar();
		Unifiable<Instant> finalArrival = lvar();
		return Literal.relation(Rules.class, "satisfied")
				.arg("trackingId", trackingId)
				.solving(routeSpecification(db, trackingId, origin, destination, deadline)
						.and(firstLeg(trackingId, lvar(), origin, lvar(), lvar(), lvar()))
						.and(lastLeg(trackingId, lvar(), lvar(), destination, lvar(), finalArrival))
						.and(Instants.lss(finalArrival, deadline)));
	}

	/** Not routed without an itinerary; routed when it satisfies the specification; misrouted otherwise. */
	Literal routingStatus(Unifiable<String> trackingId, Unifiable<RoutingStatus> status) {
		return Literal.relation(Rules.class, "routingStatus")
				.arg("trackingId", trackingId)
				.arg("status", status)
				.solving(exclude(anyLeg(trackingId)).and(status.unifies(RoutingStatus.NOT_ROUTED))
						.or(satisfied(trackingId).and(status.unifies(RoutingStatus.ROUTED)))
						.or(anyLeg(trackingId).and(exclude(satisfied(trackingId)))
								.and(status.unifies(RoutingStatus.MISROUTED))));
	}

	/** The most recently completed handling: an event with none later. */
	Literal lastEvent(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(Rules.class, "lastEvent")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.solving(handlingEvent(db, trackingId, type, location, voyageNumber, completed, lvar())
						.and(exclude(later(trackingId, completed))));
	}
}
