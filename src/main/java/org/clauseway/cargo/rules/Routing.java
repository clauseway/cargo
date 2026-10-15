package org.clauseway.cargo.rules;

// ABOUTME: The route specification read as rules: whether the itinerary
// ABOUTME: satisfies it, the routing status that follows, and the routes that would.

import static org.clauseway.cargo.Schema.cargo;
import static org.clauseway.cargo.Schema.location;
import static org.clauseway.cargo.Schema.routeSpecification;
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

import java.time.Instant;
import org.clauseway.cargo.RoutingStatus;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.functional.tuples.Tuple5;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;

public final class Routing {

	private final AnswerSource db;
	private final Itinerary itinerary;
	private final Voyages voyages;

	public Routing(AnswerSource db, Itinerary itinerary, Voyages voyages) {
		this.db = db;
		this.itinerary = itinerary;
		this.voyages = voyages;
	}

	/** The RouteSpecification constructor's guard, and the ports it is made from: known, and not one and the same. */
	public Literal specificationDenial(Unifiable<String> origin, Unifiable<String> destination,
			Unifiable<String> reason) {
		return Literal.relation(Routing.class, "specificationDenial")
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("reason", reason)
				.solving(exclude(location(db, origin, projected()))
						.and(reason.unifies("unknown origin"))
						.or(exclude(location(db, destination, projected()))
								.and(reason.unifies("unknown destination")))
						.or(origin.unifies(destination)
								.and(reason.unifies("origin and destination are the same"))));
	}

	/** Booking's denials: the tracking id must be free, and the specification must hold up. */
	public Literal bookingDenial(Unifiable<String> trackingId, Unifiable<String> origin,
			Unifiable<String> destination, Unifiable<String> reason) {
		return Literal.relation(Routing.class, "bookingDenial")
				.arg("trackingId", trackingId)
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("reason", reason)
				.solving(cargo(db, trackingId, projected())
						.and(reason.unifies("already booked"))
						.or(specificationDenial(origin, destination, reason)));
	}

	/** Cargo.specifyNewRoute's denials: the cargo must exist, and the specification must hold up. */
	public Literal reroutingDenial(Unifiable<String> trackingId, Unifiable<String> origin,
			Unifiable<String> destination, Unifiable<String> reason) {
		return Literal.relation(Routing.class, "reroutingDenial")
				.arg("trackingId", trackingId)
				.arg("origin", origin)
				.arg("destination", destination)
				.arg("reason", reason)
				.solving(exclude(cargo(db, trackingId, projected()))
						.and(reason.unifies("unknown cargo"))
						.or(specificationDenial(origin, destination, reason)));
	}

	/**
	 * RouteSpecification.isSatisfiedBy: the itinerary starts at the specified
	 * origin, ends at the specified destination, and lands before the deadline.
	 */
	public Literal satisfied(Unifiable<String> trackingId) {
		Unifiable<String> origin = lvar();
		Unifiable<String> destination = lvar();
		Unifiable<Instant> deadline = lvar();
		Unifiable<Instant> finalArrival = lvar();
		return Literal.relation(Routing.class, "satisfied")
				.arg("trackingId", trackingId)
				.solving(routeSpecification(db, trackingId, origin, destination, deadline)
						.and(itinerary.firstLeg(trackingId, lvar(), origin, lvar(), lvar(), lvar()))
						.and(itinerary.lastLeg(trackingId, lvar(), lvar(), destination, lvar(), finalArrival))
						.and(Instants.lss(finalArrival, deadline)));
	}

	/**
	 * A route from a port to the destination, landing before the deadline: a
	 * stretch aboard one voyage to some other port, then either that port is
	 * the destination, or the route goes on from there by another voyage
	 * sailing no earlier than the cargo landed. The connection is what the
	 * cargo arrived by — when, and on which voyage — and none at the origin.
	 */
	public Literal path(Unifiable<String> from,
			Unifiable<LOption<Tuple2<Unifiable<Instant>, Unifiable<String>>>> connection,
			Unifiable<String> destination, Unifiable<Instant> deadline,
			Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs) {
		return Literal.relation(Routing.class, "path")
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
					return voyages.aboard(voyageNumber, from, to, departure, arrival)
							.and(exclude(from.unifies(to)))
							.and(matche(connection,
									loption(Goal::success),
									loption(c -> matche(c, tuple((landed, arrivedBy) ->
											Instants.leq(landed, departure)
													.and(exclude(arrivedBy.unifies(voyageNumber))))))))
							.and(Instants.lss(arrival, deadline))
							.and(to.unifies(destination).and(legs.unifies(LList.of(leg)))
									.or(exclude(to.unifies(destination))
											.and(path(to, some(lval(Tuple.of(arrival, voyageNumber))), destination, deadline, onward))
											.and(legs.unifies(LList.of(leg, onward)))));
				}));
	}

	/** RoutingService.fetchRoutesForSpecification: the routes the schedule offers the cargo's specification. */
	public Literal candidate(Unifiable<String> trackingId,
			Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs) {
		Unifiable<String> origin = lvar();
		Unifiable<String> destination = lvar();
		Unifiable<Instant> deadline = lvar();
		return Literal.relation(Routing.class, "candidate")
				.arg("trackingId", trackingId)
				.arg("legs", legs)
				.solving(routeSpecification(db, trackingId, origin, destination, deadline)
						.and(path(origin, none(), destination, deadline, legs)));
	}

	/** Not routed without an itinerary; routed when it satisfies the specification; misrouted otherwise. */
	public Literal routingStatus(Unifiable<String> trackingId, Unifiable<RoutingStatus> status) {
		return Literal.relation(Routing.class, "routingStatus")
				.arg("trackingId", trackingId)
				.arg("status", status)
				.solving(exclude(itinerary.anyLeg(trackingId)).and(status.unifies(RoutingStatus.NOT_ROUTED))
						.or(satisfied(trackingId).and(status.unifies(RoutingStatus.ROUTED)))
						.or(itinerary.anyLeg(trackingId).and(exclude(satisfied(trackingId)))
								.and(status.unifies(RoutingStatus.MISROUTED))));
	}
}
