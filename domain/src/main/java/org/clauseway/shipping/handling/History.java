package org.clauseway.shipping.handling;

// ABOUTME: The HandlingEvent aggregate: the handlings as reported, written here
// ABOUTME: alone by a registration that guards itself; the history read as rules — each event, the last.

import static org.clauseway.logic.goals.Matche.loption;
import static org.clauseway.logic.goals.Matche.matche;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.Command;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Schema;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.location.Locations;
import org.clauseway.shipping.voyage.Voyages;
import org.clauseway.vavr.control.Try;

public final class History {

	private final Transaction db;
	private final Cargo cargo;
	private final Locations locations;
	private final Voyages voyages;

	public History(Transaction db, Cargo cargo, Locations locations, Voyages voyages) {
		this.db = db;
		this.cargo = cargo;
		this.locations = locations;
		this.voyages = voyages;
	}

	/**
	 * A handling event as reported — what happened to the cargo, where, when
	 * it happened and when the report arrived; the port-side kinds have no
	 * voyage. A handling reported twice is two rows; every rule projects the
	 * registration time away, so it is one event.
	 */
	static Literal handlingEvent(AnswerSource db, Unifiable<String> trackingId,
			Unifiable<HandlingType> type, Unifiable<String> location, Unifiable<LOption<String>> voyageNumber,
			Unifiable<Instant> completionTime, Unifiable<Instant> registrationTime) {
		return Literal.relation(History.class, "handlingEvent")
				.arg("trackingId", trackingId).indexed()
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completionTime", completionTime)
				.arg("registrationTime", registrationTime)
				.from(db);
	}

	/** The aggregate on a SQL source: the handling kind by name, the absent voyage as NULL, the times as timestamps. */
	public static SerializableSource onSql(SerializableSource source) {
		return source.withCodec(handlingEvent(null, lvar(), Schema.HANDLING_TYPE.arg(), lvar(), Schema.OPTIONAL_STRING.arg(),
				Schema.INSTANT.arg(), Schema.INSTANT.arg()));
	}

	/** A handling of the cargo as it happened: the kind, the port, the voyage, the completion; a report repeated is one event. */
	public Literal event(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(History.class, "event")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.solving(handlingEvent(db, trackingId, type, location, voyageNumber, completed, lvar()));
	}

	/** The cargo has been handled at all. */
	public Literal anyEvent(Unifiable<String> trackingId) {
		return Literal.relation(History.class, "anyEvent")
				.arg("trackingId", trackingId)
				.solving(handlingEvent(db, trackingId, lvar(), lvar(), lvar(), lvar(), lvar()));
	}

	/** Some handling of the cargo completed after the given time. */
	public Literal later(Unifiable<String> trackingId, Unifiable<Instant> time) {
		Unifiable<Instant> completed = lvar();
		return Literal.relation(History.class, "later")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(handlingEvent(db, trackingId, lvar(), lvar(), lvar(), completed, lvar())
						.and(Instants.lss(time, completed)));
	}

	/** Whether a handling kind names a voyage: the enum's flag, as a relation over its own facts. */
	private static Literal handlingKind(AnswerSource table, Unifiable<HandlingType> type,
			Unifiable<Boolean> voyageRequired) {
		return Literal.relation(History.class, "handlingKind")
				.arg("type", type)
				.arg("voyageRequired", voyageRequired)
				.from(table);
	}

	private static final AnswerStore HANDLING_KINDS = AnswerStore.empty().asserting(
			Arrays.stream(HandlingType.values())
					.map(type -> handlingKind(null, lval(type), lval(type.requiresVoyage())))
					.toArray(Literal[]::new));

	/**
	 * HandlingEventFactory and the HandlingEvent constructors as one
	 * complement: every rule a registration would violate, by name. The
	 * cargo and the port must be known; a kind that requires a voyage names
	 * a known one, the others name none. Registration is allowed when there
	 * are no denials.
	 */
	public Literal registrationDenial(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<String> reason) {
		return Literal.relation(History.class, "registrationDenial")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("reason", reason)
				.solving(exclude(cargo.known(trackingId))
						.and(reason.unifies("unknown cargo"))
						.or(exclude(locations.known(location))
								.and(reason.unifies("unknown location")))
						.or(matche(voyageNumber,
								loption(() -> handlingKind(HANDLING_KINDS, type, lval(true))
										.and(reason.unifies("voyage required"))),
								loption(named -> handlingKind(HANDLING_KINDS, type, lval(false))
										.and(reason.unifies("voyage prohibited"))
										.or(exclude(voyages.known(named))
												.and(reason.unifies("unknown voyage")))))));
	}

	/** The most recently completed handling: an event with none later. */
	public Literal lastEvent(Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(History.class, "lastEvent")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.solving(handlingEvent(db, trackingId, type, location, voyageNumber, completed, lvar())
						.and(exclude(later(trackingId, completed))));
	}

	/** Every rule the registration would violate, by name; empty means allowed. */
	private List<String> registrationDenials(String trackingId, HandlingType type, String location,
			Optional<String> voyageNumber) {
		Unifiable<String> reason = lvar();
		return Query.of(registrationDenial(lval(trackingId), lval(type), lval(location), LOption.of(voyageNumber), reason))
				.solve(reason).map(Reified::get).collect(Collectors.toList());
	}

	/** HandlingEventService.register: the factory's checks, then the fact. */
	public Try<Transaction> register(String trackingId, HandlingType type, String location,
			Optional<String> voyageNumber, Instant completionTime, Instant registrationTime) {
		return Command.guarded(registrationDenials(trackingId, type, location, voyageNumber),
				() -> land(db, trackingId, type, location, LOption.of(voyageNumber), completionTime, registrationTime));
	}

	/** A handling as reported, landed as given: the fact door, unchecked. */
	public static Transaction land(Transaction db, String trackingId, HandlingType type, String location,
			Unifiable<LOption<String>> voyageNumber, Instant completionTime, Instant registrationTime) {
		return db.asserting(handlingEvent(db, lval(trackingId), lval(type), lval(location), voyageNumber,
				lval(completionTime), lval(registrationTime)));
	}
}
