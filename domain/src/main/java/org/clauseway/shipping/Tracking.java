package org.clauseway.shipping;

// ABOUTME: The read face of the shipping domain — every derived fact as a Java
// ABOUTME: value, one section per aggregate's rules: history, itinerary, routing, delivery.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.Reads.first;
import static org.clauseway.shipping.Reads.holds;
import static org.clauseway.shipping.Reads.values;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.clauseway.functional.tuples.Tuple5;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.cargo.CargoRules;
import org.clauseway.shipping.cargo.DeliveryDTO;
import org.clauseway.shipping.cargo.HandlingActivityDTO;
import org.clauseway.shipping.cargo.LegDTO;
import org.clauseway.shipping.cargo.Notice;
import org.clauseway.shipping.cargo.RouteSpecificationDTO;
import org.clauseway.shipping.cargo.RoutingStatus;
import org.clauseway.shipping.handling.HandlingEvent;
import org.clauseway.shipping.handling.HandlingEventDTO;
import org.clauseway.shipping.handling.History;

public final class Tracking {

	private final AnswerSource db;
	private final Cargo cargo;

	Tracking(AnswerSource db, Cargo cargo) {
		this.db = db;
		this.cargo = cargo;
	}

	// -- history: the handling events ---------------------------------------

	/** The cargo's handling events, distinct, in completion order. */
	public List<HandlingEventDTO> handlingHistoryOf(String trackingId) {
		return History.event(db, HandlingEvent.fresh(lval(trackingId))).select()
				.map(HandlingEvent::toDto)
				.sorted(Comparator.comparing(HandlingEventDTO::getCompletionTime))
				.collect(Collectors.toList());
	}

	/** The most recently completed handling of the cargo, if any. */
	public Optional<HandlingEventDTO> lastEventOf(String trackingId) {
		return History.lastEvent(db, HandlingEvent.fresh(lval(trackingId))).select()
				.map(HandlingEvent::toDto)
				.findFirst();
	}

	// -- itinerary: the legs and what they plan --------------------------------

	/** The cargo's legs in itinerary order; empty when it is not routed. */
	public List<LegDTO> itineraryOf(String trackingId) {
		return cargo.itineraryOf(trackingId);
	}

	public Optional<LegDTO> firstLegOf(String trackingId) {
		return cargo.firstLegOf(trackingId);
	}

	public Optional<LegDTO> lastLegOf(String trackingId) {
		return cargo.lastLegOf(trackingId);
	}

	/** The handlings the cargo's itinerary plans, in order; empty when it is not routed. */
	public List<HandlingActivityDTO> planOf(String trackingId) {
		return cargo.planOf(trackingId);
	}

	/** Whether the cargo's itinerary accounts for the handling. */
	public boolean isExpected(String trackingId, HandlingEventDTO event) {
		return holds(CargoRules.expected(db, lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber())));
	}

	// -- routing: the specification and the routes that satisfy it ------------

	public Optional<RouteSpecificationDTO> routeSpecificationOf(String trackingId) {
		return cargo.routeSpecificationOf(trackingId);
	}

	public RoutingStatus routingStatusOf(String trackingId) {
		Unifiable<RoutingStatus> status = lvar();
		return first(CargoRules.routingStatus(db, lval(trackingId), status), status)
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
	}

	/** The itineraries the schedule offers the cargo's specification. */
	public List<List<LegDTO>> routeCandidatesOf(String trackingId) {
		Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs = lvar();
		return Query.of(CargoRules.candidate(db, lval(trackingId), legs)).solve(legs)
				.map(route -> route.get().elements()
						.map(leg -> leg.get())
						.map(leg -> new LegDTO(leg._1.get(), leg._2.get(), leg._3.get(), leg._4.get(), leg._5.get()))
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
	}

	// -- delivery: the stored row and the inspection ---------------------------

	/** The cargo's delivery, as its commands last stored it. */
	public DeliveryDTO deliveryOf(String trackingId) {
		return cargo.deliveryOf(trackingId);
	}

	/** The notices the cargo's delivery warrants now. */
	public List<Notice> noticesOf(String trackingId) {
		Unifiable<Notice> notice = lvar();
		return values(CargoRules.notice(db, lval(trackingId), notice), notice);
	}
}
