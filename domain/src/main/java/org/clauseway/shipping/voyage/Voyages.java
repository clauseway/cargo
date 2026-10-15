package org.clauseway.shipping.voyage;

// ABOUTME: The Voyage aggregate: the voyages and their sailings, written here
// ABOUTME: alone; read as rules — a voyage known, a stretch aboard one.

import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.Schema;

public final class Voyages {

	private final AnswerSource db;

	public Voyages(AnswerSource db) {
		this.db = db;
	}

	/** A voyage, by number; its schedule is the carrier movements naming it. */
	static Literal voyage(AnswerSource db, Unifiable<String> voyageNumber) {
		return Literal.relation(Voyages.class, "voyage")
				.arg("voyageNumber", voyageNumber).indexed()
				.from(db);
	}

	/** One sailing of a voyage: from a port at a time, to a port at a time. */
	static Literal carrierMovement(AnswerSource db, Unifiable<String> voyageNumber,
			Unifiable<String> departureLocation, Unifiable<String> arrivalLocation,
			Unifiable<Instant> departureTime, Unifiable<Instant> arrivalTime) {
		return Literal.relation(Voyages.class, "carrierMovement")
				.arg("voyageNumber", voyageNumber).indexed()
				.arg("departureLocation", departureLocation).indexed()
				.arg("arrivalLocation", arrivalLocation).indexed()
				.arg("departureTime", departureTime)
				.arg("arrivalTime", arrivalTime)
				.from(db);
	}

	/** The aggregate on a SQL source: a sailing's times as timestamps. */
	public static SerializableSource onSql(SerializableSource source) {
		return source.withCodec(carrierMovement(null, lvar(), lvar(), lvar(), Schema.INSTANT.arg(), Schema.INSTANT.arg()));
	}

	/** The voyage is known. */
	public Literal known(Unifiable<String> voyageNumber) {
		return Literal.relation(Voyages.class, "known")
				.arg("voyageNumber", voyageNumber)
				.solving(voyage(db, voyageNumber));
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

	/** A voyage declared: the fact lands. */
	public static Transaction declare(Transaction db, String voyageNumber) {
		return db.asserting(voyage(db, lval(voyageNumber)));
	}

	/** A sailing scheduled on a voyage: the fact lands. */
	public static Transaction schedule(Transaction db, String voyageNumber, String departureLocation,
			String arrivalLocation, Instant departureTime, Instant arrivalTime) {
		return db.asserting(carrierMovement(db, lval(voyageNumber), lval(departureLocation),
				lval(arrivalLocation), lval(departureTime), lval(arrivalTime)));
	}
}
