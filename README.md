# cargo — dddsample's shipping domain as rules

The [DDD sample](https://github.com/citerus/dddsample-core) shipping
domain, ported onto the Clauseway stack: facts in `pldb` relations,
every piece of derived state as a `logic` rule, one transaction as the
session. The port is an experiment with a ledger — `NOTES.md` records
predictions made before the first rule and grades them as the entries
come in — and the code is the receipt.

## What is here

- `Schema` — dddsample's three aggregates flattened to seven relations:
  `location`, `voyage`, `carrierMovement`, `cargo`, `routeSpecification`,
  `leg` (the itinerary, indexed) and `handlingEvent` (whose voyage is an
  `LOption`, because port-side handlings have none). `Schema.onSql`
  binds the codecs a SQL source needs.
- `rules/` — the derived state, each a `Literal` rule over the facts:
  `History` (the last event, registration denials), `Itinerary` (first
  and last legs, the plan of handlings a route implies, what is
  expected), `Routing` (whether an itinerary satisfies the specification,
  the routing status, and `path`/`candidate`: the routes the schedule
  offers), `Voyages` (a stretch aboard one voyage), `Progress` (transport
  status, last known location, current voyage, misdirection, ETA, the
  next expected activity, the inspection notices).
- `Shipping` — the facade: an immutable value over one transaction.
  Commands check their denial rules and land facts; `commit()` certifies
  the reads the commands stood on.
- `Tracking` — the read face, reached as `shipping.tracking()`: every
  derived fact as a value, one section per rule class.
- Value types — `Leg`, `RouteSpecification`, `HandlingEvent`,
  `HandlingActivity`, `Delivery`, and the enums.

Nothing derived is stored. `Delivery`, which dddsample computes and
saves on every handling, is eight solves at read time; the inspection
that raises "misdirected" or "arrived" is a rule read after a
registration.

## Running it

```
mvn test
```

Sixty-five tests run in memory on `SharedDatabase`. Three more run the
same facade over PostgreSQL under serializable isolation and need Docker;
they are skipped without it.

## Reading the ledger

`NOTES.md` is the point. It opens with five predictions (ordered
sequences, derived-state events, optional columns, route finding under
time, the certified write) and grades each as the port reaches it; the
numbered entries record what the engine forced, what it made free, and
what was tried and reverted. Two of them changed the engine: optional
columns became `LOption`, the stack's absence-as-a-constructor, and the
single-row delivery was built and taken out again.
