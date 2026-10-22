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
import org.clauseway.vavr.collection.Array;

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
	Literal stored(AnswerSource db) {
		return columns(Literal.relation(Delivery.class, "delivery")).from(db);
	}

	/**
	 * Delivery.derivedFrom as one rule: the delivery row the rules give now, in
	 * the stored relation's shape. The partial fields are reified to their
	 * columns — the value where the rule holds, none where it does not — and
	 * the predicates to booleans; the rules themselves stay partial for each other.
	 */
	public Literal derived(AnswerSource db) {
		Delivery r = this;
		return columns(Literal.relation(Delivery.class, "derived"))
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
				.retracting(Cargo.rows(row.stored(staged), row.stored(null)))
				.asserting(Cargo.rows(row.derived(staged), row.stored(null)));
	}

	/** The rows a literal over this shape answers, each a ground row: the variables are read off the literal's own columns. */
	public static Stream<Delivery> select(Literal delivery) {
		Delivery row = over(delivery.getArgs());
		return Query.of(delivery)
				.select(row.routingStatus, row.transportStatus, row.lastKnownLocation, row.currentVoyage, row.misdirected,
						row.eta, row.nextType, row.nextLocation, row.nextVoyage, row.unloadedAtDestination)
				.rows()
				.map(row::ground);
	}

	/** The row a literal was built from: its columns, in the order {@link #columns} applies them. */
	private static Delivery over(Array<Unifiable<?>> columns) {
		return new Delivery(column(columns, 0), column(columns, 1), column(columns, 2), column(columns, 3),
				column(columns, 4), column(columns, 5), column(columns, 6), column(columns, 7), column(columns, 8),
				column(columns, 9), column(columns, 10));
	}

	@SuppressWarnings("unchecked")
	private static <T> Unifiable<T> column(Array<Unifiable<?>> columns, int position) {
		return (Unifiable<T>) columns.get(position);
	}

	/** This row with every column bound to what the answer holds at it. */
	private Delivery ground(Row answer) {
		return new Delivery(trackingId, lval(answer.get(routingStatus).get()), lval(answer.get(transportStatus).get()),
				lval(answer.get(lastKnownLocation).get()), lval(answer.get(currentVoyage).get()),
				lval(answer.get(misdirected).get()), lval(answer.get(eta).get()), lval(answer.get(nextType).get()),
				lval(answer.get(nextLocation).get()), lval(answer.get(nextVoyage).get()),
				lval(answer.get(unloadedAtDestination).get()));
	}

	/** A ground row as the value: absence as empty, the next activity from its three columns. */
	public DeliveryDTO toDto() {
		return new DeliveryDTO(
				value(routingStatus),
				value(transportStatus),
				value(lastKnownLocation).toOptional(),
				value(currentVoyage).toOptional(),
				value(misdirected),
				value(eta).toOptional(),
				value(nextType).toOptional().map(type -> new HandlingActivity(type,
						value(nextLocation).toOptional().get(),
						value(nextVoyage).toOptional())),
				value(unloadedAtDestination));
	}

	/** A ground column's value; a row that was not selected has none. */
	private static <T> T value(Unifiable<T> column) {
		return column.asVal().orElseThrow(() -> new IllegalStateException("not a ground row: " + column));
	}
}
