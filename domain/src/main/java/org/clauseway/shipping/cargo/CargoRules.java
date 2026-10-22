package org.clauseway.shipping.cargo;

// ABOUTME: The cargo's rules, static over any answer source: the itinerary's legs and the handlings it plans,
// ABOUTME: the specification satisfied and the routes that would, the delivery derived from the last handling shown.

import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.goals.Matche.loption;
import static org.clauseway.logic.goals.Matche.matche;
import static org.clauseway.logic.goals.Matche.tuple;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;
import static org.clauseway.shipping.cargo.Cargo.cargo;
import static org.clauseway.shipping.cargo.Cargo.delivery;
import static org.clauseway.shipping.cargo.Cargo.lastHandling;
import static org.clauseway.shipping.cargo.Cargo.leg;
import static org.clauseway.shipping.cargo.Cargo.routeSpecification;

import java.time.Instant;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.functional.tuples.Tuple5;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Notice;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.TransportStatus;
import org.clauseway.shipping.location.Locations;
import org.clauseway.shipping.voyage.Voyages;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CargoRules {

	// -- the cargo -----------------------------------------------------------

	/** The cargo is booked. */
	public static Literal known(AnswerSource db, Unifiable<String> trackingId) {
		return Literal.relation(CargoRules.class, "known")
				.arg("trackingId", trackingId)
				.solving(cargo(db, trackingId, projected()));
	}

	// -- the itinerary: its legs by index, and the handlings it plans ---------

	/** The cargo is routed: it has at least one leg. */
	public static Literal anyLeg(AnswerSource db, Unifiable<String> trackingId) {
		return Literal.relation(CargoRules.class, "anyLeg")
				.arg("trackingId", trackingId)
				.solving(leg(db, trackingId, projected(), projected(), projected(), projected(), projected(), projected()));
	}

	/** The itinerary's first leg: the one at index 0. */
	public static Literal firstLeg(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> voyageNumber,
			Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		return Literal.relation(CargoRules.class, "firstLeg")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(leg(db, trackingId, lval(0), voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime));
	}

	/** The index of the itinerary's last leg: one with no leg at the next index. */
	public static Literal lastIndex(AnswerSource db, Unifiable<String> trackingId, Unifiable<Integer> index) {
		Unifiable<Integer> next = lvar();
		return Literal.relation(CargoRules.class, "lastIndex")
				.arg("trackingId", trackingId)
				.arg("index", index)
				.solving(leg(db, trackingId, index, projected(), projected(), projected(), projected(), projected())
						.and(Ints.addo(index, lval(1), next))
						.and(exclude(leg(db, trackingId, next, projected(), projected(), projected(),
								projected(), projected()))));
	}

	/** The itinerary's last leg. */
	public static Literal lastLeg(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> voyageNumber,
			Unifiable<String> loadLocation, Unifiable<String> unloadLocation,
			Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		Unifiable<Integer> index = lvar();
		return Literal.relation(CargoRules.class, "lastLeg")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(leg(db, trackingId, index, voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime)
						.and(lastIndex(db, trackingId, index)));
	}

	/**
	 * The sequence of handlings the itinerary plans: a receipt at the first
	 * leg's port (step 0); for the leg at index i, a load at its port on its
	 * voyage (step 2i+1) and an unload where it lands (step 2i+2); and a
	 * claim where the last leg lands. Customs is not planned; it may happen
	 * anywhere.
	 */
	public static Literal plannedHandling(AnswerSource db, Unifiable<String> trackingId, Unifiable<Integer> step,
			Unifiable<HandlingType> type, Unifiable<String> location, Unifiable<LOption<String>> voyageNumber) {
		Unifiable<Integer> index = lvar();
		Unifiable<Integer> twice = lvar();
		Unifiable<String> legVoyage = lvar();
		return Literal.relation(CargoRules.class, "plannedHandling")
				.arg("trackingId", trackingId)
				.arg("step", step)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(type.unifies(HandlingType.RECEIVE)
						.and(firstLeg(db, trackingId, projected(), location, projected(), projected(), projected()))
						.and(step.unifies(0))
						.and(voyageNumber.unifies(none()))
						.or(type.unifies(HandlingType.LOAD)
								.and(leg(db, trackingId, index, legVoyage, location, projected(), projected(), projected()))
								.and(voyageNumber.unifies(some(legVoyage)))
								.and(Ints.multo(index, lval(2), twice))
								.and(Ints.addo(twice, lval(1), step)))
						.or(type.unifies(HandlingType.UNLOAD)
								.and(leg(db, trackingId, index, legVoyage, projected(), location, projected(), projected()))
								.and(voyageNumber.unifies(some(legVoyage)))
								.and(Ints.multo(index, lval(2), twice))
								.and(Ints.addo(twice, lval(2), step)))
						.or(type.unifies(HandlingType.CLAIM)
								.and(leg(db, trackingId, index, projected(), projected(), location, projected(), projected()))
								.and(lastIndex(db, trackingId, index))
								.and(Ints.multo(index, lval(2), twice))
								.and(Ints.addo(twice, lval(3), step))
								.and(voyageNumber.unifies(none()))));
	}

	/**
	 * The handling the itinerary accounts for: a planned one, customs
	 * anywhere; and everything, for a cargo with no itinerary.
	 */
	public static Literal expected(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber) {
		return Literal.relation(CargoRules.class, "expected")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(exclude(anyLeg(db, trackingId))
						.or(plannedHandling(db, trackingId, projected(), type, location, voyageNumber))
						.or(type.unifies(HandlingType.CUSTOMS)));
	}

	// -- the routing: the commands' denials, the specification satisfied, the routes that would --

	/** The RouteSpecification constructor's guard, and the ports it is made from: known, and not one and the same. */
	public static Literal specificationDenial(AnswerSource db, Unifiable<String> origin, Unifiable<String> destination,
			Unifiable<String> reason) {
		return Literal.relation(CargoRules.class, "specificationDenial")
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("reason", reason)
				.solving(exclude(Locations.known(db, origin))
						.and(reason.unifies("unknown origin"))
						.or(exclude(Locations.known(db, destination))
								.and(reason.unifies("unknown destination")))
						.or(origin.unifies(destination)
								.and(reason.unifies("origin and destination are the same"))));
	}

	/** Booking's denials: the tracking id must be free, and the specification must hold up. */
	public static Literal bookingDenial(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> origin,
			Unifiable<String> destination, Unifiable<String> reason) {
		return Literal.relation(CargoRules.class, "bookingDenial")
				.arg("trackingId", trackingId)
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("reason", reason)
				.solving(cargo(db, trackingId, projected())
						.and(reason.unifies("already booked"))
						.or(specificationDenial(db, origin, destination, reason)));
	}

	/** Cargo.assignToRoute's denial: the cargo must exist. */
	public static Literal routingDenial(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> reason) {
		return Literal.relation(CargoRules.class, "routingDenial")
				.arg("trackingId", trackingId)
				.arg("reason", reason)
				.solving(exclude(cargo(db, trackingId, projected()))
						.and(reason.unifies("unknown cargo")));
	}

	/** The Leg constructor's parts: a voyage and two ports, each known. */
	public static Literal legDenial(AnswerSource db, Unifiable<String> voyageNumber, Unifiable<String> loadLocation,
			Unifiable<String> unloadLocation, Unifiable<String> reason) {
		return Literal.relation(CargoRules.class, "legDenial")
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("reason", reason)
				.solving(exclude(Voyages.known(db, voyageNumber))
						.and(reason.unifies("unknown voyage"))
						.or(exclude(Locations.known(db, loadLocation))
								.and(reason.unifies("unknown load port")))
						.or(exclude(Locations.known(db, unloadLocation))
								.and(reason.unifies("unknown unload port"))));
	}

	/** Cargo.specifyNewRoute's denials: the cargo must exist, and the specification must hold up. */
	public static Literal reroutingDenial(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> origin,
			Unifiable<String> destination, Unifiable<String> reason) {
		return Literal.relation(CargoRules.class, "reroutingDenial")
				.arg("trackingId", trackingId)
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("reason", reason)
				.solving(routingDenial(db, trackingId, reason)
						.or(specificationDenial(db, origin, destination, reason)));
	}

	/**
	 * RouteSpecification.isSatisfiedBy: the itinerary starts at the specified
	 * origin, ends at the specified destination, and lands before the deadline.
	 */
	public static Literal satisfied(AnswerSource db, Unifiable<String> trackingId) {
		Unifiable<String> origin = lvar();
		Unifiable<String> destination = lvar();
		Unifiable<Instant> deadline = lvar();
		Unifiable<Instant> finalArrival = lvar();
		return Literal.relation(CargoRules.class, "satisfied")
				.arg("trackingId", trackingId)
				.solving(routeSpecification(db, trackingId, origin, destination, deadline)
						.and(firstLeg(db, trackingId, projected(), origin, projected(), projected(), projected()))
						.and(lastLeg(db, trackingId, projected(), projected(), destination, projected(), finalArrival))
						.and(Instants.lss(finalArrival, deadline)));
	}

	/**
	 * A route from a port to the destination, landing before the deadline: a
	 * stretch aboard one voyage to some other port, then either that port is
	 * the destination, or the route goes on from there by another voyage
	 * sailing no earlier than the cargo landed. The connection is what the
	 * cargo arrived by — when, and on which voyage — and none at the origin.
	 */
	public static Literal path(AnswerSource db, Unifiable<String> from,
			Unifiable<LOption<Tuple2<Unifiable<Instant>, Unifiable<String>>>> connection,
			Unifiable<String> destination, Unifiable<Instant> deadline,
			Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs) {
		return Literal.relation(CargoRules.class, "path")
				.arg("from", from)
				.arg("connection", connection)
				.arg("destination", destination)
				.arg("deadline", deadline)
				.arg("legs", legs)
				.solving(defer(() -> {
					Unifiable<String> voyageNumber = lvar();
					Unifiable<String> to = lvar();
					Unifiable<Instant> departure = lvar();
					Unifiable<Instant> arrival = lvar();
					Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> onward = lvar();
					Unifiable<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>> leg =
							lval(Tuple.of(voyageNumber, from, to, departure, arrival));
					return Voyages.aboard(db, voyageNumber, from, to, departure, arrival)
							.and(exclude(from.unifies(to)))
							.and(matche(connection,
									loption(Goal::success),
									loption(c -> matche(c, tuple((landed, arrivedBy) ->
											Instants.leq(landed, departure)
													.and(exclude(arrivedBy.unifies(voyageNumber))))))))
							.and(Instants.lss(arrival, deadline))
							.and(to.unifies(destination).and(legs.unifies(LList.of(leg)))
									.or(exclude(to.unifies(destination))
											.and(path(db, to, some(lval(Tuple.of(arrival, voyageNumber))), destination, deadline, onward))
											.and(legs.unifies(LList.of(leg, onward)))));
				}));
	}

	/** RoutingService.fetchRoutesForSpecification: the routes the schedule offers the cargo's specification. */
	public static Literal candidate(AnswerSource db, Unifiable<String> trackingId,
			Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs) {
		Unifiable<String> origin = lvar();
		Unifiable<String> destination = lvar();
		Unifiable<Instant> deadline = lvar();
		return Literal.relation(CargoRules.class, "candidate")
				.arg("trackingId", trackingId)
				.arg("legs", legs)
				.solving(routeSpecification(db, trackingId, origin, destination, deadline)
						.and(path(db, origin, none(), destination, deadline, legs)));
	}

	/** Not routed without an itinerary; routed when it satisfies the specification; misrouted otherwise. */
	public static Literal routingStatus(AnswerSource db, Unifiable<String> trackingId, Unifiable<RoutingStatus> status) {
		return Literal.relation(CargoRules.class, "routingStatus")
				.arg("trackingId", trackingId)
				.arg("status", status)
				.solving(exclude(anyLeg(db, trackingId)).and(status.unifies(RoutingStatus.NOT_ROUTED))
						.or(satisfied(db, trackingId).and(status.unifies(RoutingStatus.ROUTED)))
						.or(anyLeg(db, trackingId).and(exclude(satisfied(db, trackingId)))
								.and(status.unifies(RoutingStatus.MISROUTED))));
	}

	// -- the delivery: where the cargo is, whether it strayed, what comes next ---

	/** The transport status each handling kind leaves the cargo in: a lookup, as a relation over its own facts. */
	private static Literal transportOf(AnswerSource table, Unifiable<HandlingType> type,
			Unifiable<TransportStatus> status) {
		return Literal.relation(CargoRules.class, "transportOf")
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
	private static Literal handled(AnswerSource db, Unifiable<String> trackingId) {
		return Literal.relation(CargoRules.class, "handled")
				.arg("trackingId", trackingId)
				.solving(lastHandling(db, trackingId, projected(), projected(), projected(), projected()));
	}

	/** Not received until handled; afterwards, wherever the last handling left it. */
	public static Literal transportStatus(AnswerSource db, Unifiable<String> trackingId, Unifiable<TransportStatus> status) {
		return Literal.relation(CargoRules.class, "transportStatus")
				.arg("trackingId", trackingId)
				.arg("status", status)
				.solving(exclude(handled(db, trackingId)).and(status.unifies(TransportStatus.NOT_RECEIVED))
						.or(defer(() -> {
							Unifiable<HandlingType> type = lvar();
							return lastHandling(db, trackingId, type, projected(), projected(), projected())
									.and(transportOf(TRANSPORT, type, status));
						})));
	}

	/** Where the cargo was last handled. */
	public static Literal lastKnownLocation(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> location) {
		return Literal.relation(CargoRules.class, "lastKnownLocation")
				.arg("trackingId", trackingId)
				.arg("location", location)
				.solving(lastHandling(db, trackingId, projected(), location, projected(), projected()));
	}

	/** The voyage the cargo is aboard: the one it was last loaded onto, while loaded. */
	public static Literal currentVoyage(AnswerSource db, Unifiable<String> trackingId, Unifiable<String> voyageNumber) {
		return Literal.relation(CargoRules.class, "currentVoyage")
				.arg("trackingId", trackingId)
				.arg("voyageNumber", voyageNumber)
				.solving(lastHandling(db, trackingId, lval(HandlingType.LOAD), projected(), some(voyageNumber), projected()));
	}

	/** The last handling is one the itinerary does not account for. */
	public static Literal misdirected(AnswerSource db, Unifiable<String> trackingId) {
		return Literal.relation(CargoRules.class, "misdirected")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<HandlingType> type = lvar();
					Unifiable<String> location = lvar();
					Unifiable<LOption<String>> voyageNumber = lvar();
					return lastHandling(db, trackingId, type, location, voyageNumber, projected())
							.and(exclude(expected(db, trackingId, type, location, voyageNumber)));
				}));
	}

	/** Routed as specified and not misdirected. */
	public static Literal onTrack(AnswerSource db, Unifiable<String> trackingId) {
		return Literal.relation(CargoRules.class, "onTrack")
				.arg("trackingId", trackingId)
				.solving(routingStatus(db, trackingId, lval(RoutingStatus.ROUTED))
						.and(exclude(misdirected(db, trackingId))));
	}

	/** The estimated arrival: the last leg's unload time, while on track. */
	public static Literal eta(AnswerSource db, Unifiable<String> trackingId, Unifiable<Instant> time) {
		return Literal.relation(CargoRules.class, "eta")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(onTrack(db, trackingId)
						.and(lastLeg(db, trackingId, projected(), projected(), projected(), projected(), time)));
	}

	/** The last handling was an unload at the specified destination. */
	public static Literal unloadedAtDestination(AnswerSource db, Unifiable<String> trackingId) {
		return Literal.relation(CargoRules.class, "unloadedAtDestination")
				.arg("trackingId", trackingId)
				.solving(defer(() -> {
					Unifiable<String> location = lvar();
					return lastHandling(db, trackingId, lval(HandlingType.UNLOAD), location, projected(), projected())
							.and(routeSpecification(db, trackingId, projected(), location, projected()));
				}));
	}

	/**
	 * What should happen to the cargo next, while on track: the plan's first
	 * step before any handling, otherwise the step after the one the last
	 * handling was.
	 */
	public static Literal nextExpectedActivity(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber) {
		return Literal.relation(CargoRules.class, "nextExpectedActivity")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.solving(onTrack(db, trackingId)
						.and(exclude(handled(db, trackingId))
								.and(plannedHandling(db, trackingId, lval(0), type, location, voyageNumber))
								.or(defer(() -> {
									Unifiable<HandlingType> lastType = lvar();
									Unifiable<String> lastLocation = lvar();
									Unifiable<LOption<String>> lastVoyage = lvar();
									Unifiable<Integer> step = lvar();
									Unifiable<Integer> next = lvar();
									return lastHandling(db, trackingId, lastType, lastLocation, lastVoyage, projected())
											.and(plannedHandling(db, trackingId, step, lastType, lastLocation, lastVoyage))
											.and(Ints.addo(step, lval(1), next))
											.and(plannedHandling(db, trackingId, next, type, location, voyageNumber));
								}))));
	}

	/**
	 * Delivery.derivedFrom as one rule: the delivery row the rules give now, in
	 * the stored relation's shape. The partial fields are reified to their
	 * columns — the value where the rule holds, none where it does not — and
	 * the predicates to booleans; the rules themselves stay partial for each other.
	 */
	public static Literal derivedDelivery(AnswerSource db, DeliveryRow r) {
		return r.columns(Literal.relation(CargoRules.class, "derivedDelivery"))
				.solving(routingStatus(db, r.trackingId, r.routingStatus)
						.and(transportStatus(db, r.trackingId, r.transportStatus))
						.and(optional(location -> lastKnownLocation(db, r.trackingId, location), r.lastKnownLocation))
						.and(optional(voyage -> currentVoyage(db, r.trackingId, voyage), r.currentVoyage))
						.and(flag(misdirected(db, r.trackingId), r.misdirected))
						.and(optional(time -> eta(db, r.trackingId, time), r.eta))
						.and(defer(() -> {
							Unifiable<HandlingType> type = lvar();
							Unifiable<String> location = lvar();
							return nextExpectedActivity(db, r.trackingId, type, location, r.nextVoyage)
									.and(r.nextType.unifies(some(type)))
									.and(r.nextLocation.unifies(some(location)));
						}).or(exclude(nextExpectedActivity(db, r.trackingId, projected(), projected(), projected()))
								.and(r.nextType.unifies(none()))
								.and(r.nextLocation.unifies(none()))
								.and(r.nextVoyage.unifies(none()))))
						.and(flag(unloadedAtDestination(db, r.trackingId), r.unloadedAtDestination)));
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
	public static Literal notice(AnswerSource db, Unifiable<String> trackingId, Unifiable<Notice> notice) {
		return Literal.relation(CargoRules.class, "notice")
				.arg("trackingId", trackingId)
				.arg("notice", notice)
				.solving(delivery(db, DeliveryRow.probe(trackingId).withMisdirected(lval(true)))
						.and(notice.unifies(Notice.MISDIRECTED))
						.or(delivery(db, DeliveryRow.probe(trackingId).withUnloadedAtDestination(lval(true)))
								.and(notice.unifies(Notice.ARRIVED))));
	}
}
