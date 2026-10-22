package org.clauseway.shipping.cargo;

// ABOUTME: One row of the delivery as variables, the key and the ten columns: the head both faces of the
// ABOUTME: relation are built from, the stored and the derived; selected and read into the value.

import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.shipping.DeliveryDTO;
import org.clauseway.shipping.HandlingActivity;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.TransportStatus;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@With
public final class DeliveryRow {

	final Unifiable<String> trackingId;
	final Unifiable<RoutingStatus> routingStatus;
	final Unifiable<TransportStatus> transportStatus;
	final Unifiable<LOption<String>> lastKnownLocation;
	final Unifiable<LOption<String>> currentVoyage;
	final Unifiable<Boolean> misdirected;
	final Unifiable<LOption<Instant>> eta;
	final Unifiable<LOption<HandlingType>> nextType;
	final Unifiable<LOption<String>> nextLocation;
	final Unifiable<LOption<String>> nextVoyage;
	final Unifiable<Boolean> unloadedAtDestination;

	/** The cargo's row with every column a fresh variable: the shape a question and its template share. */
	public static DeliveryRow fresh(Unifiable<String> trackingId) {
		return new DeliveryRow(trackingId, lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar(), lvar());
	}

	/** The cargo's row with every column projected away: the shape a rule probes, naming only the columns it cares about. */
	public static DeliveryRow probe(Unifiable<String> trackingId) {
		return new DeliveryRow(trackingId, projected(), projected(), projected(), projected(), projected(), projected(),
				projected(), projected(), projected(), projected());
	}

	/** The row's columns applied to a relation, by name, the key indexed: the one spelling both faces share. */
	Literal.Builder columns(Literal.Builder relation) {
		return relation
				.arg("trackingId", trackingId).indexed()
				.arg("routingStatus", routingStatus)
				.arg("transportStatus", transportStatus)
				.arg("lastKnownLocation", lastKnownLocation)
				.arg("currentVoyage", currentVoyage)
				.arg("misdirected", misdirected)
				.arg("eta", eta)
				.arg("nextType", nextType)
				.arg("nextLocation", nextLocation)
				.arg("nextVoyage", nextVoyage)
				.arg("unloadedAtDestination", unloadedAtDestination);
	}

	/** The rows a literal over this shape answers. */
	public Stream<Row> select(Literal delivery) {
		return Query.of(delivery).select(routingStatus, transportStatus, lastKnownLocation, currentVoyage, misdirected, eta,
				nextType, nextLocation, nextVoyage, unloadedAtDestination).rows();
	}

	/** One answered row as the value: absence as empty, the next activity from its three columns. */
	public DeliveryDTO value(Row row) {
		return new DeliveryDTO(
				row.get(routingStatus).get(),
				row.get(transportStatus).get(),
				row.get(lastKnownLocation).get().toOptional(),
				row.get(currentVoyage).get().toOptional(),
				row.get(misdirected).get(),
				row.get(eta).get().toOptional(),
				row.get(nextType).get().toOptional().map(type -> new HandlingActivity(type,
						row.get(nextLocation).get().toOptional().get(),
						row.get(nextVoyage).get().toOptional())),
				row.get(unloadedAtDestination).get());
	}
}
