# cargo-oracle — dddsample's domain model as the oracle

The [DDD sample](https://github.com/citerus/dddsample-core)'s domain
model, driven beside the cargo facade on the same inputs, with every
field of the delivery compared after every step. It answers "is the
port faithful?" with their code rather than with a reading of it.

## What is here

- `src/main/java/se/citerus/dddsample/` — their `domain` packages, the
  `DateUtils` helper and the sample locations and voyages, vendored
  verbatim from commit `d8b896254ed4e18ba8117dcd5bf9a54708bdeaaf` (MIT,
  `dddsample-LICENSE.md`). The JPA annotations are inert without a
  runtime; nothing of Spring comes along.
- `Theirs` — their `Cargo` per tracking id, in memory, re-deriving its
  delivery after each handling as their inspection service does.
- `Ours` — the facade over a shared in-memory store with the same sample
  locations and voyages as facts, one committed transaction per command,
  a handling being two: the registration, then the inspection that
  shows it to the cargo.
- `Snapshot` — one delivery as both can state it.
- `Worlds` — both driven by one script and compared after every step.
  Two known divergences (ledger entry 9) are masked by name when the
  cargo's state meets them; `DivergenceTest` pins each as both answers,
  so a change on either side shows.
- `LifecycleScenarioOracleTest` — their scenario, compared rather than
  asserted. `GeneratedRunsOracleTest` — seeded random lifecycles over
  the sample schedule, with floors on what the runs must reach.

## Running it

From the root, with the domain module, on a JDK 17:

```
mvn test
```

or this module alone, `mvn -pl oracle -am test`.
