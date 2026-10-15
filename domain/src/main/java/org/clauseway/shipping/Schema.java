package org.clauseway.shipping;

// ABOUTME: The shipping schema on a SQL source: the relations live with their
// ABOUTME: aggregates, and their encodings are composed here, instants as timestamps.

import java.sql.Timestamp;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.clauseway.pldb.sql.Codec;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.handling.History;
import org.clauseway.shipping.voyage.Voyages;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Schema {

	/** An instant as a SQL timestamp. */
	public static final Codec<Instant> INSTANT = Codec.of(Instant.class, Timestamp.class, Timestamp::from, Timestamp::toInstant);

	/** The schema on a SQL source: every aggregate's encoding. */
	public static SerializableSource onSql(SerializableSource source) {
		return Cargo.onSql(History.onSql(Voyages.onSql(source)));
	}
}
