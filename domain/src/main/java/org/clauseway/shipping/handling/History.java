package org.clauseway.shipping.handling;

// ABOUTME: The HandlingEvent aggregate: the handlings as reported, written here
// ABOUTME: alone by a registration that guards itself; the history read as rules — each event, the last.

import static org.clauseway.logic.goals.Matche.loption;
import static org.clauseway.logic.goals.Matche.matche;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.structures.LOption.none;
import static org.clauseway.logic.unification.structures.LOption.some;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.clauseway.logic.finitedomain.Instants;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.AnswerSource;
import org.clauseway.pldb.inmemory.AnswerStore;
import org.clauseway.pldb.relations.Literal;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.Command;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.location.Locations;
import org.clauseway.shipping.voyage.Voyages;
import org.clauseway.vavr.control.Try;

public final class History {

	private final Transaction db;

	public History(Transaction db) {
		this.db = db;
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

	/** A handling of the cargo as it happened: the kind, the port, the voyage, the completion; a report repeated is one event. */
	public static Literal event(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(History.class, "event")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.solving(handlingEvent(db, trackingId, type, location, voyageNumber, completed, projected()));
	}

	/** The cargo has been handled at all. */
	public static Literal anyEvent(AnswerSource db, Unifiable<String> trackingId) {
		return Literal.relation(History.class, "anyEvent")
				.arg("trackingId", trackingId)
				.solving(handlingEvent(db, trackingId, projected(), projected(), projected(), projected(), projected()));
	}

	/** Some handling of the cargo completed after the given time. */
	public static Literal later(AnswerSource db, Unifiable<String> trackingId, Unifiable<Instant> time) {
		Unifiable<Instant> completed = lvar();
		return Literal.relation(History.class, "later")
				.arg("trackingId", trackingId)
				.arg("time", time)
				.solving(handlingEvent(db, trackingId, projected(), projected(), projected(), completed, projected())
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
	public static Literal registrationDenial(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<String> reason) {
		return Literal.relation(History.class, "registrationDenial")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("reason", reason)
				.solving(exclude(Cargo.known(db, trackingId))
						.and(reason.unifies("unknown cargo"))
						.or(exclude(Locations.known(db, location))
								.and(reason.unifies("unknown location")))
						.or(matche(voyageNumber,
								loption(() -> handlingKind(HANDLING_KINDS, type, lval(true))
										.and(reason.unifies("voyage required"))),
								loption(named -> handlingKind(HANDLING_KINDS, type, lval(false))
										.and(reason.unifies("voyage prohibited"))
										.or(exclude(Voyages.known(db, named))
												.and(reason.unifies("unknown voyage")))))));
	}

	/** The most recently completed handling: an event with none later. */
	public static Literal lastEvent(AnswerSource db, Unifiable<String> trackingId, Unifiable<HandlingType> type,
			Unifiable<String> location, Unifiable<LOption<String>> voyageNumber, Unifiable<Instant> completed) {
		return Literal.relation(History.class, "lastEvent")
				.arg("trackingId", trackingId)
				.arg("type", type)
				.arg("location", location)
				.arg("voyageNumber", voyageNumber)
				.arg("completed", completed)
				.solving(handlingEvent(db, trackingId, type, location, voyageNumber, completed, projected())
						.and(exclude(later(db, trackingId, completed))));
	}

	/** Every rule the registration would violate, by name; empty means allowed. */
	private List<String> registrationDenials(String trackingId, HandlingType type, String location,
			Unifiable<LOption<String>> voyageNumber) {
		Unifiable<String> reason = lvar();
		return Query.of(registrationDenial(db, lval(trackingId), lval(type), lval(location), voyageNumber, reason))
				.solve(reason).map(Reified::get).collect(Collectors.toList());
	}

	/** HandlingEventService.register for a carrier-side handling, aboard the voyage: the factory's checks, then the fact. */
	public Try<Transaction> register(String trackingId, HandlingType type, String location,
			String voyageNumber, Instant completionTime, Instant registrationTime) {
		return register(trackingId, type, location, some(lval(voyageNumber)), completionTime, registrationTime);
	}

	/** HandlingEventService.register for a port-side handling, with no voyage: the factory's checks, then the fact. */
	public Try<Transaction> register(String trackingId, HandlingType type, String location,
			Instant completionTime, Instant registrationTime) {
		return register(trackingId, type, location, none(), completionTime, registrationTime);
	}

	private Try<Transaction> register(String trackingId, HandlingType type, String location,
			Unifiable<LOption<String>> voyageNumber, Instant completionTime, Instant registrationTime) {
		return Command.guarded(registrationDenials(trackingId, type, location, voyageNumber),
				() -> land(db, trackingId, type, location, voyageNumber, completionTime, registrationTime));
	}

	/** A handling as reported, landed as given: the fact door, unchecked. */
	public static Transaction land(Transaction db, String trackingId, HandlingType type, String location,
			Unifiable<LOption<String>> voyageNumber, Instant completionTime, Instant registrationTime) {
		return db.asserting(handlingEvent(db, lval(trackingId), lval(type), lval(location), voyageNumber,
				lval(completionTime), lval(registrationTime)));
	}
}
