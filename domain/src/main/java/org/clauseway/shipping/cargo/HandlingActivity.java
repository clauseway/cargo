package org.clauseway.shipping.cargo;

// ABOUTME: A handling the cargo's itinerary plans, as the rules answer it: one step of the plan, the kind,
// ABOUTME: the port and the voyage, read as a HandlingActivityDTO, probed by the rules that compare handlings to the plan.

import static org.clauseway.logic.unification.terms.LVar.lvar;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.functional.tuples.Tuple;
import org.clauseway.functional.tuples.Tuple2;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.Schema;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.handling.HandlingType;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@With
public final class HandlingActivity implements Schema<Tuple2<Integer, HandlingActivityDto>> {

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
	public Schema.Anonymous describe() {
		return Schema.builder()
				.arg("trackingId", trackingId)
				.arg("step", step)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.build();
	}

	/** A solution as the handling at its step: the step is the plan's, not the activity's, so it rides beside the value. */
	@Override
	public Tuple2<Integer, HandlingActivityDto> map(Row s) {
		return Tuple.of(s.get(step).get(),
				new HandlingActivityDto(s.get(type).get(), s.get(location).get(), s.get(voyageNumber).get().toOptional()));
	}
}
