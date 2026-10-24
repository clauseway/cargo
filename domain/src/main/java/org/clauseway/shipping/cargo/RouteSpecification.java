package org.clauseway.shipping.cargo;

// ABOUTME: The cargo's requirement as the cargo stores it: the row of the routeSpecification relation, built
// ABOUTME: from a RouteSpecificationDTO when the customer specifies, read back as one, probed by the routing rules.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.solving.Solution;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.literals.External;
import org.clauseway.pldb.relations.literals.Literal;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.shipping.cargo.dto.RouteSpecificationDto;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class RouteSpecification implements Schema<RouteSpecificationDto> {

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
	public Iterable<? extends Unifiable<?>> keys() {
		return Arrays.asList(trackingId, origin, destination, arrivalDeadline);
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
	External<RouteSpecification> stored(AnswerSource db) {
		return Literal.relation(RouteSpecification.class, "routeSpecification").args(this).from(db);
	}

	/** A solution as the value. */
	@Override
	public RouteSpecificationDto map(Solution s) {
		return new RouteSpecificationDto(s.get(origin).get(), s.get(destination).get(), s.get(arrivalDeadline).get());
	}
}
