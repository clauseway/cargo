package org.clauseway.shipping.cargo;

// ABOUTME: The Cargo aggregate root, the door Java calls: its relations package-private and written by its
// ABOUTME: commands alone, its parts read as values, its delivery refreshed by every command.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.Reads.values;
import static org.clauseway.shipping.cargo.CargoRules.cargo;
import static org.clauseway.shipping.cargo.CargoRules.lastHandling;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.Command;
import org.clauseway.shipping.cargo.dto.DeliveryDto;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.cargo.dto.LegDto;
import org.clauseway.shipping.cargo.dto.RouteSpecificationDto;
import org.clauseway.shipping.handling.HandlingEventDto;
import org.clauseway.vavr.control.Try;

@AllArgsConstructor
public final class Cargo {
	private final Transaction db;

	// -- the parts, as values ----------------------------------------------

	public Optional<RouteSpecificationDto> routeSpecificationOf(String trackingId) {
		return RouteSpecification.fresh(lval(trackingId)).stored(db).select()
				.map(RouteSpecification::toDto)
				.findFirst();
	}

	/** The cargo's legs in itinerary order; empty when it is not routed. */
	public List<LegDto> itineraryOf(String trackingId) {
		return Leg.fresh(lval(trackingId)).stored(db).select()
				.sorted(Comparator.comparing((Leg leg) -> leg.index.get()))
				.map(Leg::toDto)
				.collect(Collectors.toList());
	}

	public Optional<LegDto> firstLegOf(String trackingId) {
		return CargoRules.firstLeg(db, Leg.fresh(lval(trackingId))).select().map(Leg::toDto).findFirst();
	}

	public Optional<LegDto> lastLegOf(String trackingId) {
		return CargoRules.lastLeg(db, Leg.fresh(lval(trackingId))).select().map(Leg::toDto).findFirst();
	}

	/** The handlings the cargo's itinerary plans, in order; empty when it is not routed. */
	public List<HandlingActivityDto> planOf(String trackingId) {
		return CargoRules.plannedHandling(db, HandlingActivity.fresh(lval(trackingId))).select()
				.sorted(Comparator.comparing((HandlingActivity planned) -> planned.step.get()))
				.map(HandlingActivity::toDto)
				.collect(Collectors.toList());
	}

	/** The cargo's delivery as its commands last stored it. */
	public DeliveryDto deliveryOf(String trackingId) {
		return Delivery.fresh(lval(trackingId)).stored(db).select()
				.map(Delivery::toDto)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no such cargo: " + trackingId));
	}

	// -- the denials, by name; empty means allowed ---------------------------

	private List<String> bookingDenials(String trackingId, String origin, String destination) {
		return reasons(reason -> CargoRules.bookingDenial(db, lval(trackingId), lval(origin), lval(destination), reason));
	}

	private List<String> reroutingDenials(String trackingId, String origin, String destination) {
		return reasons(reason -> CargoRules.reroutingDenial(db, lval(trackingId), lval(origin), lval(destination), reason));
	}

	/** The denial every command on a cargo that is not booked gets. */
	private List<String> bookedDenials(String trackingId) {
		return reasons(reason -> CargoRules.routingDenial(db, lval(trackingId), reason));
	}

	private List<String> routingDenials(String trackingId, List<LegDto> legs) {
		Stream<String> booked = bookedDenials(trackingId).stream();
		Stream<String> shape = legs.isEmpty() ? Stream.of("empty itinerary") : Stream.empty();
		Stream<String> parts = legs.stream().flatMap(leg -> reasons(reason -> CargoRules.legDenial(db,
				lval(leg.getVoyageNumber()), lval(leg.getLoadLocation()), lval(leg.getUnloadLocation()), reason)).stream());
		return Stream.concat(booked, Stream.concat(shape, parts)).distinct().collect(Collectors.toList());
	}

	/** Every reason a denial rule gives. */
	private static List<String> reasons(Function<Unifiable<String>, Literal<?>> denial) {
		Unifiable<String> reason = lvar();
		return values(denial.apply(reason), reason);
	}

	// -- the commands: each refused by name, or its facts landed and the delivery refreshed -------------

	/** Booking: the cargo exists, with its origin, its first requirement and its first delivery. */
	public Try<Transaction> book(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return Command.guarded(bookingDenials(trackingId, origin, destination), () -> Delivery.refresh(db.asserting(
				cargo(db, lval(trackingId), lval(origin)),
				RouteSpecification.of(trackingId, new RouteSpecificationDto(origin, destination, arrivalDeadline)).stored(db)), trackingId));
	}

	/** Cargo.specifyNewRoute. The specification is a value: the old one goes whole, the new one lands, the same one is nothing; the cargo's origin stays. */
	public Try<Transaction> specify(String trackingId, String origin, String destination, Instant arrivalDeadline) {
		return Command.guarded(reroutingDenials(trackingId, origin, destination), () -> {
			RouteSpecificationDto next = new RouteSpecificationDto(origin, destination, arrivalDeadline);
			Optional<RouteSpecificationDto> current = routeSpecificationOf(trackingId);
			if (current.filter(next::equals).isPresent()) {
				return db;
			}
			return Delivery.refresh(db
					.retracting(rows(current.map(old -> RouteSpecification.of(trackingId, old).stored(db))))
					.asserting(RouteSpecification.of(trackingId, next).stored(db)), trackingId);
		});
	}

	/** Cargo.assignToRoute. The itinerary is a value: the old legs go, the new ones land in order; a leg both have at one index is neither. */
	public Try<Transaction> route(String trackingId, List<LegDto> legs) {
		return Command.guarded(routingDenials(trackingId, legs), () -> {
			List<LegDto> current = itineraryOf(trackingId);
			return Delivery.refresh(db
					.retracting(legsDiffering(db, trackingId, current, legs))
					.asserting(legsDiffering(db, trackingId, legs, current)), trackingId);
		});
	}

	/** CargoInspectionService.inspectCargo, the cargo's half: shown the handling side's latest event, it keeps it and re-derives its delivery. */
	public Try<Transaction> inspect(String trackingId, HandlingEventDto latest) {
		return Command.guarded(bookedDenials(trackingId), () -> Delivery.refresh(db
				.clearing(anyHandling(db, trackingId))
				.asserting(shown(db, trackingId, latest)), trackingId));
	}

	/** The inspection of a cargo the handling side has nothing for: it keeps no handling and re-derives its delivery. */
	public Try<Transaction> inspect(String trackingId) {
		return Command.guarded(bookedDenials(trackingId), () -> Delivery.refresh(db
				.clearing(anyHandling(db, trackingId)), trackingId));
	}

	// -- the facts and rows the commands land and retract --------------------

	/** The legs of one itinerary the other does not have at the same index, as facts. */
	private static List<Literal<?>> legsDiffering(AnswerSource db, String trackingId, List<LegDto> legs, List<LegDto> other) {
		return IntStream.range(0, legs.size())
				.filter(i -> i >= other.size() || !legs.get(i).equals(other.get(i)))
				.mapToObj(i -> (Literal<?>) Leg.of(trackingId, i, legs.get(i)).stored(db))
				.collect(Collectors.toList());
	}

	/** A handling as the cargo keeps it, as a fact. */
	private static Literal<?> shown(AnswerSource db, String trackingId, HandlingEventDto event) {
		return lastHandling(db, lval(trackingId), lval(event.getType()), lval(event.getLocation()),
				LOption.of(event.getVoyageNumber()), lval(event.getCompletionTime()));
	}

	/** Whatever handling the cargo holds, as a clearing takes it. */
	private static Literal<?> anyHandling(AnswerSource db, String trackingId) {
		return lastHandling(db, lval(trackingId), lvar(), lvar(), lvar(), lvar());
	}

	/** A fact that may be absent, as the list a write door takes. */
	private static List<Literal<?>> rows(Optional<? extends Literal<?>> fact) {
		return fact.map(Collections::<Literal<?>>singletonList).orElse(Collections.emptyList());
	}
}
