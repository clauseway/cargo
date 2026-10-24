package org.clauseway.shipping.location;

// ABOUTME: The Location aggregate: the ports by UN/LOCODE, one relation this
// ABOUTME: package alone writes; other aggregates read it through its rules.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.pldb.relations.Projected.projected;

import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.External;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.pldb.transaction.Transaction;

public final class Locations {

	/** A port, by UN/LOCODE. */
	static External<?> location(AnswerSource db, Unifiable<String> unlocode, Unifiable<String> name) {
		return Literal.relation(Locations.class, "location")
				.args(Schema.builder()
						.arg("unlocode", unlocode).indexed()
						.arg("name", name)
						.build())
				.from(db);
	}

	/** The port is known. */
	public static Literal<?> known(AnswerSource db, Unifiable<String> unlocode) {
		return Literal.relation(Locations.class, "known")
				.args(Schema.builder()
						.arg("unlocode", unlocode)
						.build())
				.solving(location(db, unlocode, projected()));
	}

	/** A port declared: the fact lands. */
	public static Transaction declare(Transaction db, String unlocode, String name) {
		return db.asserting(location(db, lval(unlocode), lval(name)));
	}
}
