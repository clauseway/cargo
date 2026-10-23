package org.clauseway.shipping.cargo;

// ABOUTME: A handling the cargo's itinerary plans, as the rules answer it: one step of the plan, the kind,
// ABOUTME: the port and the voyage, read as a HandlingActivityDTO, probed by the rules that compare handlings to the plan.

import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.Reads.value;

import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Row;
import org.clauseway.shipping.handling.HandlingType;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class HandlingActivity implements Row<HandlingActivity> {

	final Unifiable<String> trackingId;
	final Unifiable<Integer> step;
	final Unifiable<HandlingType> type;
	final Unifiable<String> location;
	final Unifiable<LOption<String>> voyageNumber;

	/** The cargo's planned handlings with every column a fresh variable: the row a read selects. */
	public static HandlingActivity fresh(Unifiable<String> trackingId) {
		return new HandlingActivity(trackingId, lvar(), lvar(), lvar(), lvar());
	}

	@Override
	public Iterable<? extends Term<?>> members() {
		return Arrays.asList(trackingId, step, type, location, voyageNumber);
	}

	@Override
	@SuppressWarnings("unchecked")
	public HandlingActivity withMembers(Term<?>[] m) {
		return new HandlingActivity((Unifiable<String>) m[0], (Unifiable<Integer>) m[1], (Unifiable<HandlingType>) m[2],
				(Unifiable<String>) m[3], (Unifiable<LOption<String>>) m[4]);
	}

	@Override
	public List<Property<?>> columns() {
		return Arrays.asList(
				Property.of("trackingId"),
				Property.of("step"),
				Property.of("type"),
				Property.of("location"),
				Property.of("voyageNumber"));
	}

	/** A ground row as the value; the step is the plan's, not the activity's. */
	public HandlingActivityDTO toDto() {
		return new HandlingActivityDTO(value(type), value(location), value(voyageNumber).toOptional());
	}
}
