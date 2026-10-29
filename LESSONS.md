# Lessons

What the port of `citerus/dddsample-core` taught, distilled from the friction
ledger (`NOTES.md`). The ledger is evidence, appended as hit and never
rewritten; this file is what the evidence adds up to. Entry numbers point back
to it. Where a lesson corrects an earlier belief, the earlier belief is named,
because the correction is the lesson.

## 1. The frictions were at the Java boundary, not in the logic

Five predictions were written before the first rule. The two about the logic
itself, ordered sequences (P1) and route finding under time (P4), were
non-events: an index column, `addo` and one negation cover first, last and
successor; the route finder was three rules, first run green, and termination
owed nothing to tabling. The prediction about derived-state events (P2) was a
misreading of the sample, which raises on state, not change. The one about the
certified write (P5) was measured correctly and then corrected by the audit,
because the sample's guard it was compared against did not exist.

The friction that was real, and that kept returning, was P3: absence. Every
later engine change the port forced (§8) is about the seam between a rule and
a Java value, not about what a rule can say.

## 2. Absence is a rule not holding

The sample carries `Location.UNKNOWN`, `Voyage.NONE`, `ETA_UNKNOWN = null`,
`NO_ACTIVITY = null` and an unreachable `default: UNKNOWN`. None exist here. A
rule that does not hold composes with everything: conjunction prunes by it,
`exclude(eta(c, _))` is "the cargos with no ETA", and it can sit under another
rule's negation (P3, 6).

The belief "the engine has no null" was wrong: a `.nullable()` column carries
`lval(null)`. What is right is narrower. Nullable columns exist so a customer's
schema can be read as it is; a DERIVED relation has no reason to put absence
in its head, because every rule built on top would have to test it, and that
is SQL's three-valued hole (6). `LOption` is the one honest place for absence
as a value: at the column where the sample has a null object, and nowhere else
(7). The delivery as one row was built twice. The first time it was reverted
(6). The second time it stands, because it is a leaf: its only consumer is the
write door, and the eight rules beneath it stay partial for each other (15).

## 3. Three things the sample implements that the engine gives away

- **Identity and dedup are projection.** `sameEventAs` ignoring
  `registrationTime`, plus a `HashSet`, is a rule head that does not name the
  column. A derived relation's answers are a set (1).
- **Uniqueness is a certified negative read.** "Already booked" is a denial
  reading absence before the write; it is safe because the probe that found
  nothing is pinned and the second commit finds the relation moved. No
  sequence, no unique index (11).
- **Rules have no order and no trigger.** The sample wires the same logic by
  call order (`misdirected` computed before `onTrack()` reads it). Flat rules
  are what keeps derived state current, and the flatness read as "soup" until
  the files followed the sample's packages (2).

## 4. Port the invariant, not the functions

The first port transcribed `isExpected` and `calculateNextExpectedActivity`
one-for-one, and so said the same invariant twice, in two directions, with a
`switch` each (3). The invariant, that an itinerary determines the sequence of
handlings, is one relation, `plannedHandling(c, step, type, location, voyage)`.
Over it the two functions are one-liners, the `handlingKind` table replaces a
second enumeration of the kinds, and questions the sample has no function for
(how far along the plan a cargo is) cost nothing (5). The method that found
this: finish the literal port, then do the refactor as a separate diff on the
same tests, so the difference between porting functions and modelling the
invariant is readable.

## 5. The aggregate is a visibility boundary; the consistency is the footprint

Clauseway composes across an aggregate boundary as readily as inside one, and
not with Java at all. DDD says where the Java doors go: the aggregate root.
Each aggregate is a package; its relation builders are package-private, so no
outside code can build a fact of them, which is to say assert or retract one.
What crosses packages is rules only. Every write is a method of the root that
solves its own denials and answers `Try`; a writer that trusts its caller to
have checked is not a guard (13). Rules are static over an answer source, the
engine's own style, and the holders bound to a transaction that preceded them
had no design behind them (16).

What the boundary does not enforce, stated so it is not oversold: one
aggregate written per transaction, and `fact()` on a rule-built literal, which
the write door still admits (13, 16).

## 6. "Derived" names which store is the source

The port first removed the sample's stored `Delivery` as a redundant cache and
derived it at read, eight solves per read. The sample's reading, and the one
that stands: the delivery is the cargo's state, maintained by the cargo's own
commands, and the handling is shown to it from outside (15). The unit is a
materialized view at row grain: a stored relation, a derived rule with exactly
its shape, and a refresh every command ends with. `replacing(stored, derived)`
is the door the engine grew for it, and an unchanged row cancels to nothing
because the delta is a signed set (4, 15).

Two consequences were not predicted. Replacing a value within the transaction
that staged it was a refusal until the delta became a signed set (4, 15). And
the certified write changed character: with the delivery written by every
command, the route-then-respecify race is a read-write cycle, and the pin and
SSI refuse it alike, where before they disagreed and both were right (P5, 15).
The lock protected a cache; the footprint protects a decision; SSI protects an
order.

## 7. The row type is a selector, and the mapping is the domain's one decision

The leg was said four times: the relation, its literal, the solver's tuple,
the Java value, with mapping code at every seam (14). Three designs for the
row type followed in two days, two of them green and wrong: a row as a term
the reifier rebuilds (unsound, an `Any` cannot sit in a `Unifiable` field),
then fields typed `Term` (sound, twenty lines of casts). The engine is honest
about its two directions, `Unifiable` in and `Reified` out, and every attempt
to give a field one type for both lied in one direction (17).

What stands: the row type is the Java interop layer, not a term. A term is
rebuilt by the reifier, with whatever the answer holds, so its members must be
`Term`; a row type's value is built by its own `map`, through `Row.get`, which
refuses a cell that is not ground, so its fields are the variables and the
value is plain Java. That is what `Selector` is, and pldb's `Schema` is a
Selector that also names its columns (17, 18). The mapping came off the
Selector for a day and went back on. The fault it was blamed for was real but
lay elsewhere: the typed stream had been the only terminal, so a sort key had
to ride inside the value (`Leg` mapped to a tuple) and a fact needed a door of
its own. `rows()` beside `stream()` is the fix; the index is read off the row
and never enters the leg. The names are still said three times, fields,
columns and mapping; the first two could be generated, the third is the only
line with a decision in it.

## 8. What the port forced into the engine, and what it still owes

Landed, each with the entry that forced it: `MagicVar` as a variable to the
finite domain (translation choices); `LOption` (7); the signed delta (15);
`Selector`, `Schema` and `Row.get` as the gate (17); the staged literal
builder and `describe()` (18); `replacing`/`clearing` (15, 17); `names()` and
`ground()` with the whole-row refusal; `fact(Row)` with the refresh on the
query's own driver; the `known` guard that keeps a derivation range-
restricted; a marker that refuses unclaimed.

Owed, named and not built: `fact()` refusing a rule-built literal (13, 16); an
unsafe-head check so a rule total in a column is refused at build rather than
read as an unbooked cargo's empty delivery; a relation whose row is a value
type, so a leg is one shape (14); a user-defined structure a rule can carry as
one term (P4, 17); the materialized view as a thing the engine names (16).

## 9. A port needs an oracle

Nine entries were written from memory before the source was read; the audit
withdrew one claim (the version column) and found four divergences (9). The
transliterated scenario passed every assertion on the first run that reached
it, and forced one-transaction-per-use-case (10). The oracle, the sample's
domain vendored and driven beside the facade with two hundred seeded
lifecycles, found on its fourteenth run what twelve test files had not: a
re-route sharing a leg with the route it replaced, refused by the signed-delta
rule (12). Reading finds divergence in derivation; only execution finds it in
the facade.

## 10. Working notes

- A facade opened on Postgres and not closed holds its relation locks; the
  next `DROP TABLE` waits forever. The in-memory tier never says so.
- Ledger entries 13 to 17 ran to 130 lines each. An entry is what changed,
  what it did here, what it amends, what was not done, and should fit in
  thirty.
- The pattern that held for every rule in the ledger: one test per claim,
  written first, and the rule green on its first run or rewritten. Where a
  rule was green and wrong (17), the test had not reached the case; the
  lesson is about the test, not the rule.
