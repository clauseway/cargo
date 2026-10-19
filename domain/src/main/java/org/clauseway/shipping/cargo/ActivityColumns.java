package org.clauseway.shipping.cargo;

// ABOUTME: The variables of one handling activity's columns — kind, port, voyage —
// ABOUTME: and the row-to-value reading over them.

import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.util.stream.Stream;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.shipping.HandlingActivity;
import org.clauseway.shipping.HandlingType;

final class ActivityColumns {
	final Unifiable<HandlingType> type = lvar();
	final Unifiable<String> location = lvar();
	final Unifiable<LOption<String>> voyageNumber = lvar();

	Stream<Row> select(Literal activities) {
		return Query.of(activities).select(type, location, voyageNumber).rows();
	}

	Stream<HandlingActivity> activities(Stream<Row> rows) {
		return rows.map(row -> new HandlingActivity(row.get(type).get(), row.get(location).get(),
				row.get(voyageNumber).get().toOptional()));
	}
}
