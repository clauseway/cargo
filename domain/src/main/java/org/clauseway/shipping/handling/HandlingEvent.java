package org.clauseway.shipping.handling;

// ABOUTME: A handling of the cargo as it happened, as the history's rules answer it: the kind, the port,
// ABOUTME: the voyage and the completion, read as a HandlingEventDTO.

import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.Reads.value;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Schema;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class HandlingEvent implements Schema<HandlingEvent> {

	final Term<String> trackingId;
	final Term<HandlingType> type;
	final Term<String> location;
	final Term<LOption<String>> voyageNumber;
	final Term<Instant> completed;

	/** The cargo's handlings with every column a fresh variable: the row a read selects. */
	public static HandlingEvent fresh(Unifiable<String> trackingId) {
		return new HandlingEvent(trackingId, lvar(), lvar(), lvar(), lvar());
	}

	@Override
	public Iterable<? extends Term<?>> members() {
		return Arrays.asList(trackingId, type, location, voyageNumber, completed);
	}

	@Override
	@SuppressWarnings("unchecked")
	public HandlingEvent withMembers(Term<?>[] m) {
		return new HandlingEvent((Term<String>) m[0], (Term<HandlingType>) m[1], (Term<String>) m[2],
				(Term<LOption<String>>) m[3], (Term<Instant>) m[4]);
	}

	@Override
	public List<Property<?>> columns() {
		return Arrays.asList(
				Property.of("trackingId"),
				Property.of("type"),
				Property.of("location"),
				Property.of("voyageNumber"),
				Property.of("completed"));
	}

	/** A ground row as the value. */
	public HandlingEventDto toDto() {
		return new HandlingEventDto(value(type), value(location), value(voyageNumber).toOptional(), value(completed));
	}
}
