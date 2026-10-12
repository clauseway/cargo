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
  through a `HashSet`). Facts are values here, so that dedup is free (entry 1).

## 1. Identity is the rule head's projection

dddsample's `HandlingEvent` carries two clocks — `completionTime` (when the
handling happened) and `registrationTime` (when the report of it arrived; the
reports come late and sometimes twice from terminals' logging systems) — and
`sameEventAs` deliberately ignores the second, so `HandlingHistoryTest`
registers one handling twice and expects one event. My first reading: the
engine would see two rows and "last event" would answer twice; I split the
row into `handlingEvent(identity)` + `registration(identity, time)`. Wrong, as
Tom pointed out: `lastEvent`'s head names five columns and not the sixth, so
both rows derive the SAME answer and a derived relation's answers are a set.
The split was unnecessary; one row with both clocks, and every rule that
leaves `registrationTime` out of its head gets dddsample's `equals` for free.
What dddsample does in `equals` + `HashSet` is, relationally, projection —
a thing every rule does anyway. What the engine would need: nothing.

## P1 graded: ordered sequences — milder than predicted

The itinerary's order is an `index` column. "First" is index 0; "last" is a
leg with no leg at index+1 — `Ints.addo(index, 1, next)` then
`exclude(leg(c, next, …))`, with `projected()` on the columns the negation
does not care about. Three lines, correct first run, and `expected`
(`Itinerary.isExpected`) reads as five disjuncts naming the five handling
kinds, one per branch of the original `if` chain. The one concession is that
the itinerary is written with an index the OO list carried implicitly, and
the facade sorts legs by it on the Java side (no `order by` in the engine).
Not a friction yet; "the leg after this one" in `nextExpectedActivity` is
the remaining P1 test.

- dddsample refuses an empty itinerary in the constructor; here an
  itinerary with no legs is simply the unrouted state (`¬anyLeg`), which is
  what `Delivery` calls NOT_ROUTED. A value-object guard that says "this value
  may not be empty" has no counterpart because the empty value is absence.

- An `if / else if / else` chain over a status (`calculateRoutingStatus`)
  becomes three disjuncts, and the `else` has to be said in full:
  `anyLeg ∧ ¬satisfied → MISROUTED`. Nothing falls through. Slightly more to
  write, and the three cases are visibly exhaustive and exclusive, which the
  chain only was by reading order.

## P1 closed: "the leg after this one" is index+1

`nextExpectedActivity` after an UNLOAD at port L: the leg unloading at L has
index i; the leg at i+1 says LOAD there, and `¬leg(c, i+1, …)` says CLAIM at
L. Same three lines as `lastLeg`. The ordered-sequence prediction is closed:
an index column and `addo` cover first, last and successor, and nothing was
clumsy enough to want an engine feature. What dddsample does with an
`Iterator` and `hasNext()` is one negation.

## P3 graded: absence is `Optional` at the facade, and the engine has no `null`

Every "sometimes there isn't one" in `Delivery` — last known location before
any handling, current voyage while not aboard, ETA and next activity while
not on track — is a rule with no answer, and the facade reads it as
`Optional.empty()`. dddsample carries `Location.UNKNOWN`, `Voyage.NONE`,
`ETA_UNKNOWN = null`, `NO_ACTIVITY = null` and a `default: UNKNOWN` transport
status that no switch arm reaches; none of them exist here, because a rule
that does not hold is not a value. The cost: `Delivery` is a Java value built
from eight solves rather than one relation, because one row with optional
columns is exactly the null-object problem again. Eight solves over a pinned
read set is not expensive; a single `delivery(c, …)` relation would need a
story for absent columns and I did not want one. Also gone: `calculatedAt` —
nothing is calculated and stored, so nothing is stale.

- A lookup (handling kind → transport status) is a relation over its own
  five facts in a static `AnswerStore`, read with the same literal as any
  other relation. The `switch` became a table; worth keeping as an idiom.

## 2. The rules read as a soup; part of that is essential, part is ceremony

After five slices the rules were one 300-line file, and Tom's reading was
"a soup that's hard to follow — but they interrelate freely, which may be
the point". Separating the two:

Essential, and the strength: dddsample wires the same logic by call order
(the `Delivery` constructor computes `misdirected` before `onTrack()` reads
it; `deriveDeliveryProgress` must run at the right moment or the snapshot is
stale). The rules have no order and no trigger; each one is queryable alone
and a new one composes with the rest without editing them. The flatness is
what makes derived state always current.

Accidental, fixed: one file for four of dddsample's concepts. The rules now
live in `rules/` as `History` ← `Itinerary` ← `Routing` ← `Progress`, the
same grouping as dddsample's packages, with the dependency direction visible
as constructor arguments; each file reads bottom-up.

Accidental, not fixed — the ceremony: every rule declares its columns twice
(Java parameters, then `.arg("x", x)` each), don't-care columns are
`lvar(), lvar(), lvar()`, and fresh locals need a `defer(() -> {…})` block.
None of that is relational structure; it is Java-hosted miniKanren syntax.
What the engine would need: a don't-care marker and a positional head would
remove most of it. Noted, not built — the shape is right, the spelling is
long.

## Non-event: an FD comparison under negation

`lastEvent` is `handlingEvent(c, …, t) ∧ ¬later(c, t)` with `later` a named
projection over `Instants.lss(t, t′)`. First rule, first run, correct — the
nogood store completes the inner extension with the comparison inside it and
nothing needed saying twice. Noted because it was the prediction most likely
to bite first.
