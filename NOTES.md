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
  `Voyage.NONE` with the empty voyage number, rather than a nullable column
  (which the engine has — `.nullable()` and `lval(null)`; see the
  correction under P3). The empty number is what dddsample itself stores.
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

- **Correction (Tom):** "the engine has no `null`" is false. A column
  declared `.nullable()` carries `lval(null)` as a value that equals itself
  and nothing else — a free probe delivers it, a bound-null probe selects
  it, an indexed null cell keys its bucket, and SQL NULL rides through a
  declared-nullable column (`NullCellTest`, `NullableColumnTest`). So a
  single `delivery(c, …)` relation with nullable columns was available;
  what it would cost is that each disjunct must SAY `eta.unifies(null)` in
  its absent case, where a rule with no answer says it by not holding.
  The eight-solve `Delivery` stands as the choice I prefer, not the one
  the engine forced. The rest of the entry holds: dddsample's null
  objects (`Location.UNKNOWN`, `Voyage.NONE`) and the unreachable
  `default: UNKNOWN` are still gone, because absence is a rule not holding.

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

## 3. The port is too literal: one invariant, written twice, in two directions

Tom's question, after reading the delivery rules: where does the original
pay for control — an invariant kept implicit and re-implemented as separate
functions for each direction it is used in — while the relational model
states it once and queries it any way round? The first answer I gave was
about recomputation; that is performance, not what was asked. The answer:

- `Itinerary.isExpected(event)` and `Delivery.calculateNextExpectedActivity`
  are the same invariant — the itinerary determines the sequence of handlings:
  receipt at the first load port, then per leg a load and an unload on that
  voyage, then a claim at the last unload port — written twice, as a
  membership walk over the legs and as a successor walk over the legs, with a
  different `switch` each. The sequence itself is stated in neither; it is
  implicit in both bodies, and a rule added to one has to be re-derived in
  the other by hand.
- `HandlingEvent`'s two constructors enforce "LOAD/UNLOAD carry a voyage, the
  rest must not"; `isExpected` assumes it by comparing voyages only for those
  two. Enforced in one place, relied on in another, stated in neither.
- `RouteSpecification.isSatisfiedBy` (given an itinerary, does it fit?) and
  `RoutingService.fetchRoutesForSpecification` (given a specification, which
  itineraries fit?) are one invariant in its checking and its generating
  direction — the second one outsourced to a pathfinder plus a filter.

And the port, so far, reproduces the first two one-for-one: `expected` and
`nextExpectedActivity` are two rules transcribed from two functions, and the
voyage convention is a comment on `handlingEvent`. The translation was
faithful to the functions where it should have been faithful to the
invariant. What it should be: one relation `plannedHandling(c, seq, type,
location, voyage)` stating the sequence; `expected` is "∃ seq (or customs)",
the next activity is "the planned handling at the seq after the one the last
event matched", `misdirected` is "the last event matches no seq", and
questions dddsample has no function for — how far along the plan a cargo is,
which cargos are past step three — cost nothing. Likewise one fact relation
`handlingKind(type, voyageRequired)` for the denial at registration and for
any rule that needs it; and, in the next phase, one `route(…)` relation over
`carrierMovement` that generates itineraries forward and checks them with
the legs bound.

Decision: finish the literal port first — denials, re-routing — so the
faithful translation is complete and reviewable on its own, then do the
`plannedHandling` refactor as a separate step with the same tests, so the
difference between porting the functions and modelling the invariant is one
diff.

## 4. Replacing a value object within the transaction that created it is refused

dddsample's `Cargo.specifyNewRoute` swaps the `RouteSpecification` value in
place; our translation is retract-old + assert-new. Booking stages the spec;
re-specifying in the SAME transaction retracts a fact that is still only
staged, and `WriteBuffer.refuseCollision` refuses: "the fact … is staged with
the opposite polarity — this transaction has not decided what it believes".
The library never met this because its writes are append-only (a loan and
its return are two facts). The first domain that replaces a value hits it on
the first re-route.

The rule is deliberate — a transaction's delta is a signed set of claims,
and a fact claimed both ways is a contradiction, not a cancellation — and it
is the right rule for two independent commands landing in one transaction.
But "book, then correct the destination before confirming" is one request,
and the facade cannot express it. Had to: commit between (the tests open a
second transaction over a `SharedDatabase`, the library's idiom). Open
question for the engine: should retract-after-assert in one buffer CANCEL
(the delta is a set, and asserting then retracting leaves it unchanged),
with only assert-after-retract-of-a-base-fact as the refusal? That would
make "replace" a single-transaction operation. Not changed here — it is a
pldb decision, and the refusal is receipted as a test.

## 5. The refactor from entry 3: the invariant stated once, the functions become one-liners

Done as its own diff on the same tests (plus one for the new relation);
nothing else moved, every test passed first run.

`plannedHandling(c, step, type, location, voyage)` is the itinerary as a
sequence of handlings: RECEIVE at the first leg's port is step 0, the leg
at index i plans LOAD at 2i+1 and UNLOAD at 2i+2, CLAIM where the last leg
lands is one more (`Ints.multo` and `addo`, forward from the index; with
the step bound the same arithmetic is a check, because the leg literal
enumerates first). Over it:

- `expected` is "not routed, or planned at some step, or customs" — the
  five typed disjuncts transcribed from `isExpected`'s `switch` are gone.
- `nextExpectedActivity` is "step 0 before any handling, else the step
  after the one the last handling was": `lastEvent` ∧ `plannedHandling` ∧
  `addo(step, 1, next)` ∧ `plannedHandling(next)`. The four `case` arms
  with their two `for` loops and the index+1 negation are gone; 40 lines
  became 12. The CLAIM and CUSTOMS arms that returned `NO_ACTIVITY` need no
  spelling: a claim has no next step and customs is at none, so the rule
  has no answer.
- `handlingKind(type, voyageRequired)` is the enum's flag read as a table,
  built from `HandlingType.values()`; the denial says "requires a voyage
  and names none" instead of enumerating the kinds a second time.
- `planOf(c)` on the facade — the question dddsample has no function for —
  is a `select` over the new relation. Nothing else was needed.

One difference from the port worth stating. Before any handling, dddsample
expects RECEIVE at the *specification's* origin; the plan says the *first
leg's* port. The rule only fires on track, and ROUTED means `satisfied`,
which includes "first leg loads at the origin", so the two agree exactly
where the rule applies. The port had copied the function's choice of
which fact to read; the plan reads the one the invariant is about.

And one smell left where it was found: `lastLeg` and `plannedHandling`'s
CLAIM branch both need "the index of the last leg", so that negation is
now a relation of its own, `lastIndex`, used by both — the second copy
(in the old `nextExpectedActivity`) is gone with the arms.

## 6. The delivery as one row, tried and reverted: nulls are for SQL, not for rules

After the P3 correction I built `Delivery` as one relation
(`7cc4890`, reverted here): the eight component rules conjoined, each
partial one reified as `rule(c, x) ∨ (¬rule(c, _) ∧ x = null)` on a
`.nullable()` column, each boolean as `rule ∧ true ∨ ¬rule ∧ false`,
`deliveryOf` one `select`. Twelve lines, same tests, green first run.
Receipts from the attempt, worth keeping:

- `exclude` over a RULE with a free head column, `projected()` in the
  slot, works as over a fact relation. First use; a non-event.
- `x.unifies(null)` is ambiguous between `unifies(T)` and
  `unifies(Unifiable<T>)`; `x.unifies(lval(null))` compiles and binds the
  null — no cast needed (Tom checked).

Why it came out again — Tom's call, and the right one: a rule that has
no answer composes with everything the engine has. Conjunction prunes by
it, `exclude(eta(c, _))` is "the cargos with no ETA", and it can sit under
another rule's negation. A total row with nulls composes with none of
that: absence has become a value, every rule built on top has to test it,
and that is the three-valued-logic hole SQL has. Nullable columns exist
so the engine can read a customer's schema as it is; a DERIVED relation
has no reason to put a null in its head, because it can say absence by
not holding. The eight solves in `deliveryOf` are that reification done
at the edge, in Java, where it belongs — the facade assembles the row for
the caller and the rules stay partial for each other.

## Non-event: an FD comparison under negation

`lastEvent` is `handlingEvent(c, …, t) ∧ ¬later(c, t)` with `later` a named
projection over `Instants.lss(t, t′)`. First rule, first run, correct — the
nogood store completes the inner extension with the comparison inside it and
nothing needed saying twice. Noted because it was the prediction most likely
to bite first.
