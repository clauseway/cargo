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
import java.util.stream.Stream;
import org.clauseway.functional.tuples.Tuple5;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Solution;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.cargo.CargoRules;
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
	public List<HandlingEvent> handlingHistoryOf(String trackingId) {
		EventColumns c = new EventColumns();
		return c.events(c.select(History.event(db, lval(trackingId), c.type, c.location, c.voyageNumber, c.completed)))
				.sorted(Comparator.comparing(HandlingEvent::getCompletionTime))
				.collect(Collectors.toList());
	}

	/** The most recently completed handling of the cargo, if any. */
	public Optional<HandlingEvent> lastEventOf(String trackingId) {
		EventColumns c = new EventColumns();
		return c.events(c.select(History.lastEvent(db, lval(trackingId), c.type, c.location, c.voyageNumber, c.completed)))
				.findFirst();
	}

	/** The variables of one event's columns and the row-to-value reading over them. */
	private static final class EventColumns {
		final Unifiable<HandlingType> type = lvar();
		final Unifiable<String> location = lvar();
		final Unifiable<LOption<String>> voyageNumber = lvar();
		final Unifiable<Instant> completed = lvar();

		Stream<Solution> select(Literal events) {
			return Query.of(events).select(type, location, voyageNumber, completed).solutions();
		}

		Stream<HandlingEvent> events(Stream<Solution> rows) {
			return rows.map(row -> new HandlingEvent(row.get(type).get(), row.get(location).get(),
					row.get(voyageNumber).get().toOptional(), row.get(completed).get()));
		}
	}

	// -- itinerary: the legs and what they plan --------------------------------

	/** The cargo's legs in itinerary order; empty when it is not routed. */
	public List<Leg> itineraryOf(String trackingId) {
		return cargo.itineraryOf(trackingId);
	}

	public Optional<Leg> firstLegOf(String trackingId) {
		return cargo.firstLegOf(trackingId);
	}

	public Optional<Leg> lastLegOf(String trackingId) {
		return cargo.lastLegOf(trackingId);
	}

	/** The handlings the cargo's itinerary plans, in order; empty when it is not routed. */
	public List<HandlingActivity> planOf(String trackingId) {
		Unifiable<Integer> step = lvar();
		ActivityColumns c = new ActivityColumns();
		return c.activities(Query.of(CargoRules.plannedHandling(db, lval(trackingId), step, c.type, c.location, c.voyageNumber))
				.select(step, c.type, c.location, c.voyageNumber).solutions()
				.sorted(Comparator.comparing((Solution row) -> row.get(step).get())))
				.collect(Collectors.toList());
	}

	/** Whether the cargo's itinerary accounts for the handling. */
	public boolean isExpected(String trackingId, HandlingEvent event) {
		return holds(CargoRules.expected(db, lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber())));
	}

	/** The variables of one planned handling's columns and the row-to-value reading over them. */
	private static final class ActivityColumns {
		final Unifiable<HandlingType> type = lvar();
		final Unifiable<String> location = lvar();
		final Unifiable<LOption<String>> voyageNumber = lvar();

		Stream<HandlingActivity> activities(Stream<Solution> rows) {
			return rows.map(row -> new HandlingActivity(row.get(type).get(), row.get(location).get(),
					row.get(voyageNumber).get().toOptional()));
		}
	}

	// -- routing: the specification and the routes that satisfy it ------------

	public Optional<RouteSpecification> routeSpecificationOf(String trackingId) {
		return cargo.routeSpecificationOf(trackingId);
	}

	public RoutingStatus routingStatusOf(String trackingId) {
		Unifiable<RoutingStatus> status = lvar();
		return first(CargoRules.routingStatus(db, lval(trackingId), status), status)
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
	}

	/** The itineraries the schedule offers the cargo's specification. */
	public List<List<Leg>> routeCandidatesOf(String trackingId) {
		Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs = lvar();
		return Query.of(CargoRules.candidate(db, lval(trackingId), legs)).solve(legs)
				.map(route -> route.get().elements()
						.map(leg -> leg.get())
						.map(leg -> new Leg(leg._1.get(), leg._2.get(), leg._3.get(), leg._4.get(), leg._5.get()))
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
