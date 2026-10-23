package org.clauseway.shipping.cargo;

// ABOUTME: dddsample's Delivery as the cargo stores it: one row the rules derive, a shape both its faces are
// ABOUTME: built from — the stored relation and the derivation over the row — refreshed by the cargo's commands.

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
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.goals.Goal;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Rows;
import org.clauseway.pldb.relations.Shape;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.DeliveryDTO;
import org.clauseway.shipping.HandlingActivity;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.TransportStatus;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@With
public final class Delivery implements Shape<Delivery> {

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
		return fresh(trackingId).probe().withTrackingId(trackingId);
	}

	// -- the shape: what the engine builds both faces from -------------------

	@Override
	public Iterable<? extends Term<?>> members() {
		return Arrays.asList(trackingId, routingStatus, transportStatus, lastKnownLocation, currentVoyage, misdirected,
				eta, nextType, nextLocation, nextVoyage, unloadedAtDestination);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Delivery withMembers(Term<?>[] m) {
		return new Delivery((Unifiable<String>) m[0], (Unifiable<RoutingStatus>) m[1], (Unifiable<TransportStatus>) m[2],
				(Unifiable<LOption<String>>) m[3], (Unifiable<LOption<String>>) m[4], (Unifiable<Boolean>) m[5],
				(Unifiable<LOption<Instant>>) m[6], (Unifiable<LOption<HandlingType>>) m[7],
				(Unifiable<LOption<String>>) m[8], (Unifiable<LOption<String>>) m[9], (Unifiable<Boolean>) m[10]);
	}

	@Override
	public List<Property<?>> columns() {
		return Arrays.asList(
				Property.of("trackingId").indexed(),
				Property.of("routingStatus"),
				Property.of("transportStatus"),
				Property.of("lastKnownLocation"),
				Property.of("currentVoyage"),
				Property.of("misdirected"),
				Property.of("eta"),
				Property.of("nextType"),
				Property.of("nextLocation"),
				Property.of("nextVoyage"),
				Property.of("unloadedAtDestination"));
	}

	// -- the two faces: stored, and derived from the rules -------------------

	/** The delivery as stored: one row per cargo, the absent fields as none; replaced whole by every command. */
	Rows<Delivery> stored(AnswerSource db) {
		return Literal.relation(Delivery.class, "delivery").rows(this).from(db);
	}

	/**
	 * Delivery.derivedFrom as one rule: the delivery row the rules give now, in
	 * the stored relation's shape. The partial fields are reified to their
	 * columns — the value where the rule holds, none where it does not — and
	 * the predicates to booleans; the rules themselves stay partial for each other.
	 */
	public Rows<Delivery> derived(AnswerSource db) {
		return Literal.relation(Delivery.class, "derived").rows(this)
				.solving(r -> routingStatus(db, r.trackingId, r.routingStatus)
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
		return staged.replacing(row.stored(staged), row.derived(staged));
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
