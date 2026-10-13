package org.clauseway.cargo.rules;

// ABOUTME: The delivery's progress read as rules — every field of dddsample's
// ABOUTME: Delivery snapshot derived from the history, itinerary and routing.

import static org.clauseway.cargo.Schema.routeSpecification;
import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import org.clauseway.cargo.HandlingType;
import org.clauseway.cargo.RoutingStatus;
import org.clauseway.cargo.TransportStatus;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
import org.clauseway.pldb.relations.Literal;

public final class Progress {

	private final AnswerSource db;
	private final History history;
	private final Itinerary itinerary;
	private final Routing routing;

	public Progress(AnswerSource db, History history, Itinerary itinerary, Routing routing) {
		this.db = db;
		this.history = history;
		this.itinerary = itinerary;
		this.routing = routing;
	}

	/** The transport status each handling kind leaves the cargo in: a lookup, as a relation over its own facts. */
	private static Literal transportOf(AnswerSource table, Unifiable<HandlingType> type,
			Unifiable<TransportStatus> status) {
		return Literal.relation(Progress.class, "transportOf")
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
	public Literal transportStatus(Unifiable<String> trackingId, Unifiable<TransportStatus> status) {
		return Literal.relation(Progress.class, "transportStatus")
				.arg("trackingId", trackingId)
				.arg("status", status)
				.solving(exclude(history.anyEvent(trackingId)).and(status.unifies(TransportStatus.NOT_RECEIVED))
						.or(defer(() -> {
							Unifiable<HandlingType> type = lvar();
							return history.lastEvent(trackingId, type, lvar(), lvar(), lvar())
									.and(transportOf(TRANSPORT, type, status));
						})));
	}

	/** Where the cargo was last handled. */
	public Literal lastKnownLocation(Unifiable<String> trackingId, Unifiable<String> location) {
		return Literal.relation(Progress.class, "lastKnownLocation")
				.arg("trackingId", trackingId)
				.arg("location", location)
				.solving(history.lastEvent(trackingId, lvar(), location, lvar(), lvar()));
	}

	/** The voyage the cargo is aboard: the one it was last loaded onto, while loaded. */
	public Literal currentVoyage(Unifiable<String> trackingId, Unifiable<String> voyageNumber) {
		return Literal.relation(Progress.class, "currentVoyage")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.solving(history.lastEvent(trackingId, lval(HandlingType.LOAD), lvar(), voyageNumber, lvar()));
	}

	/** The last handling is one the itinerary does not account for. */
	public Literal misdirected(Unifiable<String> trackingId) {
		return Literal.relation(Progress.class, "misdirected")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<HandlingType> type = lvar();
					Unifiable<String> location = lvar();
					Unifiable<String> voyageNumber = lvar();
					return history.lastEvent(trackingId, type, location, voyageNumber, lvar())
							.and(exclude(itinerary.expected(trackingId, type, location, voyageNumber)));
				}));
	}

	/** Routed as specified and not misdirected. */
	public Literal onTrack(Unifiable<String> trackingId) {
		return Literal.relation(Progress.class, "onTrack")
				.arg("trackingId", trackingId)
				.solving(routing.routingStatus(trackingId, lval(RoutingStatus.ROUTED))
						.and(exclude(misdirected(trackingId))));
	}

	/** The estimated arrival: the last leg's unload time, while on track. */
	public Literal eta(Unifiable<String> trackingId, Unifiable<Instant> time) {
		return Literal.relation(Progress.class, "eta")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(onTrack(trackingId)
						.and(itinerary.lastLeg(trackingId, lvar(), lvar(), lvar(), lvar(), time)));
	}

	/** The last handling was an unload at the specified destination. */
	public Literal unloadedAtDestination(Unifiable<String> trackingId) {
		return Literal.relation(Progress.class, "unloadedAtDestination")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<String> location = lvar();
					return history.lastEvent(trackingId, lval(HandlingType.UNLOAD), location, lvar(), lvar())
							.and(routeSpecification(db, trackingId, lvar(), location, lvar()));
				}));
	}

	/**
	 * The delivery as one row per cargo — dddsample's Delivery snapshot. The
	 * partial fields (a last location, a voyage, an ETA, a next activity) are
	 * null where their rule has no answer, and the two booleans are true
	 * where theirs holds; so the row exists for every cargo, and it filters
	 * nothing — ask the rules beneath it for that.
	 */
	public Literal delivery(Unifiable<String> trackingId, Unifiable<RoutingStatus> routingStatus,
			Unifiable<TransportStatus> transport, Unifiable<String> lastKnownLocation,
			Unifiable<String> currentVoyage, Unifiable<Boolean> misdirected, Unifiable<Instant> eta,
			Unifiable<HandlingType> nextType, Unifiable<String> nextLocation, Unifiable<String> nextVoyage,
			Unifiable<Boolean> unloadedAtDestination) {
		return Literal.relation(Progress.class, "delivery")
				.arg("trackingId", trackingId)
				.arg("routingStatus", routingStatus)
				.arg("transport", transport)
				.arg("lastKnownLocation", lastKnownLocation).nullable()
				.arg("currentVoyage", currentVoyage).nullable()
				.arg("misdirected", misdirected)
				.arg("eta", eta).nullable()
				.arg("nextType", nextType).nullable()
				.arg("nextLocation", nextLocation).nullable()
				.arg("nextVoyage", nextVoyage).nullable()
				.arg("unloadedAtDestination", unloadedAtDestination)
				.solving(routing.routingStatus(trackingId, routingStatus)
						.and(transportStatus(trackingId, transport))
						.and(lastKnownLocation(trackingId, lastKnownLocation)
								.or(exclude(lastKnownLocation(trackingId, projected()))
										.and(lastKnownLocation.unifies(lval((String) null)))))
						.and(currentVoyage(trackingId, currentVoyage)
								.or(exclude(currentVoyage(trackingId, projected()))
										.and(currentVoyage.unifies(lval((String) null)))))
						.and(misdirected(trackingId).and(misdirected.unifies(true))
								.or(exclude(misdirected(trackingId)).and(misdirected.unifies(false))))
						.and(eta(trackingId, eta)
								.or(exclude(eta(trackingId, projected())).and(eta.unifies(lval((Instant) null)))))
						.and(nextExpectedActivity(trackingId, nextType, nextLocation, nextVoyage)
								.or(exclude(nextExpectedActivity(trackingId, projected(), projected(), projected()))
										.and(nextType.unifies(lval((HandlingType) null)))
										.and(nextLocation.unifies(lval((String) null)))
										.and(nextVoyage.unifies(lval((String) null)))))
						.and(unloadedAtDestination(trackingId).and(unloadedAtDestination.unifies(true))
								.or(exclude(unloadedAtDestination(trackingId)).and(unloadedAtDestination.unifies(false)))));
	}

	/**
	 * What should happen to the cargo next, while on track: the plan's first
	 * step before any handling, otherwise the step after the one the last
	 * handling was.
	 */
	public Literal nextExpectedActivity(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber) {
		return Literal.relation(Progress.class, "nextExpectedActivity")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(onTrack(trackingId)
						.and(exclude(history.anyEvent(trackingId))
								.and(itinerary.plannedHandling(trackingId, lval(0), type, location, voyageNumber))
								.or(defer(() -> {
									Unifiable<HandlingType> lastType = lvar();
									Unifiable<String> lastLocation = lvar();
									Unifiable<String> lastVoyage = lvar();
									Unifiable<Integer> step = lvar();
									Unifiable<Integer> next = lvar();
									return history.lastEvent(trackingId, lastType, lastLocation, lastVoyage, lvar())
											.and(itinerary.plannedHandling(trackingId, step, lastType, lastLocation, lastVoyage))
											.and(Ints.addo(step, lval(1), next))
											.and(itinerary.plannedHandling(trackingId, next, type, location, voyageNumber));
								}))));
	}
}
