package org.clauseway.shipping.cargo;

// ABOUTME: The cargo's requirement as the cargo stores it: the row of the routeSpecification relation, built
// ABOUTME: from a RouteSpecificationDTO when the customer specifies, read back as one, probed by the routing rules.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.Reads.value;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.shipping.cargo.dto.RouteSpecificationDto;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class RouteSpecification implements Schema<RouteSpecification> {

	final Unifiable<String> trackingId;
	final Unifiable<String> origin;
	final Unifiable<String> destination;
	final Unifiable<Instant> arrivalDeadline;

	/** The cargo's specification with every column a fresh variable: the row a read selects. */
	public static RouteSpecification fresh(Unifiable<String> trackingId) {
		return new RouteSpecification(trackingId, lvar(), lvar(), lvar());
	}

	/** The cargo specified as given, as a fact. */
	static RouteSpecification of(String trackingId, RouteSpecificationDto spec) {
		return new RouteSpecification(lval(trackingId), lval(spec.getOrigin()), lval(spec.getDestination()),
				lval(spec.getArrivalDeadline()));
	}

	@Override
	public Iterable<? extends Term<?>> members() {
		return Arrays.asList(trackingId, origin, destination, arrivalDeadline);
	}

	@Override
	@SuppressWarnings("unchecked")
	public RouteSpecification withMembers(Term<?>[] m) {
		return new RouteSpecification((Unifiable<String>) m[0], (Unifiable<String>) m[1], (Unifiable<String>) m[2],
				(Unifiable<Instant>) m[3]);
	}

	@Override
	public List<Property<?>> columns() {
		return Arrays.asList(
				Property.of("trackingId").indexed(),
				Property.of("origin"),
				Property.of("destination"),
				Property.of("arrivalDeadline"));
	}

	/** The specification as stored: one row per cargo, replaced whole when the customer re-specifies. */
	Literal<RouteSpecification> stored(AnswerSource db) {
		return Literal.relation(RouteSpecification.class, "routeSpecification").args(this).from(db);
	}

	/** A ground row as the value. */
	public RouteSpecificationDto toDto() {
		return new RouteSpecificationDto(value(origin), value(destination), value(arrivalDeadline));
	}
}
