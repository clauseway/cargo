# Friction ledger

The point of this project: translate the canonical DDD sample — Eric Evans'
cargo shipping domain, as implemented in `citerus/dddsample-core` (MIT) — into
relations on the engine, keeping its names and rules, and record every place the
translation is clumsy, forces a workaround, or wants something the engine does
not have. The library app bent its own domain toward the engine; this one
cannot, because the domain is someone else's. One entry per friction, appended
as hit, never rewritten — this file is evidence, not a doc.

Entry format: what dddsample says, what I had to say instead, what the engine
would need.

## Predictions, written before the first rule

Graded as the entries come in; a prediction that never turns into an entry is
as informative as one that does.

- P1. **Ordered sequences.** An itinerary is an ordered list of legs; "first",
  "last", "the leg after this one" become an index column plus negation
  ("no leg at index+1"). Workable, probably clumsy.
- P2. **Derived-state change as an event.** dddsample raises "cargo was
  misdirected" when the derived flag flips. With state derived at read there is
  no flip to observe; the application layer has to solve before and after.
- P3. **Optional columns.** RECEIVE/CLAIM/CUSTOMS events have no voyage; ETA and
  next activity are sometimes absent. dddsample uses null objects and nulls.
- P4. **Route finding under time** — legs chaining with arrival before the next
  departure and finishing before the deadline — is tabling and `Instants`
  comparisons together; the likeliest place for an engine surprise.
- P5. **The certified write.** Assigning a route races a destination change:
  both read the spec, both commit, the cargo is silently misrouted. dddsample
  locks the Cargo aggregate; the footprint should refuse the second commit.

## Translation choices (not frictions — decisions, recorded once)

- A value object that dddsample *replaces* (a new route specification, a new
  itinerary) is retract-old + assert-new: the literal reading of "discard the
  old value and attach a new one".
- The three voyage-less handling types carry dddsample's own null object,
  `Voyage.NONE` with the empty voyage number, rather than a nullable column.
  P3 stays open for the derived side.
- dddsample dedups handling events by value (`distinctEventsByCompletionTime`
  through a `HashSet`). Facts are values here, so that dedup is free — once
  the event's identity and its registration are separate facts (entry 1).

## 1. An entity's identity is a subset of its columns

dddsample's `HandlingEvent.sameEventAs` compares cargo, voyage, completion
time, location and type — and NOT `registrationTime`; `HandlingHistoryTest`
registers one event twice at different times and expects one event. Had I
kept `registrationTime` on `handlingEvent`, the two registrations would be two
facts and "last event" would have two answers at the same completion time. Had
to: split the row along the identity line — `handlingEvent(…identity…)` and
`registration(…identity…, registrationTime)` — so re-registering states the
same event fact and one more registration fact. That is the relational-normal
answer and arguably the better model (dddsample stores the duplicate and
dedups on read), but it is a decision the engine forces at schema time where
an OO model makes it in `equals`. What the engine would need: nothing — a
relation with a declared key would let the one row keep its attributes, but
that is a schema feature, not a gap.

## Non-event: an FD comparison under negation

`lastEvent` is `handlingEvent(c, …, t) ∧ ¬later(c, t)` with `later` a named
projection over `Instants.lss(t, t′)`. First rule, first run, correct — the
nogood store completes the inner extension with the comparison inside it and
nothing needed saying twice. Noted because it was the prediction most likely
to bite first.
