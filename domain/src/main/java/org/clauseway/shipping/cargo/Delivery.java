package org.clauseway.shipping.cargo;

// ABOUTME: dddsample's Delivery as the cargo stores it: one row the rules derive, in the shape both its faces
// ABOUTME: share — the stored relation and the derivation over the row — refreshed by the cargo's commands.

import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;
import static org.clauseway.shipping.cargo.CargoRules.currentVoyage;
import static org.clauseway.shipping.cargo.CargoRules.eta;
import static org.clauseway.shipping.cargo.CargoRules.lastKnownLocation;
import static org.clauseway.shipping.cargo.CargoRules.misdirected;
import static org.clauseway.shipping.cargo.CargoRules.nextExpectedActivity;
import static org.clauseway.shipping.cargo.CargoRules.routingStatus;
import static org.clauseway.shipping.cargo.CargoRules.transportStatus;
import static org.clauseway.shipping.cargo.CargoRules.unloadedAtDestination;

import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.DeliveryDTO;
import org.clauseway.shipping.HandlingActivity;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.TransportStatus;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@With
public final class Delivery {

	// -- the row: the key and the ten columns, as variables ------------------

	final Unifiable<String> trackingId;
	final Unifiable<RoutingStatus> routingStatus;
	final Unifiable<TransportStatus> transportStatus;
	final Unifiable<LOption<String>> lastKnownLocation;
	final Unifiable<LOption<String>> currentVoyage;
	final Unifiable<Boolean> misdirected;
	final Unifiable<LOption<Instant>> eta;
	final Unifiable<LOption<HandlingType>> nextType;
	final Unifiable<LOption<String>> nextLocation;
	final Unifiable<LOption<String>> nextVoyage;
	final Unifiable<Boolean> unloadedAtDestination;

	/** The cargo's row with every column a fresh variable: the shape a question and its template share. */
	public static Delivery fresh(Unifiable<String> trackingId) {
		return new Delivery(trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar());
	}

	/** The cargo's row with every column projected away: the shape a rule probes, naming only the columns it cares about. */
	public static Delivery probe(Unifiable<String> trackingId) {
		return new Delivery(trackingId, projected(), projected(), projected(), projected(), projected(), projected(),
				projected(), projected(), projected(), projected());
	}

	/** The row's columns applied to a relation, by name, the key indexed: the one spelling both faces share. */
	private Literal.Builder columns(Literal.Builder relation) {
		return relation
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
				.arg("unloadedAtDestination", unloadedAtDestination);
	}

	// -- the two faces: stored, and derived from the rules -------------------

	/** The delivery as stored: one row per cargo, the absent fields as none; replaced whole by every command. */
	static Literal stored(AnswerSource db, Delivery row) {
		return row.columns(Literal.relation(Delivery.class, "delivery")).from(db);
	}

	/**
	 * Delivery.derivedFrom as one rule: the delivery row the rules give now, in
	 * the stored relation's shape. The partial fields are reified to their
	 * columns — the value where the rule holds, none where it does not — and
	 * the predicates to booleans; the rules themselves stay partial for each other.
	 */
	public static Literal derived(AnswerSource db, Delivery r) {
		return r.columns(Literal.relation(Delivery.class, "derived"))
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

	// -- the refresh, and the value ------------------------------------------

	/**
	 * Every cargo command's last step: the stored row leaves and the row the
	 * rules derive over the staged state lands; an unchanged one cancels to nothing.
	 */
	static Transaction refresh(Transaction staged, String trackingId) {
		Delivery row = fresh(lval(trackingId));
		return staged
				.retracting(Cargo.rows(stored(staged, row), stored(null, row)))
				.asserting(Cargo.rows(derived(staged, row), stored(null, row)));
	}

	/** The rows a literal over this shape answers. */
	public Stream<Row> select(Literal delivery) {
		return Query.of(delivery).select(routingStatus, transportStatus, lastKnownLocation, currentVoyage, misdirected, eta,
				nextType, nextLocation, nextVoyage, unloadedAtDestination).rows();
	}

	/** One answered row as the value: absence as empty, the next activity from its three columns. */
	public DeliveryDTO value(Row row) {
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
