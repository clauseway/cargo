package org.clauseway.cargo.rules;

// ABOUTME: The delivery's progress read as rules — every field of dddsample's
// ABOUTME: Delivery snapshot derived from the history, itinerary and routing.

import static org.clauseway.cargo.Schema.NO_VOYAGE;
import static org.clauseway.cargo.Schema.leg;
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
	 * What should happen to the cargo next, while on track: receipt at the
	 * origin before any handling; after receipt, loading onto the first leg;
	 * after loading, unloading where that leg lands; after unloading, loading
	 * onto the following leg — or the claim, when there is none.
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
								.and(type.unifies(HandlingType.RECEIVE))
								.and(routeSpecification(db, trackingId, location, lvar(), lvar()))
								.and(voyageNumber.unifies(NO_VOYAGE))
								.or(history.lastEvent(trackingId, lval(HandlingType.RECEIVE), lvar(), lvar(), lvar())
										.and(type.unifies(HandlingType.LOAD))
										.and(itinerary.firstLeg(trackingId, voyageNumber, location, lvar(), lvar(), lvar())))
								.or(defer(() -> {
									Unifiable<String> loadedAt = lvar();
									return history.lastEvent(trackingId, lval(HandlingType.LOAD), loadedAt, lvar(), lvar())
											.and(type.unifies(HandlingType.UNLOAD))
											.and(leg(db, trackingId, lvar(), voyageNumber, loadedAt, location, lvar(), lvar()));
								}))
								.or(defer(() -> {
									Unifiable<String> unloadedAt = lvar();
									Unifiable<Integer> index = lvar();
									Unifiable<Integer> next = lvar();
									return history.lastEvent(trackingId, lval(HandlingType.UNLOAD), unloadedAt, lvar(), lvar())
											.and(leg(db, trackingId, index, lvar(), lvar(), unloadedAt, lvar(), lvar()))
											.and(Ints.addo(index, lval(1), next))
											.and(leg(db, trackingId, next, voyageNumber, location, lvar(), lvar(), lvar())
													.and(type.unifies(HandlingType.LOAD))
													.or(exclude(leg(db, trackingId, next, projected(), projected(), projected(),
															projected(), projected()))
															.and(type.unifies(HandlingType.CLAIM))
															.and(location.unifies(unloadedAt))
															.and(voyageNumber.unifies(NO_VOYAGE))));
								}))));
	}
}
