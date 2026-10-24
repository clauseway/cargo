package org.clauseway.shipping;

// ABOUTME: A goal's answers as Java values: the first one, whether there is
// ABOUTME: any, every one of them.

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.literals.Literal;

import static org.clauseway.logic.unification.terms.LVal.lval;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Reads {

	/** The first answer for one variable of a goal, if any. */
	public static <T> Optional<T> first(Literal<?> goal, Unifiable<T> out) {
		return Query.of(goal).solve(out).findFirst().map(Reified::get);
	}

	/** Whether a ground goal has an answer at all. */
	public static boolean holds(Literal<?> goal) {
		return Query.of(goal).solve(lval(true)).findAny().isPresent();
	}

	/** Every answer for one variable of a goal. */
	public static <T> List<T> values(Literal<?> goal, Unifiable<T> out) {
		return Query.of(goal).solve(out).map(Reified::get).collect(Collectors.toList());
	}

	/** A ground column's value; a row that was not selected has none. */
	public static <T> T value(Term<T> column) {
		return column.asVal().orElseThrow(() -> new IllegalStateException("not a ground row: " + column));
	}
}
