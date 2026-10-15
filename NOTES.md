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

## 7. The absent voyage is a constructor: `LOption` lands, `NO_VOYAGE` goes

The engine grew its optional out of this ledger (entries P3, 6; the
`absence-is-a-constructor` note in logic), and the port is the receipt.
`handlingEvent`'s voyage column is now `Unifiable<LOption<String>>`;
`Voyage.NONE`-as-empty-string is gone, and so is the comment that
explained it. Same 51 tests, green first run against the logic and pldb
snapshots. What moved, and what it reads like:

- `registrationDenial`'s three voyage rules are one `matche` on the
  column's shape: `none()` → "voyage required" if the kind requires one;
  `some(v)` → "voyage prohibited" if the kind forbids one, or "unknown
  voyage" if `v` is not a voyage. The "unknown voyage" rule no longer
  says "and it isn't the empty string", because nothing is.
- `plannedHandling` binds the shape per step — `none()` for the receipt
  and the claim, `some(legVoyage)` for a load or unload — and
  `currentVoyage` destructures in the head position:
  `lastEvent(c, LOAD, _, some(voyageNumber), _)`, with `voyageNumber`
  a plain `Unifiable<String>` in its own head, because a cargo that HAS a
  current voyage has a real one. The rule stays partial; its type says so.
- `expected`, `misdirected`, `nextExpectedActivity`, `lastEvent` changed
  only the column's type: they pass the shape through.
- The facade reads `row.get(voyage).get().toOptional()` and writes
  `LOption.of(optional)`; `HandlingEvent` and `HandlingActivity` carry
  `Optional<String>`. The tests spell `Optional.of("CM001")` and
  `Optional.empty()` where they spelled a string and a sentinel.

Net: 13 files, +97/−85, and the optional appears exactly where
dddsample has a null object and nowhere else. The one thing the port does
NOT do is make `Delivery` a row again — entry 6's reasoning stands, now
with the proviso that an `LOption` head would be legal and visible; the
facade's eight solves remain the choice.

## Non-event: an FD comparison under negation

`lastEvent` is `handlingEvent(c, …, t) ∧ ¬later(c, t)` with `later` a named
projection over `Instants.lss(t, t′)`. First rule, first run, correct — the
nogood store completes the inner extension with the comparison inside it and
nothing needed saying twice. Noted because it was the prediction most likely
to bite first.

## P4 graded: route finding under time — a non-event, and the leg is a stretch

dddsample's `RoutingService` asks a stub pathfinder for random itineraries
and keeps the ones `isSatisfiedBy` accepts. The port has a route finder:
three rules, first run green, with a three-connection route in the test.

- `Voyages.aboard(v, from, to, dep, arr)` — a stretch of one voyage: a
  sailing, or a sailing followed by a stretch from the port it lands at,
  leaving no earlier than it landed. A leg is a stretch, not a sailing:
  the cargo stays aboard V001 past Tokyo and the leg reads Hong Kong to
  New York, which is the shape `RouteSpecificationTest` already stored.
- `Routing.path(from, connection, destination, deadline, legs)` — a stretch
  to some other port; either that port is the destination and the route
  is the one leg, or the route goes on from there. The connection is what
  the cargo arrived by — `none()` at the origin, `some((landed, voyage))`
  after a leg — and the next stretch leaves no earlier than it landed, on
  another voyage. `Routing.candidate(c, legs)` reads the specification.
- The itinerary is a structured answer: an `LList` of five-tuples, the
  first time the port returns a structure rather than rows. `solve(legs)`
  delivers it reified through the table, tuple members included, and the
  facade walks it into `List<Leg>`.

The predicted engine surprise did not come. Termination owes nothing to
tabling: every sailing and every connection advances time, and the
deadline bounds it, so the recursion is finite on any schedule, cyclic
voyages included. What the table contributes is sharing — the routes
onward from New York are one entry read by every stretch that lands
there — which is an observation here, not a receipt; nothing counts it.

Two nogoods keep the silly candidates out: a stretch that returns to its
port is not a leg (`from ≠ to`), and the route never re-boards the voyage
it just left (`arrivedBy ≠ voyage`) — without the second, Hong Kong to
Tokyo and Tokyo to New York, both on V001, would be offered as a
two-leg route. Not excluded: calling at a port twice by different
voyages. Time bounds it; no test wants it out.

Friction: the result type is spelled in full three times —
`Unifiable<LList<Tuple5<Unifiable<String>, …>>>` — because Java has no
alias and the engine decomposes tuples but not a `Leg` of its own. That
is the itch the structural contract note names: a user-defined structure
with members would make `Unifiable<LList<LegTerm>>` writable.

## P5 graded: the certified write — refused where the decision's read moved, admitted where it is serializable

dddsample puts a version column on `Cargo` so that assigning a route and
changing the destination cannot both save. The port has no aggregate to
version; the commit proves the reads its decision stood on, and what
"proves" means depends on the tier. Nothing was built; eight tests,
first run green, four per tier.

On the simulated tier (`SharedDatabase`, the footprint of pins):

- Route chosen by reading the specification (`routeCandidatesOf`, then
  `assignToRoute` in the same transaction), destination changed and
  committed by another party: the route's commit is refused with
  `Conflict`, nothing of it lands, the cargo reads NOT_ROUTED. The
  refused party reopens, re-solves, gets the route for the new
  destination, lands as ROUTED.
- Route assigned blind (`assignToRoute` alone reads only the leg region)
  beside the same change: both land, the derived status is MISROUTED.
- Two re-specifications: each read the one it replaces; the second is
  refused.

On PostgreSQL under SSI (`SerializableSource.postgres`, the rented tier):

- The two re-specifications: the second one's retraction meets a row the
  first deleted, and SSI refuses in its dialect — received as `Conflict`.
- The route chosen for a specification another party then changes: BOTH
  commits land, and the cargo reads MISROUTED. The schedule is
  serializable — "route first, then re-specify" is an order in which
  both parties did what they did — and SSI admits every serializable
  schedule.

So the two tiers disagree on exactly the race the prediction named, and
both are right by their contracts. A pin asks that the read still hold
at commit, which is stricter than serializability; the transaction note
calls it an over-approximation ("retries, never unsoundness"), and here
is the domain case where the approximation is the behaviour dddsample
wanted: its version column refuses this schedule too. SSI is the
weaker certifier for this domain, not a wrong one. Where the decision
did not read what moved, both tiers let the write through, because
nothing stored can go stale — dddsample would have refused one of the
blind pair as well, to protect a stored `Delivery` the port never has.
The lock protected a cache; the footprint protects a decision; SSI
protects an order.

Whether a blind `assignToRoute` should exist at all the ledger cannot
say from here; dddsample's entity method is blind too, and the
certified form costs one read in the same transaction, so the port
keeps both and the caller chooses.

## P2 graded: the derived-state event is a read, because dddsample's is too

The prediction assumed dddsample raises "cargo was misdirected" when the
flag flips. It does not: `CargoInspectionService.inspectCargo` re-derives
the delivery after a handling and raises `cargoWasMisdirected` if the
cargo IS misdirected and `cargoHasArrived` if it IS unloaded at its
destination — state, not change. So there is no before-and-after to
solve. The port is one rule, `Progress.notice(c, notice)`: misdirected
gives MISDIRECTED, unloaded at destination gives ARRIVED, both when both
hold (arriving off the wrong ship raises both there as well), and the
facade's `inspect(c)` reads it after a registration. Four tests, first
run green. What dddsample stores on inspection — the re-derived
`Delivery` — the port never stored, so the inspection writes nothing.

Open, not graded: a notice that should fire ONCE per change (so the
same misdirection is not reported after every later handling) would be
a before-and-after, and dddsample does not have it either; nothing in
the sample asks for it.

## The Postgres world: one method binds the schema, the rest is the same facade

`Schema.onSql(source)` registers three codecs through template literals —
instants as `TIMESTAMP`, the handling kind by its name, the voyage column
of `handlingEvent` through `Codec.optional(String.class)` so NULL is
`none()` on the way in and `none()` is NULL on the way out — and
`Shipping.over(Transaction.over(…))` is the same facade on the native
tier. The story (booking, route finding over `carrierMovement` regions
with the time comparisons pushed through the codec, assignment, three
handlings, the delivery, the inspection, a misdirection) runs unchanged;
the tables are seven `CREATE TABLE`s by pldb's convention, unquoted so
that the camel-cased names fold the same way on both sides.

One receipt against the port, not the engine: a facade opened for a read
and never closed is a Postgres session idle in an open transaction,
holding its relation locks — the next test's `DROP TABLE` waited on it
forever. On the simulated tier a forgotten transaction costs nothing; on
the rented tier it costs everyone else's DDL. The test closes what it
opens; the facade is `AutoCloseable` for this reason, and the in-memory
tests never had to notice.

## 8. The facade cut in two: commands are services, reads are the aggregate's parts

`Shipping` had grown to twenty-six public methods, and dddsample cuts
the same surface four ways: `BookingService`, `HandlingEventService`,
`CargoInspectionService`, and a tracking interface with no service at
all — a controller reads the aggregate and a view adapter builds the
DTO. That partition is by who calls. The rules were partitioned the
other way, by what is known: `History` is `HandlingHistory` and the
event factory, `Itinerary` the itinerary value, `Routing` the
specification's `isSatisfiedBy` and the `RoutingService`, `Progress` the
`Delivery` — dddsample's domain objects, with the same dependency order
(`Delivery.derivedFrom` takes the specification, the itinerary and the
history; `Progress` consumes the other three).

Every facade read reaches exactly one rule class and marshals into that
class's value type, so the reads already had the domain's partition and
not the services'. The cut follows that: `Shipping` keeps the lifecycle,
the facts and the commands — the services' side, which needs the value
chain, since a command must return the world it extends — and `Tracking`
holds the reads in four sections by rule class, each with its
row-to-value reading beside it. A four-way cut by service would have had
Tracking reach into every rule class, which is what dddsample's tracking
view does; faithful, but it buys a hop on every chained call for no
meaning.

What the split does not touch: the facade was thick because the engine
hands back rows and the value types want objects, and nothing in the
stack does that translation. Over half of the old `Shipping` was
columns-to-value readers; they are now `EventColumns`, `LegColumns`,
`ActivityColumns`, one per value type, and that is where the mass went.

## 9. Audit against the source: the port was written from memory, and here is what that cost

Until this entry, nothing in this ledger had been checked against
`citerus/dddsample-core`; the port and every "dddsample does X" above
were recollection. Tom asked how he would know the port is faithful.
The source is now cloned beside the project (commit `d8b8962`,
2025-06-02), and this entry is the walk through it: domain model,
services, and the seven domain tests plus the lifecycle scenario, read
against the rules and the ledger. Verdicts first, then the divergences.

**Wrong.** P5 says "dddsample puts a version column on `Cargo`" and that
its lock refuses the route-then-respecify schedule. At this commit there
is no `@Version`, no lock mode, no isolation setting anywhere in the main
sources; `Cargo` is a plain JPA entity and the two services are
`@Transactional` at the default level. The race P5 names is unguarded
in dddsample: both saves land, the last one wins, and the stored
`Delivery` is whatever the second party computed. So the simulated tier
is stricter than dddsample, not equal to it, and the sentence "its
version column refuses this schedule too" is withdrawn, with the one
after it ("dddsample would have refused one of the blind pair as well").
The rest of P5 — what each tier does and why — stands; it was measured,
not remembered.

**Wrong, already superseded.** The translation choice "the empty number
is what dddsample itself stores": `HandlingEvent` stores a null voyage
and the accessor returns `Voyage.NONE`. Entry 7 removed the sentinel
anyway.

**Holds.** `distinctEventsByCompletionTime` through a `HashSet` with
`sameEventAs` ignoring `registrationTime` (entry 1). `Itinerary`
refuses an empty leg list (P1). `isExpected` as one `if` per handling
kind and `calculateNextExpectedActivity` as a `switch` with an
`Iterator` (entries 3, 5). `Cargo.specifyNewRoute` swaps the value in
place (entry 4). The null objects `Location.UNKNOWN`, `Voyage.NONE`,
`ETA_UNKNOWN`, `NO_ACTIVITY` and the unreachable `default: UNKNOWN`
(P3). RECEIVE expected at the specification's origin, where the plan
says the first leg's port (entry 5). The inspection raises on state,
both notices when both hold (P2). The routing service is a random
pathfinder behind a satisfaction filter (P4). The `Delivery` constructor
computes `misdirected` before `onTrack()` reads it (entry 2).

**Divergences in behaviour**, found by reading, each one a case for the
oracle test to pin:

- A booked, unrouted cargo that has been handled and inspected reads
  MISROUTED there and NOT_ROUTED here. `Cargo.itinerary()` returns an
  empty itinerary rather than null, `deriveDeliveryProgress` passes it
  on, and `isSatisfiedBy` fails against an unknown departure. Before
  any inspection the constructor path says NOT_ROUTED, so dddsample
  answers this question two ways depending on whether a handling has
  been seen; the port answers it one way. Kept.
- Changing the destination of an unrouted cargo that has been handled
  throws there: `updateOnRouting` passes a null itinerary and
  `calculateMisdirectionStatus` dereferences it. Here it lands. Kept.
- Booking with an unknown location is refused there (the repository
  returns null and the specification's constructor throws) and
  admitted here: `specificationDenial` checks only that origin and
  destination differ, while `registrationDenial` does check the port.
  A gap in the port.
- After a LOAD or UNLOAD, the next expected activity there is read off
  the first leg with that load or unload port, voyage ignored; here it
  is the step the last handling matched, port and voyage. Same answer
  unless an itinerary calls at one port twice.

And one agreement worth recording because I had it wrong aloud: after
re-routing a misdirected cargo, dddsample's scenario leaves "no longer
misdirected" as a TODO. Its `updateOnRouting` does re-derive every
field from the retained last event, and that event — the unload at the
wrong port — is not expected by the new itinerary either, so both
implementations say misdirected until the next handling. The rule is
the same rule; the TODO is the domain's, not the snapshot's.

The remaining distance between "read against the source" and "known to
agree" is the oracle: their domain model driven beside the facade on
the same inputs, every field compared. Next.

## 10. The lifecycle scenario, transliterated: green step for step, and one thing it forced

dddsample's `CargoLifecycleScenarioTest` is the one test of theirs with
a story in it: Hongkong to Stockholm, received, loaded on V100,
unloaded at Tokyo by mistake, re-specified from Tokyo, re-routed over
V300 and V400, claimed. Twenty-nine assertions on the delivery in a
fixed order with fixed dates. It cannot be pointed at our code — the
assertions go through their concrete `Cargo` and `Delivery`, and two
steps mutate the aggregate directly — so it is transliterated
(`CargoLifecycleScenarioTest`): their sample locations and voyages as
facts, their routing stub's itineraries assigned as legs, every
assertion as written against `deliveryOf`. Every one passed on the
first run that reached it.

What it forced: the first draft ran the whole story inside one
`Shipping` value chain, the way their test runs it inside one object
graph, and the engine refused the re-route — the specification staged
by the booking cannot be retracted by the same transaction (entry 4).
Their scenario never sees this because its in-memory repositories have
no transactions at all; in their production wiring every service call
is `@Transactional`, so each use case is its own commit. The
transliteration commits one transaction per use case over a shared
store, which is the real shape, and the engine's rule is what made the
test say so.

Not checked by this test, still owed to the oracle: the four
divergences in entry 9, none of which the scenario's path crosses.
