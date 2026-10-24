package org.clauseway.shipping.cargo;

// ABOUTME: One leg of the cargo's itinerary as the cargo stores it: the row of the leg relation, built from a
// ABOUTME: LegDTO when the cargo is routed, read back as one, probed by the rules that walk the itinerary.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.logic.solving.Solution;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.relations.literals.External;
import org.clauseway.pldb.relations.literals.Literal;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.shipping.cargo.dto.LegDto;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class Leg implements Schema<Tuple2<Integer, LegDto>> {

	final Unifiable<String> trackingId;
	final Unifiable<Integer> index;
	final Unifiable<String> voyageNumber;
	final Unifiable<String> loadLocation;
	final Unifiable<String> unloadLocation;
	final Unifiable<Instant> loadTime;
	final Unifiable<Instant> unloadTime;

	/** The cargo's legs with every column a fresh variable: the row a read selects. */
	public static Leg fresh(Unifiable<String> trackingId) {
		return new Leg(trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar());
	}

	/** The cargo's legs with every column projected away: the row a rule probes, naming only the columns it cares about. */
	static Leg probe(Unifiable<String> trackingId) {
		return new Leg(trackingId, projected(), projected(), projected(), projected(), projected(), projected());
	}

	/** The leg at an index of the cargo's itinerary, as a fact. */
	static Leg of(String trackingId, int index, LegDto leg) {
		return new Leg(lval(trackingId), lval(index), lval(leg.getVoyageNumber()), lval(leg.getLoadLocation()),
				lval(leg.getUnloadLocation()), lval(leg.getLoadTime()), lval(leg.getUnloadTime()));
	}

	@Override
	public Iterable<? extends Unifiable<?>> keys() {
		return Arrays.asList(trackingId, index, voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime);
	}

	@Override
	public List<Property<?>> columns() {
		return Arrays.asList(
				Property.of("trackingId").indexed(),
				Property.of("index"),
				Property.of("voyageNumber"),
				Property.of("loadLocation"),
				Property.of("unloadLocation"),
				Property.of("loadTime"),
				Property.of("unloadTime"));
	}

	/** The legs as stored; the index is the itinerary's order. */
	External<Leg> stored(AnswerSource db) {
		return Literal.relation(Leg.class, "leg").args(this).from(db);
	}

	/** A solution as the leg at its index: the index is the itinerary's, not the leg's, so it rides beside the value. */
	@Override
	public Tuple2<Integer, LegDto> map(Solution s) {
		return Tuple.of(s.get(index).get(), new LegDto(s.get(voyageNumber).get(),
				s.get(loadLocation).get(), s.get(unloadLocation).get(), s.get(loadTime).get(), s.get(unloadTime).get()));
	}
}
