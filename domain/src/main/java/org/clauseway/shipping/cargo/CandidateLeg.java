package org.clauseway.shipping.cargo;

// ABOUTME: One leg of a route the schedule offers a cargo: LegDTO as a term, with no tracking id and no
// ABOUTME: index because the leg is nobody's yet and its order is its place in the route.

import static org.clauseway.shipping.Reads.value;

import java.time.Instant;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.clauseway.logic.unification.structures.Structural;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.shipping.cargo.dto.LegDto;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class CandidateLeg implements Structural<CandidateLeg> {

	final Term<String> voyageNumber;
	final Term<String> loadLocation;
	final Term<String> unloadLocation;
	final Term<Instant> loadTime;
	final Term<Instant> unloadTime;

	@Override
	public Iterable<? extends Term<?>> members() {
		return Arrays.asList(voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime);
	}

	@Override
	@SuppressWarnings("unchecked")
	public CandidateLeg withMembers(Term<?>[] m) {
		return new CandidateLeg((Term<String>) m[0], (Term<String>) m[1], (Term<String>) m[2],
				(Term<Instant>) m[3], (Term<Instant>) m[4]);
	}

	/** A ground leg as the value. */
	public LegDto toDto() {
		return new LegDto(value(voyageNumber), value(loadLocation), value(unloadLocation), value(loadTime), value(unloadTime));
	}
}
