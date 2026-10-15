package org.clauseway.shipping.rules;

// ABOUTME: The voyage schedule read as rules: where a cargo that boards at a
// ABOUTME: port can stay aboard to, through the voyage's consecutive sailings.

import static org.clauseway.shipping.Schema.carrierMovement;
import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;

public final class Voyages {

	private final AnswerSource db;

	public Voyages(AnswerSource db) {
		this.db = db;
	}

	/**
	 * A stretch of one voyage: boarded at a port when it sails, left at a port
	 * the voyage reaches by consecutive sailings — each leaving the port the
	 * previous one landed at, no earlier than it landed.
	 */
	public Literal aboard(Unifiable<String> voyageNumber, Unifiable<String> loadLocation,
			Unifiable<String> unloadLocation, Unifiable<Instant> loadTime, Unifiable<Instant> unloadTime) {
		return Literal.relation(Voyages.class, "aboard")
				.arg("voyageNumber", voyageNumber)
				.arg("loadLocation", loadLocation)
				.arg("unloadLocation", unloadLocation)
				.arg("loadTime", loadTime)
				.arg("unloadTime", unloadTime)
				.solving(carrierMovement(db, voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime)
						.or(defer(() -> {
							Unifiable<String> port = lvar();
							Unifiable<Instant> landed = lvar();
							Unifiable<Instant> sailsOn = lvar();
							return carrierMovement(db, voyageNumber, loadLocation, port, loadTime, landed)
									.and(aboard(voyageNumber, port, unloadLocation, sailsOn, unloadTime))
									.and(Instants.leq(landed, sailsOn));
						})));
	}
}
