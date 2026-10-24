package org.clauseway.shipping.oracle;

// ABOUTME: Seeded random lifecycles over dddsample's sample schedule — routes
// ABOUTME: found or made up, handlings on plan or astray — compared after every step.

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import se.citerus.dddsample.domain.model.location.Location;
import se.citerus.dddsample.domain.model.voyage.CarrierMovement;
import se.citerus.dddsample.domain.model.voyage.Voyage;
import se.citerus.dddsample.infrastructure.sampledata.SampleLocations;
import se.citerus.dddsample.infrastructure.sampledata.SampleVoyages;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.handling.HandlingType;
import org.clauseway.shipping.cargo.dto.LegDto;

public class GeneratedRunsOracleTest {

	private static final int RUNS = 200;
	private static final long SEED = 20261015L;
	private static final Instant DEADLINE = Instant.parse("2010-01-01T00:00:00Z");
	private static final Instant FIRST_HANDLING = Instant.parse("2008-10-01T00:00:00Z");

	private static final List<String> PORTS = SampleLocations.getAll().stream()
			.map(l -> l.unLocode().idString()).sorted().toList();
	private static final List<Voyage> VOYAGES = SampleVoyages.getAll().stream()
			.sorted((a, b) -> a.voyageNumber().idString().compareTo(b.voyageNumber().idString())).toList();

	@Test
	public void everyGeneratedLifecycleAgreesAfterEveryStep() {
		Random random = new Random(SEED);
		for (int run = 0; run < RUNS; run++) {
			lifecycle(random, "RUN" + run);
		}
		Worlds.Coverage c = Worlds.coverage;
		assertThat(c.comparisons).as(c.toString()).isGreaterThan(1000);
		assertThat(c.masked).as(c.toString()).isLessThan(c.comparisons / 5);
		assertThat(c.routed).as(c.toString()).isGreaterThan(100);
		assertThat(c.misrouted).as(c.toString()).isGreaterThan(100);
		assertThat(c.misdirected).as(c.toString()).isGreaterThan(100);
		assertThat(c.claimed).as(c.toString()).isGreaterThan(50);
	}

	private static void lifecycle(Random random, String id) {
		Worlds w = new Worlds();
		String origin = pick(random, PORTS);
		String destination = pickOther(random, PORTS, origin);
		w.book(id, origin, destination, DEADLINE);
		w.assertAgree(id);

		if (random.nextDouble() < 0.85) {
			w.assignToRoute(id, itinerary(random, w, id));
			w.assertAgree(id);
		}

		int handlings = random.nextInt(9);
		for (int step = 0; step < handlings; step++) {
			boolean unroutedAndHandled = w.ours.tracking().itineraryOf(id).isEmpty()
					&& w.ours.tracking().lastEventOf(id).isPresent();
			if (random.nextDouble() < 0.1 && !unroutedAndHandled) {
				destination = pickOther(random, PORTS, origin);
				w.specifyNewRoute(id, origin, destination, DEADLINE);
				w.assertAgree(id);
				if (random.nextDouble() < 0.6) {
					w.assignToRoute(id, itinerary(random, w, id));
					w.assertAgree(id);
				}
			}
			Instant at = FIRST_HANDLING.plus(step, ChronoUnit.DAYS);
			Optional<HandlingActivityDto> planned = w.ours.tracking().deliveryOf(id).getNextExpectedActivity();
			if (planned.isPresent() && random.nextDouble() < 0.7) {
				HandlingActivityDto a = planned.get();
				w.handle(id, a.getType(), a.getLocation(), a.getVoyageNumber(), at);
			} else {
				HandlingType type = HandlingType.values()[random.nextInt(HandlingType.values().length)];
				Optional<String> voyage = type.requiresVoyage()
						? Optional.of(pick(random, VOYAGES).voyageNumber().idString())
						: Optional.empty();
				w.handle(id, type, pick(random, PORTS), voyage, at);
			}
			w.assertAgree(id);
		}
	}

	/** A route the schedule offers the specification when it offers one; otherwise legs cut from random voyages. */
	private static List<LegDto> itinerary(Random random, Worlds w, String id) {
		List<List<LegDto>> candidates = w.ours.tracking().routeCandidatesOf(id);
		if (!candidates.isEmpty() && random.nextDouble() < 0.7) {
			return pick(random, candidates);
		}
		List<LegDto> legs = new ArrayList<>();
		int count = 1 + random.nextInt(3);
		for (int i = 0; i < count; i++) {
			Voyage voyage = pick(random, VOYAGES);
			List<CarrierMovement> movements = voyage.schedule().carrierMovements();
			int from = random.nextInt(movements.size());
			int to = from + random.nextInt(movements.size() - from);
			Location load = movements.get(from).departureLocation();
			Location unload = movements.get(to).arrivalLocation();
			legs.add(new LegDto(voyage.voyageNumber().idString(), load.unLocode().idString(),
					unload.unLocode().idString(), movements.get(from).departureTime(), movements.get(to).arrivalTime()));
		}
		return legs;
	}

	private static <T> T pick(Random random, List<T> from) {
		return from.get(random.nextInt(from.size()));
	}

	private static String pickOther(Random random, List<String> from, String not) {
		String chosen = pick(random, from);
		while (chosen.equals(not)) {
			chosen = pick(random, from);
		}
		return chosen;
	}
}
