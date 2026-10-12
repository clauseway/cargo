package org.clauseway.cargo.rules;

// ABOUTME: The handling history read as rules: whether the cargo was handled
// ABOUTME: at all, and which handling was the most recently completed.

import static org.clauseway.cargo.Schema.NO_VOYAGE;
import static org.clauseway.cargo.Schema.cargo;
import static org.clauseway.cargo.Schema.handlingEvent;
import static org.clauseway.cargo.Schema.voyage;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import java.util.Arrays;
import org.clauseway.cargo.HandlingType;
import org.clauseway.cargo.Schema;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
import org.clauseway.pldb.relations.Literal;

public final class History {

	private final AnswerSource db;

	public History(AnswerSource db) {
		this.db = db;
	}

	/** The cargo has been handled at all. */
	public Literal anyEvent(Unifiable<String> trackingId) {
		return Literal.relation(History.class, "anyEvent")
				.arg("trackingId", trackingId)
				.solving(handlingEvent(db, trackingId, lvar(), lvar(), lvar(), lvar(), lvar()));
	}

	/** Some handling of the cargo completed after the given time. */
	public Literal later(Unifiable<String> trackingId, Unifiable<Instant> time) {
		Unifiable<Instant> completed = lvar();
		return Literal.relation(History.class, "later")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(handlingEvent(db, trackingId, lvar(), lvar(), lvar(), completed, lvar())
						.and(Instants.lss(time, completed)));
	}

	/** Whether a handling kind names a voyage: the enum's flag, as a relation over its own facts. */
	private static Literal handlingKind(AnswerSource table, Unifiable<HandlingType> type,
			Unifiable<Boolean> voyageRequired) {
		return Literal.relation(History.class, "handlingKind")
				.arg("type", type)
				.arg("voyageRequired", voyageRequired)
				.from(table);
	}

	private static final AnswerStore HANDLING_KINDS = AnswerStore.empty().asserting(
			Arrays.stream(HandlingType.values())
					.map(type -> handlingKind(null, lval(type), lval(type.requiresVoyage())))
					.toArray(Literal[]::new));

	/**
	 * HandlingEventFactory and the HandlingEvent constructors as one
	 * complement: every rule a registration would violate, by name. The
	 * cargo, the port and the voyage must be known; a kind that requires a
	 * voyage names one, the others name none. Registration is allowed when
	 * there are no denials.
	 */
	public Literal registrationDenial(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber, Unifiable<String> reason) {
		return Literal.relation(History.class, "registrationDenial")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("reason", reason)
				.solving(exclude(cargo(db, trackingId, projected()))
						.and(reason.unifies("unknown cargo"))
						.or(exclude(Schema.location(db, location, projected()))
								.and(reason.unifies("unknown location")))
						.or(exclude(voyageNumber.unifies(NO_VOYAGE))
								.and(exclude(voyage(db, voyageNumber)))
								.and(reason.unifies("unknown voyage")))
						.or(handlingKind(HANDLING_KINDS, type, lval(true))
								.and(voyageNumber.unifies(NO_VOYAGE))
								.and(reason.unifies("voyage required")))
						.or(handlingKind(HANDLING_KINDS, type, lval(false))
								.and(exclude(voyageNumber.unifies(NO_VOYAGE)))
								.and(reason.unifies("voyage prohibited"))));
	}

	/** The most recently completed handling: an event with none later. */
	public Literal lastEvent(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(History.class, "lastEvent")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.solving(handlingEvent(db, trackingId, type, location, voyageNumber, completed, lvar())
						.and(exclude(later(trackingId, completed))));
	}
}
