package org.clauseway.cargo;

// ABOUTME: The derived relations over the shipping facts — dddsample's
// ABOUTME: Delivery, Itinerary and RouteSpecification calculations as rules.

import static org.clauseway.cargo.Schema.handlingEvent;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;

final class Rules {

	private final AnswerSource db;

	Rules(AnswerSource db) {
		this.db = db;
	}

	/** Some handling of the cargo completed after the given time. */
	Literal later(Unifiable<String> trackingId, Unifiable<Instant> time) {
		Unifiable<Instant> completed = lvar();
		return Literal.relation(Rules.class, "later")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(handlingEvent(db, trackingId, lvar(), lvar(), lvar(), completed, lvar())
						.and(Instants.lss(time, completed)));
	}

	/** The most recently completed handling: an event with none later. */
	Literal lastEvent(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<String> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(Rules.class, "lastEvent")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.solving(handlingEvent(db, trackingId, type, location, voyageNumber, completed, lvar())
						.and(exclude(later(trackingId, completed))));
	}
}
