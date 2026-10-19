package org.clauseway.shipping.cargo;

// ABOUTME: dddsample's Delivery as the cargo keeps it: the handling it was last shown and the delivery
// ABOUTME: row, stored by its commands; the rules that calculate the row from the itinerary and the routing.

import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;
import static org.clauseway.shipping.cargo.Cargo.routeSpecification;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.clauseway.functional.fibers.schedulers.BreadthFirstScheduler;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.solving.Answer;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.relations.Question;
import org.clauseway.pldb.relations.Relation;
import org.clauseway.shipping.DeliveryDTO;
import org.clauseway.shipping.HandlingActivity;
import org.clauseway.shipping.HandlingEvent;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Notice;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.TransportStatus;

public final class Delivery {

	private final AnswerSource db;
	private final Itinerary itinerary;
	private final Routing routing;

	Delivery(AnswerSource db, Itinerary itinerary, Routing routing) {
		this.db = db;
		this.itinerary = itinerary;
		this.routing = routing;
	}

	// -- the stored state: what the cargo keeps of its delivery --------------

	/** The handling the cargo was last shown — Delivery.lastEvent; replaced when an inspection brings a later one. */
	static Literal lastHandling(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(Delivery.class, "lastHandling")
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
		return Literal.relation(Delivery.class, "delivery")
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

	/** The cargo's delivery as its commands last stored it. */
	public DeliveryDTO deliveryOf(String trackingId) {
		DeliveryColumns c = new DeliveryColumns();
		return Query.of(delivery(db, lval(trackingId), c))
				.select(c.routingStatus, c.transportStatus, c.lastKnownLocation, c.currentVoyage, c.misdirected, c.eta,
						c.nextType, c.nextLocation, c.nextVoyage, c.unloadedAtDestination).rows()
				.map(c::delivery)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
	}

	// -- the rows a command retracts and lands --------------------------------

	/** The delivery row the cargo holds, as a retraction takes it. */
	List<Answer<Relation>> storedRows(String trackingId) {
		DeliveryColumns c = new DeliveryColumns();
		return rows(delivery(db, lval(trackingId), c), delivery(null, lval(trackingId), c));
	}

	/** Delivery.derivedFrom: the delivery the rules calculate now, as the row to land. */
	List<Answer<Relation>> calculatedRows(String trackingId) {
		DeliveryColumns c = new DeliveryColumns();
		return rows(calculated(lval(trackingId), c), delivery(null, lval(trackingId), c));
	}

	/** The handling the cargo holds, as a retraction takes it. */
	List<Answer<Relation>> lastHandlingRows(String trackingId) {
		Unifiable<HandlingType> type = lvar();
		Unifiable<String> location = lvar();
		Unifiable<LOption<String>> voyageNumber = lvar();
		Unifiable<Instant> completed = lvar();
		return rows(lastHandling(db, lval(trackingId), type, location, voyageNumber, completed),
				lastHandling(null, lval(trackingId), type, location, voyageNumber, completed));
	}

	/** A handling as the cargo keeps it, as a fact. */
	static Literal shown(AnswerSource db, String trackingId, HandlingEvent event) {
		return lastHandling(db, lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber()), lval(event.getCompletionTime()));
	}

	/** The question's answers in the template's shape, as rows a write door takes. */
	private static List<Answer<Relation>> rows(Literal question, Literal template) {
		return new BreadthFirstScheduler<>(Question.select(question, template)).get();
	}

	/** The variables of one delivery row's columns, shared between a question and its template, and the row-to-value reading over them. */
	private static final class DeliveryColumns {
		final Unifiable<RoutingStatus> routingStatus = lvar();
		final Unifiable<TransportStatus> transportStatus = lvar();
		final Unifiable<LOption<String>> lastKnownLocation = lvar();
		final Unifiable<LOption<String>> currentVoyage = lvar();
		final Unifiable<Boolean> misdirected = lvar();
		final Unifiable<LOption<Instant>> eta = lvar();
		final Unifiable<LOption<HandlingType>> nextType = lvar();
		final Unifiable<LOption<String>> nextLocation = lvar();
		final Unifiable<LOption<String>> nextVoyage = lvar();
		final Unifiable<Boolean> unloadedAtDestination = lvar();

		DeliveryDTO delivery(Row row) {
			return new DeliveryDTO(
					row.get(routingStatus).get(),
					row.get(transportStatus).get(),
					row.get(lastKnownLocation).get().toOptional(),
					row.get(currentVoyage).get().toOptional(),
					row.get(misdirected).get(),
					row.get(eta).get().toOptional(),
					row.get(nextType).get().toOptional().map(type -> new HandlingActivity(type,
							row.get(nextLocation).get().toOptional().get(),
							row.get(nextVoyage).get().toOptional())),
					row.get(unloadedAtDestination).get());
		}
	}

	private Literal calculated(Unifiable<String> trackingId, DeliveryColumns c) {
		return calculated(trackingId, c.routingStatus, c.transportStatus, c.lastKnownLocation, c.currentVoyage,
				c.misdirected, c.eta, c.nextType, c.nextLocation, c.nextVoyage, c.unloadedAtDestination);
	}

	private static Literal delivery(AnswerSource db, Unifiable<String> trackingId, DeliveryColumns c) {
		return delivery(db, trackingId, c.routingStatus, c.transportStatus, c.lastKnownLocation, c.currentVoyage,
				c.misdirected, c.eta, c.nextType, c.nextLocation, c.nextVoyage, c.unloadedAtDestination);
	}

	// -- the rules: where the cargo is, whether it strayed, what comes next ---

	/** The transport status each handling kind leaves the cargo in: a lookup, as a relation over its own facts. */
	private static Literal transportOf(AnswerSource table, Unifiable<HandlingType> type,
			Unifiable<TransportStatus> status) {
		return Literal.relation(Delivery.class, "transportOf")
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
		return Literal.relation(Delivery.class, "handled")
				.arg("trackingId", trackingId)
				.solving(lastHandling(db, trackingId, lvar(), lvar(), lvar(), lvar()));
	}

	/** Not received until handled; afterwards, wherever the last handling left it. */
	public Literal transportStatus(Unifiable<String> trackingId, Unifiable<TransportStatus> status) {
		return Literal.relation(Delivery.class, "transportStatus")
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
		return Literal.relation(Delivery.class, "lastKnownLocation")
				.arg("trackingId", trackingId)
				.arg("location", location)
				.solving(lastHandling(db, trackingId, lvar(), location, lvar(), lvar()));
	}

	/** The voyage the cargo is aboard: the one it was last loaded onto, while loaded. */
	public Literal currentVoyage(Unifiable<String> trackingId, Unifiable<String> voyageNumber) {
		return Literal.relation(Delivery.class, "currentVoyage")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.solving(lastHandling(db, trackingId, lval(HandlingType.LOAD), lvar(), some(voyageNumber), lvar()));
	}

	/** The last handling is one the itinerary does not account for. */
	public Literal misdirected(Unifiable<String> trackingId) {
		return Literal.relation(Delivery.class, "misdirected")
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
		return Literal.relation(Delivery.class, "onTrack")
				.arg("trackingId", trackingId)
				.solving(routing.routingStatus(trackingId, lval(RoutingStatus.ROUTED))
						.and(exclude(misdirected(trackingId))));
	}

	/** The estimated arrival: the last leg's unload time, while on track. */
	public Literal eta(Unifiable<String> trackingId, Unifiable<Instant> time) {
		return Literal.relation(Delivery.class, "eta")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(onTrack(trackingId)
						.and(itinerary.lastLeg(trackingId, lvar(), lvar(), lvar(), lvar(), time)));
	}

	/** The last handling was an unload at the specified destination. */
	public Literal unloadedAtDestination(Unifiable<String> trackingId) {
		return Literal.relation(Delivery.class, "unloadedAtDestination")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<String> location = lvar();
					return lastHandling(db, trackingId, lval(HandlingType.UNLOAD), location, lvar(), lvar())
							.and(routeSpecification(db, trackingId, lvar(), location, lvar()));
				}));
	}

	/**
	 * What should happen to the cargo next, while on track: the plan's first
	 * step before any handling, otherwise the step after the one the last
	 * handling was.
	 */
	public Literal nextExpectedActivity(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber) {
		return Literal.relation(Delivery.class, "nextExpectedActivity")
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

	// -- the row rule: the delivery the rules calculate; the notices it warrants --

	/**
	 * Delivery.derivedFrom as one rule: the delivery row the rules give now.
	 * The partial fields are reified to their columns — the value where the
	 * rule holds, none where it does not — and the predicates to booleans;
	 * the rules themselves stay partial for each other.
	 */
	public Literal calculated(Unifiable<String> trackingId, Unifiable<RoutingStatus> routingStatus,
			Unifiable<TransportStatus> transportStatus, Unifiable<LOption<String>> lastKnownLocation,
			Unifiable<LOption<String>> currentVoyage, Unifiable<Boolean> misdirected, Unifiable<LOption<Instant>> eta,
			Unifiable<LOption<HandlingType>> nextType, Unifiable<LOption<String>> nextLocation,
			Unifiable<LOption<String>> nextVoyage, Unifiable<Boolean> unloadedAtDestination) {
		return Literal.relation(Delivery.class, "calculated")
				.arg("trackingId", trackingId)
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
				.solving(routing.routingStatus(trackingId, routingStatus)
						.and(transportStatus(trackingId, transportStatus))
						.and(optional(location -> lastKnownLocation(trackingId, location), lastKnownLocation))
						.and(optional(voyage -> currentVoyage(trackingId, voyage), currentVoyage))
						.and(flag(misdirected(trackingId), misdirected))
						.and(optional(time -> eta(trackingId, time), eta))
						.and(defer(() -> {
							Unifiable<HandlingType> type = lvar();
							Unifiable<String> location = lvar();
							return nextExpectedActivity(trackingId, type, location, nextVoyage)
									.and(nextType.unifies(some(type)))
									.and(nextLocation.unifies(some(location)));
						}).or(exclude(nextExpectedActivity(trackingId, projected(), projected(), projected()))
								.and(nextType.unifies(none()))
								.and(nextLocation.unifies(none()))
								.and(nextVoyage.unifies(none()))))
						.and(flag(unloadedAtDestination(trackingId), unloadedAtDestination)));
	}

	/** A partial rule reified to a column: its value where it holds, none where it does not. */
	private static <T> Goal optional(Function<Unifiable<T>, Literal> rule, Unifiable<LOption<T>> column) {
		return defer(() -> {
			Unifiable<T> value = lvar();
			return rule.apply(value).and(column.unifies(some(value)));
		}).or(exclude(rule.apply(projected())).and(column.unifies(none())));
	}

	/** A predicate reified to a boolean column. */
	private static Goal flag(Literal rule, Unifiable<Boolean> column) {
		return rule.and(column.unifies(true)).or(exclude(rule).and(column.unifies(false)));
	}

	/** CargoInspectionService: the notices the stored delivery warrants — misdirected, arrived at its destination. */
	public Literal notice(Unifiable<String> trackingId, Unifiable<Notice> notice) {
		return Literal.relation(Delivery.class, "notice")
				.arg("trackingId", trackingId)
				.arg("notice", notice)
				.solving(delivery(db, trackingId, lvar(), lvar(), lvar(), lvar(), lval(true), lvar(), lvar(), lvar(), lvar(), lvar())
						.and(notice.unifies(Notice.MISDIRECTED))
						.or(delivery(db, trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lval(true))
								.and(notice.unifies(Notice.ARRIVED))));
	}
}
