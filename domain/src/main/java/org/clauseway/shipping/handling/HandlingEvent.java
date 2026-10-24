package org.clauseway.shipping.handling;

// ABOUTME: A handling of the cargo as it happened, as the history's rules answer it: the kind, the port,
// ABOUTME: the voyage and the completion, read as a HandlingEventDTO.

import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.solving.Solution;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.Property;
import org.clauseway.pldb.relations.Schema;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class HandlingEvent implements Schema<HandlingEventDto> {

	final Unifiable<String> trackingId;
	final Unifiable<HandlingType> type;
	final Unifiable<String> location;
	final Unifiable<LOption<String>> voyageNumber;
	final Unifiable<Instant> completed;

	/** The cargo's handlings with every column a fresh variable: the row a read selects. */
	public static HandlingEvent fresh(Unifiable<String> trackingId) {
		return new HandlingEvent(trackingId, lvar(), lvar(), lvar(), lvar());
	}

	@Override
	public Iterable<? extends Unifiable<?>> keys() {
		return Arrays.asList(trackingId, type, location, voyageNumber, completed);
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

	/** A solution as the value. */
	@Override
	public HandlingEventDto map(Solution s) {
		return new HandlingEventDto(s.get(type).get(), s.get(location).get(), s.get(voyageNumber).get().toOptional(),
				s.get(completed).get());
	}
}
