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
import static org.clauseway.shipping.cargo.CargoRules.currentVoyage;
import static org.clauseway.shipping.cargo.CargoRules.eta;
import static org.clauseway.shipping.cargo.CargoRules.known;
import static org.clauseway.shipping.cargo.CargoRules.lastKnownLocation;
import static org.clauseway.shipping.cargo.CargoRules.misdirected;
import static org.clauseway.shipping.cargo.CargoRules.nextExpectedActivity;
import static org.clauseway.shipping.cargo.CargoRules.routingStatus;
import static org.clauseway.shipping.cargo.CargoRules.transportStatus;
import static org.clauseway.shipping.cargo.CargoRules.unloadedAtDestination;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Solution;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.literals.External;
import org.clauseway.pldb.relations.literals.Literal;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.dto.DeliveryDto;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.handling.HandlingType;

@With
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Delivery implements Schema<DeliveryDto> {

	// -- the row: the key and the ten columns, as variables ------------------
	Unifiable<String> trackingId;
	Unifiable<RoutingStatus> routingStatus;
	Unifiable<TransportStatus> transportStatus;
	Unifiable<LOption<String>> lastKnownLocation;
	Unifiable<LOption<String>> currentVoyage;
	Unifiable<Boolean> misdirected;
	Unifiable<LOption<Instant>> eta;
	Unifiable<LOption<HandlingType>> nextType;
	Unifiable<LOption<String>> nextLocation;
	Unifiable<LOption<String>> nextVoyage;
	Unifiable<Boolean> unloadedAtDestination;

	/** The cargo's row with every column a fresh variable: the row a question and its template share. */
	public static Delivery fresh(Unifiable<String> trackingId) {
		return new Delivery(trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar());
	}

	/** The cargo's row with every column projected away: the row a rule probes, naming only the columns it cares about. */
	public static Delivery probe(Unifiable<String> trackingId) {
		return new Delivery(trackingId, projected(), projected(), projected(), projected(), projected(), projected(),
				projected(), projected(), projected(), projected());
	}

	// -- the schema: the keys the head is over, the columns, the value ---------------

	@Override
	public Schema.Anonymous describe() {
		return Schema.builder()
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
				.arg("unloadedAtDestination", unloadedAtDestination)
				.build();
	}

	// -- the two faces: stored, and derived from the rules -------------------

	/** The delivery as stored: one row per cargo, the absent fields as none; replaced whole by every command. */
	External<Delivery> stored(AnswerSource db) {
		return Literal.relation(Delivery.class, "delivery").schema(this).from(db);
	}

	/**
	 * Delivery.derivedFrom as one rule: the delivery row the rules give now, in
	 * the stored relation's columns. The partial fields are reified to their
	 * columns — the value where the rule holds, none where it does not — and
	 * the predicates to booleans; the rules themselves stay partial for each other.
	 */
	public Literal<Delivery> derived(AnswerSource db) {
		return Literal.relation(Delivery.class, "derived").schema(this)
				.solving(known(db, trackingId)
						.and(routingStatus(db, trackingId, routingStatus))
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
		return staged.replacing(row.stored(staged), Query.of(row.derived(staged)));
	}

	/** A solution as the value: absence as empty, the next activity from its three columns. */
	@Override
	public DeliveryDto map(Solution s) {
		return new DeliveryDto(
				s.get(routingStatus).get(),
				s.get(transportStatus).get(),
				s.get(lastKnownLocation).get().toOptional(),
				s.get(currentVoyage).get().toOptional(),
				s.get(misdirected).get(),
				s.get(eta).get().toOptional(),
				s.get(nextType).get().toOptional().map(type -> new HandlingActivityDto(type,
						s.get(nextLocation).get().toOptional().get(),
						s.get(nextVoyage).get().toOptional())),
				s.get(unloadedAtDestination).get());
	}
}
