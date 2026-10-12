package org.clauseway.cargo.rules;

// ABOUTME: The handling history read as rules: whether the cargo was handled
// ABOUTME: at all, and which handling was the most recently completed.

import static org.clauseway.cargo.Schema.handlingEvent;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import org.clauseway.cargo.HandlingType;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
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
