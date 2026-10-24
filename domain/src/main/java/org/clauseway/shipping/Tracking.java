package org.clauseway.shipping;

// ABOUTME: The read face of the shipping domain — every derived fact as a Java
// ABOUTME: value, one section per aggregate's rules: history, itinerary, routing, delivery.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.Reads.first;
import static org.clauseway.shipping.Reads.holds;
import static org.clauseway.shipping.Reads.values;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.literals.Question;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.cargo.CargoRules;
import org.clauseway.shipping.cargo.dto.DeliveryDto;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.cargo.dto.LegDto;
import org.clauseway.shipping.cargo.Notice;
import org.clauseway.shipping.cargo.CandidateLeg;
import org.clauseway.shipping.cargo.dto.RouteSpecificationDto;
import org.clauseway.shipping.cargo.RoutingStatus;
import org.clauseway.shipping.handling.HandlingEvent;
import org.clauseway.shipping.handling.HandlingEventDto;
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
	public List<HandlingEventDto> handlingHistoryOf(String trackingId) {
		HandlingEvent event = HandlingEvent.fresh(lval(trackingId));
		return Question.select(Query.of(History.event(db, event)), event)
				.map(HandlingEvent::toDto)
				.sorted(Comparator.comparing(HandlingEventDto::getCompletionTime))
				.collect(Collectors.toList());
	}

	/** The most recently completed handling of the cargo, if any. */
	public Optional<HandlingEventDto> lastEventOf(String trackingId) {
		HandlingEvent event = HandlingEvent.fresh(lval(trackingId));
		return Question.select(Query.of(History.lastEvent(db, event)), event)
				.map(HandlingEvent::toDto)
				.findFirst();
	}

	// -- itinerary: the legs and what they plan --------------------------------

	/** The cargo's legs in itinerary order; empty when it is not routed. */
	public List<LegDto> itineraryOf(String trackingId) {
		return cargo.itineraryOf(trackingId);
	}

	public Optional<LegDto> firstLegOf(String trackingId) {
		return cargo.firstLegOf(trackingId);
	}

	public Optional<LegDto> lastLegOf(String trackingId) {
		return cargo.lastLegOf(trackingId);
	}

	/** The handlings the cargo's itinerary plans, in order; empty when it is not routed. */
	public List<HandlingActivityDto> planOf(String trackingId) {
		return cargo.planOf(trackingId);
	}

	/** Whether the cargo's itinerary accounts for the handling. */
	public boolean isExpected(String trackingId, HandlingEventDto event) {
		return holds(CargoRules.expected(db, lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber())));
	}

	// -- routing: the specification and the routes that satisfy it ------------

	public Optional<RouteSpecificationDto> routeSpecificationOf(String trackingId) {
		return cargo.routeSpecificationOf(trackingId);
	}

	public RoutingStatus routingStatusOf(String trackingId) {
		Unifiable<RoutingStatus> status = lvar();
		return first(CargoRules.routingStatus(db, lval(trackingId), status), status)
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
	}

	/** The itineraries the schedule offers the cargo's specification. */
	public List<List<LegDto>> routeCandidatesOf(String trackingId) {
		Unifiable<LList<CandidateLeg>> legs = lvar();
		return Query.of(CargoRules.candidate(db, lval(trackingId), legs)).solve(legs)
				.map(route -> route.get().elements()
						.map(Term::get)
						.map(CandidateLeg::toDto)
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
	}

	// -- delivery: the stored row and the inspection ---------------------------

	/** The cargo's delivery, as its commands last stored it. */
	public DeliveryDto deliveryOf(String trackingId) {
		return cargo.deliveryOf(trackingId);
	}

	/** The notices the cargo's delivery warrants now. */
	public List<Notice> noticesOf(String trackingId) {
		Unifiable<Notice> notice = lvar();
		return values(CargoRules.notice(db, lval(trackingId), notice), notice);
	}
}
