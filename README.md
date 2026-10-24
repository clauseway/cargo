# dddsample-clauseway

Eric Evans' cargo shipping domain, the one every DDD book and the
[DDD sample](https://github.com/citerus/dddsample-core) teach from,
ported onto [Clauseway](https://github.com/clauseway): the facts in
relations, every piece of derived state a rule, one transaction as the
unit of work, and the one derivation the sample stores, stored the
sample's way. The domain is someone else's, so the port could not bend it
toward the engine. That is the point. It is built to show two things.

**That the stack holds up against a real domain.** Booking, routing,
handling, misdirection, re-routing, inspection: all of it lands, and the
derived state agrees with the original's own code on every field across
hundreds of generated lifecycles. Where the engine and the domain rubbed
against each other, the friction is recorded, not smoothed over, in
[`NOTES.md`](NOTES.md): five predictions made before the first rule,
graded as the port reached them, and sixteen numbered entries on what
the engine forced, what it made free, and what was tried and taken out
again. Two of those entries changed the engine.

**An introduction to the library for people who already know DDD.** The
sample's vocabulary is kept, name for name and test for test, so the
distance between the object model and the relational one is visible in
every file. If you know what a `Delivery` is and why `Cargo` is an
aggregate, you can read this repository without reading the engine's
documentation first.

## If you know DDD: where the building blocks went

Evans wrote the tactical patterns for object-oriented models and says
so: entity, value object, aggregate and repository are the OO
expression of a model. What survives here is the model and its
language. The blocks are replaced by their relational counterparts,
and there is no object per cargo: the tracking id is the identity, the
relations keyed by it are its state, and `Cargo` is the type, the
repository and the application service in one. If that sounds like an
entity component system, it is the same move: an entity is an id, a
component is a table keyed by it, and the logic lives outside the
object, grouped by what it reads and writes. What differs is that a
rule derives on demand and never mutates, and a command lands facts
for one id on request; nothing runs over every entity on a tick. The
table says where each block went.

| in dddsample | here |
|---|---|
| entity, value object | a row in a relation, its class a schema that maps a solution into the value on the way out (`Leg` → `LegDto`, `Delivery` → `DeliveryDto`). Identity is what the rule head projects: two reports of one handling are one event because no rule names the registration time |
| aggregate `Cargo` and its invariants | a package. Its relations are written only by its own code and read elsewhere as rules, so the compiler keeps the access boundary. The invariants are rules; the consistency boundary is the transaction's footprint, certified at commit |
| repository | the `Transaction`: the read face answers rules over the facts, the write face stages them |
| domain service (`RoutingService`) | a rule: `CargoRules.candidate` enumerates the routes the schedule offers |
| application service (`BookingService`, `HandlingEventService`) | a command on the aggregate root, lifted by `Shipping`: its denial rules first, then the facts |
| factory and constructor validation | denial rules, every violated one named, not the first exception thrown |
| domain event (`CargoWasMisdirected`, `CargoHasArrived`) | a notice: a rule over the stored delivery's two flags, read after the inspection |
| the delivery snapshot, computed and saved on every handling | the same: one row the cargo's commands derive and store, eight rules solved over the staged facts. The handling reaches it by inspection, in a transaction of its own, as the sample's message hop makes it |
| optimistic lock on the aggregate | none in the sample. Here the commit proves the reads the command stood on are unmoved, on an in-memory store and on PostgreSQL alike; a delivery derived from a specification another party replaced is a stale read, refused |

## Reading order

1. `domain/src/main/java/org/clauseway/shipping/`: four packages, one
   per aggregate the sample has. Each holds its relations as
   package-private builders, written by its own commands alone, and its
   rules, static over any answer source, which is all that crosses
   between packages. `Cargo` is the root and the door Java calls: four
   relations, note the itinerary's `index` column, the parts as values,
   and the four commands. `History` is the handling aggregate, with the
   `LOption` voyage and `HandlingEventFactory`'s checks. `Voyages` is
   `Schedule`; `Locations` is the ports.
2. `CargoRules` beside `Cargo`, the cargo's rules in three sections: the
   itinerary, its legs by index and the plan of handlings it implies;
   the routing, `RouteSpecification.isSatisfiedBy`, the commands'
   denials and the routing service; and the delivery's calculations,
   where the cargo is, whether it strayed and what comes next, and the
   inspection's notices. `Delivery` beside them is the sample's
   `Delivery`: the stored row's shape as variables, its stored face, the
   derivation over the row, and the refresh every command ends with.
3. `Shipping` and `Tracking`: the commands and the reads, cut the way
   the sample cuts services from the aggregate. A command is the
   aggregate's own; it refuses by name or lands, and the facade lifts.
4. `domain/src/test/java/.../CargoTest.java` beside the sample's
   `CargoTest`, then `CargoLifecycleScenarioTest`, the sample's one story.
5. `NOTES.md`, from the predictions down. Entry 4 is the engine's rule
   nobody expected to meet. P3 is the entry that put `LOption` into the
   engine. P5 is where the port is stricter than the original. Entry 13
   is the aggregate boundary; entry 14 is the value boundary's one
   friction; entry 15 is the delivery stored the sample's way, and what
   that closed in the engine; entry 16 is why the rules are one bag and
   the root is the door.
6. `oracle/`: the sample's own domain classes driven beside the facade on
   the same inputs, deliveries compared after every step.

## The oracle

A port written from a reading of the original is only as faithful as the
reading. The `oracle` module settles it with the original's code: its
domain packages, vendored verbatim under their MIT license, are driven in
memory beside `Shipping` with the same bookings, routes and handlings, and
the delivery is compared field by field after every step, over the
lifecycle scenario and two hundred seeded random lifecycles. The two
places the port differs on purpose are masked by name and pinned as both
answers in `DivergenceTest`. The oracle's first run found a bug the
twelve test files had not: ledger entry 12.

## What is still open

- The aggregate's two jobs got two mechanisms: the package keeps the
  access boundary, the footprint keeps consistency. Every command writes
  one aggregate, but nothing enforces that, here or in the sample.
  Entries 13 and 15 say why.
- Three places the port stays looser than the original, recorded in
  entry 11 and left.
- The sample's application services hold one more rule each than its
  domain methods; the port sits at the domain level. Entry 11 again.

## Layout and running it

Two modules under one root: `domain/` is the port, Java 8 bytecode;
`oracle/` runs the sample's model beside it on Java 17. The build needs a
JDK 17:

```
mvn test
```

Eighty-five tests in the domain, eighty-two of them in memory on
`SharedDatabase`; three run the same facade over PostgreSQL under
serializable isolation and need Docker, and are skipped without it. The
oracle adds five, the generated lifecycles among them.

## Attribution

The domain is Eric Evans' and the sample is Citerus AB's and Domain
Language's, MIT; the vendored copy in `oracle/` carries their license.
Everything else is Clauseway's.
