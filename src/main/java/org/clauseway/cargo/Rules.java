package org.clauseway.cargo;

// ABOUTME: The derived relations over the shipping facts — dddsample's
// ABOUTME: Delivery, Itinerary and RouteSpecification calculations as rules.

import static org.clauseway.cargo.Schema.handlingEvent;
import static org.clauseway.cargo.Schema.leg;
import static org.clauseway.cargo.Schema.routeSpecification;
import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
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

	/** The cargo has been handled at all. */
	Literal anyEvent(Unifiable<String> trackingId) {
		return Literal.relation(Rules.class, "anyEvent")
				.arg("trackingId", trackingId)
				.solving(handlingEvent(db, trackingId, lvar(), lvar(), lvar(), lvar(), lvar()));
	}

	/** The transport status each handling kind leaves the cargo in: a lookup, as a relation over its own facts. */
	private static Literal transportOf(AnswerSource table, Unifiable<HandlingType> type,
			Unifiable<TransportStatus> status) {
		return Literal.relation(Rules.class, "transportOf")
				.arg("type", type)
				.arg("status", status)
				.from(table);
	}

	private static final AnswerStore TRANSPORT = AnswerStore.empty().asserting(
			transportOf(null, lval(HandlingType.LOAD), lval(TransportStatus.ONBOARD_CARRIER)),
			transportOf(null, lval(HandlingType.UNLOAD), lval(TransportStatus.IN_PORT)),
			transportOf(null, lval(HandlingType.RECEIVE), lval(TransportStatus.IN_PORT)),
			transportOf(null, lval(HandlingType.CUSTOMS), lval(TransportStatus.IN_PORT)),
			transportOf(null, lval(HandlingType.CLAIM), lval(TransportStatus.CLAIMED)));

	/** Not received until handled; afterwards, wherever the last handling left it. */
	Literal transportStatus(Unifiable<String> trackingId, Unifiable<TransportStatus> status) {
		return Literal.relation(Rules.class, "transportStatus")
				.arg("trackingId", trackingId)
				.arg("status", status)
				.solving(exclude(anyEvent(trackingId)).and(status.unifies(TransportStatus.NOT_RECEIVED))
						.or(defer(() -> {
							Unifiable<HandlingType> type = lvar();
							return lastEvent(trackingId, type, lvar(), lvar(), lvar())
									.and(transportOf(TRANSPORT, type, status));
						})));
	}

	/** Where the cargo was last handled. */
	Literal lastKnownLocation(Unifiable<String> trackingId, Unifiable<String> location) {
		return Literal.relation(Rules.class, "lastKnownLocation")
				.arg("trackingId", trackingId)
				.arg("location", location)
				.solving(lastEvent(trackingId, lvar(), location, lvar(), lvar()));
	}

	/** The voyage the cargo is aboard: the one it was last loaded onto, while loaded. */
	Literal currentVoyage(Unifiable<String> trackingId, Unifiable<String> voyageNumber) {
		return Literal.relation(Rules.class, "currentVoyage")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.solving(lastEvent(trackingId, lval(HandlingType.LOAD), lvar(), voyageNumber, lvar()));
	}

	/** The last handling is one the itinerary does not account for. */
	Literal misdirected(Unifiable<String> trackingId) {
		return Literal.relation(Rules.class, "misdirected")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<HandlingType> type = lvar();
					Unifiable<String> location = lvar();
					Unifiable<String> voyageNumber = lvar();
					return lastEvent(trackingId, type, location, voyageNumber, lvar())
							.and(exclude(expected(trackingId, type, location, voyageNumber)));
				}));
	}

	/** Routed as specified and not misdirected. */
	Literal onTrack(Unifiable<String> trackingId) {
		return Literal.relation(Rules.class, "onTrack")
				.arg("trackingId", trackingId)
				.solving(routingStatus(trackingId, lval(RoutingStatus.ROUTED))
						.and(exclude(misdirected(trackingId))));
	}

	/** The estimated arrival: the last leg's unload time, while on track. */
	Literal eta(Unifiable<String> trackingId, Unifiable<Instant> time) {
		return Literal.relation(Rules.class, "eta")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(onTrack(trackingId)
						.and(lastLeg(trackingId, lvar(), lvar(), lvar(), lvar(), time)));
	}

	/** The last handling was an unload at the specified destination. */
	Literal unloadedAtDestination(Unifiable<String> trackingId) {
		return Literal.relation(Rules.class, "unloadedAtDestination")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<String> location = lvar();
					return lastEvent(trackingId, lval(HandlingType.UNLOAD), location, lvar(), lvar())
							.and(routeSpecification(db, trackingId, lvar(), location, lvar()));
				}));
	}

	/**
	 * What should happen to the cargo next, while on track: receipt at the
	 * origin before any handling; after receipt, loading onto the first leg;
	 * after loading, unloading where that leg lands; after unloading, loading
	 * onto the following leg — or the claim, when there is none.
	 */
	Literal nextExpectedActivity(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber) {
		return Literal.relation(Rules.class, "nextExpectedActivity")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(onTrack(trackingId)
						.and(exclude(anyEvent(trackingId))
								.and(type.unifies(HandlingType.RECEIVE))
								.and(routeSpecification(db, trackingId, location, lvar(), lvar()))
								.and(voyageNumber.unifies(Shipping.NO_VOYAGE))
								.or(lastEvent(trackingId, lval(HandlingType.RECEIVE), lvar(), lvar(), lvar())
										.and(type.unifies(HandlingType.LOAD))
										.and(firstLeg(trackingId, voyageNumber, location, lvar(), lvar(), lvar())))
								.or(defer(() -> {
									Unifiable<String> loadedAt = lvar();
									return lastEvent(trackingId, lval(HandlingType.LOAD), loadedAt, lvar(), lvar())
											.and(type.unifies(HandlingType.UNLOAD))
											.and(leg(db, trackingId, lvar(), voyageNumber, loadedAt, location, lvar(), lvar()));
								}))
								.or(defer(() -> {
									Unifiable<String> unloadedAt = lvar();
									Unifiable<Integer> index = lvar();
									Unifiable<Integer> next = lvar();
									return lastEvent(trackingId, lval(HandlingType.UNLOAD), unloadedAt, lvar(), lvar())
											.and(leg(db, trackingId, index, lvar(), lvar(), unloadedAt, lvar(), lvar()))
											.and(Ints.addo(index, lval(1), next))
											.and(leg(db, trackingId, next, voyageNumber, location, lvar(), lvar(), lvar())
													.and(type.unifies(HandlingType.LOAD))
													.or(exclude(leg(db, trackingId, next, projected(), projected(), projected(),
															projected(), projected()))
															.and(type.unifies(HandlingType.CLAIM))
															.and(location.unifies(unloadedAt))
															.and(voyageNumber.unifies(Shipping.NO_VOYAGE))));
								}))));
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
