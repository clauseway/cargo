package org.clauseway.shipping.sql;

// ABOUTME: The shipping schema on a SQL source: every stored relation's columns that need an encoding,
// ABOUTME: bound by name — kinds and statuses as text, instants as timestamps, absence as NULL.

import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.sql.Codec;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.RoutingStatus;
import org.clauseway.shipping.TransportStatus;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.handling.History;
import org.clauseway.shipping.voyage.Voyages;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Schema {

	/** An instant as a SQL timestamp. */
	private static final Codec<Instant> INSTANT = Codec.of(Instant.class, Timestamp.class, Timestamp::from, Timestamp::toInstant);
	private static final Codec<LOption<Instant>> OPTIONAL_INSTANT = INSTANT.optional();
	/** A text column that may be NULL. */
	private static final Codec<LOption<String>> OPTIONAL_STRING = Codec.optional(String.class);
	private static final Codec<HandlingType> HANDLING_TYPE = enumeration(HandlingType.class);
	private static final Codec<LOption<HandlingType>> OPTIONAL_HANDLING_TYPE = HANDLING_TYPE.optional();
	private static final Codec<RoutingStatus> ROUTING_STATUS = enumeration(RoutingStatus.class);
	private static final Codec<TransportStatus> TRANSPORT_STATUS = enumeration(TransportStatus.class);

	/** An enum by its constant's name. */
	private static <E extends Enum<E>> Codec<E> enumeration(Class<E> type) {
		return Codec.of(type, String.class, Enum::name, name -> Enum.valueOf(type, name));
	}

	/** The schema on a SQL source: each relation spelled as its aggregate declares it, with the codec at every column that needs one. */
	public static SerializableSource onSql(SerializableSource source) {
		return source
				.withCodec(Literal.relation(Voyages.class, "carrierMovement")
						.arg("voyageNumber", lvar())
						.arg("departureLocation", lvar())
						.arg("arrivalLocation", lvar())
						.arg("departureTime", INSTANT.arg())
						.arg("arrivalTime", INSTANT.arg())
						.from(null))
				.withCodec(Literal.relation(Cargo.class, "routeSpecification")
						.arg("trackingId", lvar())
						.arg("origin", lvar())
						.arg("destination", lvar())
						.arg("arrivalDeadline", INSTANT.arg())
						.from(null))
				.withCodec(Literal.relation(Cargo.class, "leg")
						.arg("trackingId", lvar())
						.arg("index", lvar())
						.arg("voyageNumber", lvar())
						.arg("loadLocation", lvar())
						.arg("unloadLocation", lvar())
						.arg("loadTime", INSTANT.arg())
						.arg("unloadTime", INSTANT.arg())
						.from(null))
				.withCodec(Literal.relation(Cargo.class, "lastHandling")
						.arg("trackingId", lvar())
						.arg("type", HANDLING_TYPE.arg())
						.arg("location", lvar())
						.arg("voyageNumber", OPTIONAL_STRING.arg())
						.arg("completed", INSTANT.arg())
						.from(null))
				.withCodec(Literal.relation(Cargo.class, "delivery")
						.arg("trackingId", lvar())
						.arg("routingStatus", ROUTING_STATUS.arg())
						.arg("transportStatus", TRANSPORT_STATUS.arg())
						.arg("lastKnownLocation", OPTIONAL_STRING.arg())
						.arg("currentVoyage", OPTIONAL_STRING.arg())
						.arg("misdirected", lvar())
						.arg("eta", OPTIONAL_INSTANT.arg())
						.arg("nextType", OPTIONAL_HANDLING_TYPE.arg())
						.arg("nextLocation", OPTIONAL_STRING.arg())
						.arg("nextVoyage", OPTIONAL_STRING.arg())
						.arg("unloadedAtDestination", lvar())
						.from(null))
				.withCodec(Literal.relation(History.class, "handlingEvent")
						.arg("trackingId", lvar())
						.arg("type", HANDLING_TYPE.arg())
						.arg("location", lvar())
						.arg("voyageNumber", OPTIONAL_STRING.arg())
						.arg("completionTime", INSTANT.arg())
						.arg("registrationTime", INSTANT.arg())
						.from(null));
	}
}
