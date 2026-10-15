package org.clauseway.shipping;

// ABOUTME: The read face of the shipping domain — every derived fact as a Java
// ABOUTME: value, one section per aggregate's rules: history, itinerary, routing, progress.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.clauseway.functional.tuples.Tuple5;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LList;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.cargo.Itinerary;
import org.clauseway.shipping.cargo.Progress;
import org.clauseway.shipping.cargo.Routing;
import org.clauseway.shipping.handling.History;

public final class Tracking {

	private final Cargo cargo;
	private final History history;
	private final Itinerary itinerary;
	private final Routing routing;
	private final Progress progress;

	Tracking(Cargo cargo, History history, Itinerary itinerary, Routing routing, Progress progress) {
		this.cargo = cargo;
		this.history = history;
		this.itinerary = itinerary;
		this.routing = routing;
		this.progress = progress;
	}

	// -- history: the handling events ---------------------------------------

	/** The cargo's handling events, distinct, in completion order. */
	public List<HandlingEvent> handlingHistoryOf(String trackingId) {
		EventColumns c = new EventColumns();
		return c.events(c.select(history.event(lval(trackingId), c.type, c.location, c.voyageNumber, c.completed)))
				.sorted(Comparator.comparing(HandlingEvent::getCompletionTime))
				.collect(Collectors.toList());
	}

	/** The most recently completed handling of the cargo, if any. */
	public Optional<HandlingEvent> lastEventOf(String trackingId) {
		EventColumns c = new EventColumns();
		return c.events(c.select(history.lastEvent(lval(trackingId), c.type, c.location, c.voyageNumber, c.completed)))
				.findFirst();
	}

	/** The variables of one event's columns and the row-to-value reading over them. */
	private static final class EventColumns {
		final Unifiable<HandlingType> type = lvar();
		final Unifiable<String> location = lvar();
		final Unifiable<LOption<String>> voyageNumber = lvar();
		final Unifiable<Instant> completed = lvar();

		Stream<Row> select(Literal events) {
			return Query.of(events).select(type, location, voyageNumber, completed).rows();
		}

		Stream<HandlingEvent> events(Stream<Row> rows) {
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
		return c.activities(Query.of(itinerary.plannedHandling(lval(trackingId), step, c.type, c.location, c.voyageNumber))
				.select(step, c.type, c.location, c.voyageNumber).rows()
				.sorted(Comparator.comparing((Row row) -> row.get(step).get())))
				.collect(Collectors.toList());
	}

	/** Whether the cargo's itinerary accounts for the handling. */
	public boolean isExpected(String trackingId, HandlingEvent event) {
		return holds(itinerary.expected(lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber())));
	}

	/** The variables of one planned handling's columns and the row-to-value reading over them. */
	private static final class ActivityColumns {
		final Unifiable<HandlingType> type = lvar();
		final Unifiable<String> location = lvar();
		final Unifiable<LOption<String>> voyageNumber = lvar();

		Stream<Row> select(Literal activities) {
			return Query.of(activities).select(type, location, voyageNumber).rows();
		}

		Stream<HandlingActivity> activities(Stream<Row> rows) {
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
		return first(routing.routingStatus(lval(trackingId), status), status)
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
	}

	/** The itineraries the schedule offers the cargo's specification. */
	public List<List<Leg>> routeCandidatesOf(String trackingId) {
		Unifiable<LList<Tuple5<Unifiable<String>, Unifiable<String>, Unifiable<String>, Unifiable<Instant>, Unifiable<Instant>>>> legs = lvar();
		return Query.of(routing.candidate(lval(trackingId), legs)).solve(legs)
				.map(route -> route.get().elements()
						.map(leg -> leg.get())
						.map(leg -> new Leg(leg._1.get(), leg._2.get(), leg._3.get(), leg._4.get(), leg._5.get()))
						.collect(Collectors.toList()))
				.collect(Collectors.toList());
	}

	// -- progress: the delivery and the inspection ----------------------------

	/** The cargo's delivery, every field derived now from the facts. */
	public Delivery deliveryOf(String trackingId) {
		Unifiable<String> id = lval(trackingId);
		Unifiable<TransportStatus> transport = lvar();
		Unifiable<String> location = lvar();
		Unifiable<String> voyageNumber = lvar();
		Unifiable<Instant> eta = lvar();
		return new Delivery(
				routingStatusOf(trackingId),
				first(progress.transportStatus(id, transport), transport)
						.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId)),
				first(progress.lastKnownLocation(id, location), location),
				first(progress.currentVoyage(id, voyageNumber), voyageNumber),
				holds(progress.misdirected(id)),
				first(progress.eta(id, eta), eta),
				nextExpectedActivityOf(trackingId),
				holds(progress.unloadedAtDestination(id)));
	}

	/** The inspection after a handling: the notices the cargo's state warrants now. */
	public List<Notice> inspect(String trackingId) {
		Unifiable<Notice> notice = lvar();
		return values(Query.of(progress.notice(lval(trackingId), notice)).solve(notice));
	}

	private Optional<HandlingActivity> nextExpectedActivityOf(String trackingId) {
		ActivityColumns c = new ActivityColumns();
		return c.activities(c.select(progress.nextExpectedActivity(lval(trackingId), c.type, c.location, c.voyageNumber)))
				.findFirst();
	}

	// -- reading answers ---------------------------------------------------------

	/** The first answer for one variable of a goal, if any. */
	private static <T> Optional<T> first(Literal goal, Unifiable<T> out) {
		return Query.of(goal).solve(out).findFirst().map(Reified::get);
	}

	/** Whether a ground goal has an answer at all. */
	private static boolean holds(Literal goal) {
		return Query.of(goal).solve(lval(true)).findAny().isPresent();
	}

	private static <T> List<T> values(Stream<Reified<T>> answers) {
		return answers.map(Reified::get).collect(Collectors.toList());
	}
}
