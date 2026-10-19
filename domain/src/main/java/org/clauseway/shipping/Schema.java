package org.clauseway.shipping;

// ABOUTME: The shipping schema on a SQL source: the relations live with their
// ABOUTME: aggregates, and their encodings are composed here, instants as timestamps.

import java.sql.Timestamp;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.pldb.sql.Codec;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.handling.History;
import org.clauseway.shipping.voyage.Voyages;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Schema {

	/** An instant as a SQL timestamp. */
	public static final Codec<Instant> INSTANT = Codec.of(Instant.class, Timestamp.class, Timestamp::from, Timestamp::toInstant);
	public static final Codec<LOption<Instant>> OPTIONAL_INSTANT = INSTANT.optional();
	/** A text column that may be NULL. */
	public static final Codec<LOption<String>> OPTIONAL_STRING = Codec.optional(String.class);
	public static final Codec<HandlingType> HANDLING_TYPE = enumeration(HandlingType.class);
	public static final Codec<LOption<HandlingType>> OPTIONAL_HANDLING_TYPE = HANDLING_TYPE.optional();
	public static final Codec<RoutingStatus> ROUTING_STATUS = enumeration(RoutingStatus.class);
	public static final Codec<TransportStatus> TRANSPORT_STATUS = enumeration(TransportStatus.class);

	/** An enum by its constant's name. */
	private static <E extends Enum<E>> Codec<E> enumeration(Class<E> type) {
		return Codec.of(type, String.class, Enum::name, name -> Enum.valueOf(type, name));
	}

	/** The schema on a SQL source: every aggregate's encoding. */
	public static SerializableSource onSql(SerializableSource source) {
		return Cargo.onSql(History.onSql(Voyages.onSql(source)));
	}
}
