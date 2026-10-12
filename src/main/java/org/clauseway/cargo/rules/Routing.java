package org.clauseway.cargo.rules;

// ABOUTME: The route specification read as rules: whether the itinerary
// ABOUTME: satisfies it, and the routing status that follows.

import static org.clauseway.cargo.Schema.routeSpecification;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import org.clauseway.cargo.RoutingStatus;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;

public final class Routing {

	private final AnswerSource db;
	private final Itinerary itinerary;

	public Routing(AnswerSource db, Itinerary itinerary) {
		this.db = db;
		this.itinerary = itinerary;
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
