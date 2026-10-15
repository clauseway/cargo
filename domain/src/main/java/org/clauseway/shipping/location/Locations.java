package org.clauseway.shipping.location;

// ABOUTME: The Location aggregate: the ports by UN/LOCODE, one relation this
// ABOUTME: package alone writes; other aggregates read it through its rules.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.transaction.Transaction;

public final class Locations {

	private final AnswerSource db;

	public Locations(AnswerSource db) {
		this.db = db;
	}

	/** A port, by UN/LOCODE. */
	static Literal location(AnswerSource db, Unifiable<String> unlocode, Unifiable<String> name) {
		return Literal.relation(Locations.class, "location")
				.arg("unlocode", unlocode).indexed()
				.arg("name", name)
				.from(db);
	}

	/** The port is known. */
	public Literal known(Unifiable<String> unlocode) {
		return Literal.relation(Locations.class, "known")
				.arg("unlocode", unlocode)
				.solving(location(db, unlocode, lvar()));
	}

	/** A port declared: the fact lands. */
	public static Transaction declare(Transaction db, String unlocode, String name) {
		return db.asserting(location(db, lval(unlocode), lval(name)));
	}
}
