package org.clauseway.shipping.cargo;

// ABOUTME: dddsample's Delivery as the cargo stores it: one row the rules derive, the row both its faces are
// ABOUTME: built from — the stored relation and the derivation over the row — refreshed by the cargo's commands.

import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.literals.Literal.flag;
import static org.clauseway.pldb.relations.literals.Literal.option;
import static org.clauseway.pldb.relations.Projected.projected;
import static org.clauseway.shipping.Reads.value;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.literals.External;
import org.clauseway.pldb.relations.literals.Literal;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.dto.DeliveryDto;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.handling.HandlingType;

@With
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Delivery implements Schema<Delivery> {

	// -- the row: the key and the ten columns, as variables ------------------
	Term<String> trackingId;
	Term<RoutingStatus> routingStatus;
	Term<TransportStatus> transportStatus;
	Term<LOption<String>> lastKnownLocation;
	Term<LOption<String>> currentVoyage;
	Term<Boolean> misdirected;
	Term<LOption<Instant>> eta;
	Term<LOption<HandlingType>> nextType;
	Term<LOption<String>> nextLocation;
	Term<LOption<String>> nextVoyage;
	Term<Boolean> unloadedAtDestination;

	/** The cargo's row with every column a fresh variable: the row a question and its template share. */
	public static Delivery fresh(Unifiable<String> trackingId) {
		return new Delivery(trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar());
	}

	/** The cargo's row with every column projected away: the row a rule probes, naming only the columns it cares about. */
	public static Delivery probe(Unifiable<String> trackingId) {
		return fresh(trackingId).probe().withTrackingId(trackingId);
	}

	// -- the row contract: what the engine builds both faces from -------------------

	@Override
	public Iterable<? extends Term<?>> members() {
		return Arrays.asList(trackingId, routingStatus, transportStatus, lastKnownLocation, currentVoyage, misdirected,
				eta, nextType, nextLocation, nextVoyage, unloadedAtDestination);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Delivery withMembers(Term<?>[] m) {
		return new Delivery((Term<String>) m[0], (Term<RoutingStatus>) m[1], (Term<TransportStatus>) m[2],
				(Term<LOption<String>>) m[3], (Term<LOption<String>>) m[4], (Term<Boolean>) m[5],
				(Term<LOption<Instant>>) m[6], (Term<LOption<HandlingType>>) m[7],
				(Term<LOption<String>>) m[8], (Term<LOption<String>>) m[9], (Term<Boolean>) m[10]);
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
	External<Delivery> stored(AnswerSource db) {
		return Literal.relation(Delivery.class, "delivery").args(this).from(db);
	}

	/**
	 * Delivery.derivedFrom as one rule: the delivery row the rules give now, in
	 * the stored relation's columns. The partial fields are reified to their
	 * columns — the value where the rule holds, none where it does not — and
	 * the predicates to booleans; the rules themselves stay partial for each other.
	 */
	public Literal<Delivery> derived(AnswerSource db) {
		Unifiable<String> trackingId = this.trackingId.unifiable();
		Unifiable<RoutingStatus> routingStatus = this.routingStatus.unifiable();
		Unifiable<TransportStatus> transportStatus = this.transportStatus.unifiable();
		Unifiable<LOption<String>> lastKnownLocation = this.lastKnownLocation.unifiable();
		Unifiable<LOption<String>> currentVoyage = this.currentVoyage.unifiable();
		Unifiable<Boolean> misdirected = this.misdirected.unifiable();
		Unifiable<LOption<Instant>> eta = this.eta.unifiable();
		Unifiable<LOption<HandlingType>> nextType = this.nextType.unifiable();
		Unifiable<LOption<String>> nextLocation = this.nextLocation.unifiable();
		Unifiable<LOption<String>> nextVoyage = this.nextVoyage.unifiable();
		Unifiable<Boolean> unloadedAtDestination = this.unloadedAtDestination.unifiable();
		return Literal.relation(Delivery.class, "derived").args(this)
				.solving(routingStatus(db, trackingId, routingStatus)
						.and(transportStatus(db, trackingId, transportStatus))
						.and(option(location -> lastKnownLocation(db, trackingId, location), lastKnownLocation))
						.and(option(voyage -> currentVoyage(db, trackingId, voyage), currentVoyage))
						.and(flag(misdirected(db, trackingId), misdirected))
						.and(option(time -> eta(db, trackingId, time), eta))
						.and(defer(() -> {
							Unifiable<HandlingType> type = lvar();
							Unifiable<String> location = lvar();
							return nextExpectedActivity(db, trackingId, type, location, nextVoyage)
									.and(nextType.unifies(some(type)))
									.and(nextLocation.unifies(some(location)));
						}).or(exclude(nextExpectedActivity(db, trackingId, projected(), projected(), projected()))
								.and(nextType.unifies(none()))
								.and(nextLocation.unifies(none()))
								.and(nextVoyage.unifies(none()))))
						.and(flag(unloadedAtDestination(db, trackingId), unloadedAtDestination)));
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
	public DeliveryDto toDto() {
		return new DeliveryDto(
				value(routingStatus),
				value(transportStatus),
				value(lastKnownLocation).toOptional(),
				value(currentVoyage).toOptional(),
				value(misdirected),
				value(eta).toOptional(),
				value(nextType).toOptional().map(type -> new HandlingActivityDto(type,
						value(nextLocation).toOptional().get(),
						value(nextVoyage).toOptional())),
				value(unloadedAtDestination));
	}
}
