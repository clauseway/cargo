package org.clauseway.shipping.cargo;

// ABOUTME: One leg of the cargo's itinerary as the cargo stores it: the row of the leg relation, built from a
// ABOUTME: LegDTO when the cargo is routed, read back as one, probed by the rules that walk the itinerary.

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
import org.clauseway.pldb.relations.External;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.shipping.cargo.dto.LegDto;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class Leg implements Schema<Leg> {

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
		return fresh(trackingId).probe().withTrackingId(trackingId);
	}

	/** The leg at an index of the cargo's itinerary, as a fact. */
	static Leg of(String trackingId, int index, LegDto leg) {
		return new Leg(lval(trackingId), lval(index), lval(leg.getVoyageNumber()), lval(leg.getLoadLocation()),
				lval(leg.getUnloadLocation()), lval(leg.getLoadTime()), lval(leg.getUnloadTime()));
	}

	@Override
	public Iterable<? extends Term<?>> members() {
		return Arrays.asList(trackingId, index, voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Leg withMembers(Term<?>[] m) {
		return new Leg((Unifiable<String>) m[0], (Unifiable<Integer>) m[1], (Unifiable<String>) m[2],
				(Unifiable<String>) m[3], (Unifiable<String>) m[4], (Unifiable<Instant>) m[5], (Unifiable<Instant>) m[6]);
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

	/** A ground row as the value; the index is the itinerary's, not the leg's. */
	public LegDto toDto() {
		return new LegDto(value(voyageNumber), value(loadLocation), value(unloadLocation), value(loadTime), value(unloadTime));
	}
}
