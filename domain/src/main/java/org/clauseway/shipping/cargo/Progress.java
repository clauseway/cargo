package org.clauseway.shipping.cargo;

// ABOUTME: dddsample's Delivery: the cargo's record of its progress and of the handling
// ABOUTME: it was last shown — every field derived as a rule, stored whole by the cargo's commands.

import static org.clauseway.shipping.Reads.first;
import static org.clauseway.shipping.Reads.holds;
import static org.clauseway.shipping.cargo.Cargo.routeSpecification;
import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Optional;
import org.clauseway.shipping.Delivery;
import org.clauseway.shipping.HandlingActivity;
import org.clauseway.shipping.HandlingEvent;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Notice;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.Schema;
import org.clauseway.shipping.TransportStatus;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.sql.SerializableSource;

public final class Progress {

	private final AnswerSource db;
	private final Itinerary itinerary;
	private final Routing routing;

	Progress(AnswerSource db, Itinerary itinerary, Routing routing) {
		this.db = db;
		this.itinerary = itinerary;
		this.routing = routing;
	}

	// -- the stored state: what the cargo keeps of its progress --------------

	/** The handling the cargo was last shown — Delivery.lastEvent; replaced when an inspection brings a later one. */
	static Literal lastHandling(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(Progress.class, "lastHandling")
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
		return Literal.relation(Progress.class, "delivery")
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

	/** The stored state on a SQL source: the kinds and statuses by name, the absent fields as NULL, the times as timestamps. */
	static SerializableSource onSql(SerializableSource source) {
		return source
				.withCodec(lastHandling(null, lvar(), Schema.HANDLING_TYPE.arg(), lvar(), Schema.OPTIONAL_STRING.arg(),
						Schema.INSTANT.arg()))
				.withCodec(delivery(null, lvar(), Schema.ROUTING_STATUS.arg(), Schema.TRANSPORT_STATUS.arg(),
						Schema.OPTIONAL_STRING.arg(), Schema.OPTIONAL_STRING.arg(), lvar(), Schema.OPTIONAL_INSTANT.arg(),
						Schema.OPTIONAL_HANDLING_TYPE.arg(), Schema.OPTIONAL_STRING.arg(), Schema.OPTIONAL_STRING.arg(),
						lvar()));
	}

	/** The handling the cargo was last shown, if any. */
	Optional<HandlingEvent> lastHandlingOf(String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<LOption<String>> voyageNumber = lvar();
		Unifiable<Instant> completed = lvar();
		return Query.of(lastHandling(db, lval(trackingId), type, location, voyageNumber, completed))
				.select(type, location, voyageNumber, completed).rows()
				.map(row -> new HandlingEvent(row.get(type).get(), row.get(location).get(),
						row.get(voyageNumber).get().toOptional(), row.get(completed).get()))
				.findFirst();
	}

	/** A handling as the cargo keeps it, as a fact. */
	static Literal shown(AnswerSource db, String trackingId, HandlingEvent event) {
		return lastHandling(db, lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber()), lval(event.getCompletionTime()));
	}

	/** The delivery stored for the cargo, if it is booked. */
	public Optional<Delivery> stored(String trackingId) {
		Unifiable<RoutingStatus> routingStatus = lvar();
		Unifiable<TransportStatus> transportStatus = lvar();
		Unifiable<LOption<String>> lastKnownLocation = lvar();
		Unifiable<LOption<String>> currentVoyage = lvar();
		Unifiable<Boolean> misdirected = lvar();
		Unifiable<LOption<Instant>> eta = lvar();
		Unifiable<LOption<HandlingType>> nextType = lvar();
		Unifiable<LOption<String>> nextLocation = lvar();
		Unifiable<LOption<String>> nextVoyage = lvar();
		Unifiable<Boolean> unloadedAtDestination = lvar();
		return Query.of(delivery(db, lval(trackingId), routingStatus, transportStatus, lastKnownLocation, currentVoyage,
						misdirected, eta, nextType, nextLocation, nextVoyage, unloadedAtDestination))
				.select(routingStatus, transportStatus, lastKnownLocation, currentVoyage, misdirected, eta,
						nextType, nextLocation, nextVoyage, unloadedAtDestination).rows()
				.map(row -> new Delivery(
						row.get(routingStatus).get(),
						row.get(transportStatus).get(),
						row.get(lastKnownLocation).get().toOptional(),
						row.get(currentVoyage).get().toOptional(),
						row.get(misdirected).get(),
						row.get(eta).get().toOptional(),
						row.get(nextType).get().toOptional().map(type -> new HandlingActivity(type,
								row.get(nextLocation).get().toOptional().get(),
								row.get(nextVoyage).get().toOptional())),
						row.get(unloadedAtDestination).get()))
				.findFirst();
	}

	/** The delivery as the row the cargo stores. */
	static Literal row(AnswerSource db, String trackingId, Delivery delivery) {
		Optional<HandlingActivity> next = delivery.getNextExpectedActivity();
		return Progress.delivery(db, lval(trackingId), lval(delivery.getRoutingStatus()), lval(delivery.getTransportStatus()),
				LOption.of(delivery.getLastKnownLocation()), LOption.of(delivery.getCurrentVoyage()),
				lval(delivery.isMisdirected()), LOption.of(delivery.getEta()),
				LOption.of(next.map(HandlingActivity::getType)), LOption.of(next.map(HandlingActivity::getLocation)),
				LOption.of(next.flatMap(HandlingActivity::getVoyageNumber)), lval(delivery.isUnloadedAtDestination()));
	}

	/** Delivery.derivedFrom: every field as the rules give it now, from the specification, the itinerary and the last handling shown. */
	Delivery derived(String trackingId) {
		Unifiable<String> id = lval(trackingId);
		Unifiable<RoutingStatus> routingStatus = lvar();
		Unifiable<TransportStatus> transport = lvar();
		Unifiable<String> location = lvar();
		Unifiable<String> voyageNumber = lvar();
		Unifiable<Instant> time = lvar();
		ActivityColumns next = new ActivityColumns();
		return new Delivery(
				first(routing.routingStatus(id, routingStatus), routingStatus)
						.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId)),
				first(transportStatus(id, transport), transport)
						.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId)),
				first(lastKnownLocation(id, location), location),
				first(currentVoyage(id, voyageNumber), voyageNumber),
				holds(misdirected(id)),
				first(eta(id, time), time),
				next.activities(next.select(nextExpectedActivity(id, next.type, next.location, next.voyageNumber))).findFirst(),
				holds(unloadedAtDestination(id)));
	}

	// -- the derivation: each field a rule over the cargo's own relations ----

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

	/** The cargo has been shown a handling at all. */
	private Literal handled(Unifiable<String> trackingId) {
		return Literal.relation(Progress.class, "handled")
				.arg("trackingId", trackingId)
				.solving(lastHandling(db, trackingId, lvar(), lvar(), lvar(), lvar()));
	}

	/** Not received until handled; afterwards, wherever the last handling left it. */
	public Literal transportStatus(Unifiable<String> trackingId, Unifiable<TransportStatus> status) {
		return Literal.relation(Progress.class, "transportStatus")
				.arg("trackingId", trackingId)
				.arg("status", status)
				.solving(exclude(handled(trackingId)).and(status.unifies(TransportStatus.NOT_RECEIVED))
						.or(defer(() -> {
							Unifiable<HandlingType> type = lvar();
							return lastHandling(db, trackingId, type, lvar(), lvar(), lvar())
									.and(transportOf(TRANSPORT, type, status));
						})));
	}

	/** Where the cargo was last handled. */
	public Literal lastKnownLocation(Unifiable<String> trackingId, Unifiable<String> location) {
		return Literal.relation(Progress.class, "lastKnownLocation")
				.arg("trackingId", trackingId)
				.arg("location", location)
				.solving(lastHandling(db, trackingId, lvar(), location, lvar(), lvar()));
	}

	/** The voyage the cargo is aboard: the one it was last loaded onto, while loaded. */
	public Literal currentVoyage(Unifiable<String> trackingId, Unifiable<String> voyageNumber) {
		return Literal.relation(Progress.class, "currentVoyage")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.solving(lastHandling(db, trackingId, lval(HandlingType.LOAD), lvar(), some(voyageNumber), lvar()));
	}

	/** The last handling is one the itinerary does not account for. */
	public Literal misdirected(Unifiable<String> trackingId) {
		return Literal.relation(Progress.class, "misdirected")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<HandlingType> type = lvar();
					Unifiable<String> location = lvar();
					Unifiable<LOption<String>> voyageNumber = lvar();
					return lastHandling(db, trackingId, type, location, voyageNumber, lvar())
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
					return lastHandling(db, trackingId, lval(HandlingType.UNLOAD), location, lvar(), lvar())
							.and(routeSpecification(db, trackingId, lvar(), location, lvar()));
				}));
	}

	/** CargoInspectionService: the notices the stored delivery warrants — misdirected, arrived at its destination. */
	public Literal notice(Unifiable<String> trackingId, Unifiable<Notice> notice) {
		return Literal.relation(Progress.class, "notice")
				.arg("trackingId", trackingId)
				.arg("notice", notice)
				.solving(delivery(db, trackingId, lvar(), lvar(), lvar(), lvar(), lval(true), lvar(), lvar(), lvar(), lvar(), lvar())
						.and(notice.unifies(Notice.MISDIRECTED))
						.or(delivery(db, trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lval(true))
								.and(notice.unifies(Notice.ARRIVED))));
	}

	/**
	 * What should happen to the cargo next, while on track: the plan's first
	 * step before any handling, otherwise the step after the one the last
	 * handling was.
	 */
	public Literal nextExpectedActivity(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber) {
		return Literal.relation(Progress.class, "nextExpectedActivity")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(onTrack(trackingId)
						.and(exclude(handled(trackingId))
								.and(itinerary.plannedHandling(trackingId, lval(0), type, location, voyageNumber))
								.or(defer(() -> {
									Unifiable<HandlingType> lastType = lvar();
									Unifiable<String> lastLocation = lvar();
									Unifiable<LOption<String>> lastVoyage = lvar();
									Unifiable<Integer> step = lvar();
									Unifiable<Integer> next = lvar();
									return lastHandling(db, trackingId, lastType, lastLocation, lastVoyage, lvar())
											.and(itinerary.plannedHandling(trackingId, step, lastType, lastLocation, lastVoyage))
											.and(Ints.addo(step, lval(1), next))
											.and(itinerary.plannedHandling(trackingId, next, type, location, voyageNumber));
								}))));
	}
}
