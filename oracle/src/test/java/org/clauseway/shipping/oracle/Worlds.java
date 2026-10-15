package org.clauseway.shipping.oracle;

// ABOUTME: Both implementations driven by one script, and their deliveries
// ABOUTME: compared field by field after every step, the known divergences masked by name.

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.clauseway.shipping.HandlingEvent;
import org.clauseway.shipping.HandlingType;
import org.clauseway.shipping.Leg;

final class Worlds {

	final Theirs theirs = new Theirs();
	final Ours ours = new Ours();
	private final List<String> script = new ArrayList<>();

	/** What the comparisons so far reached, so a green run can say what it covered. */
	static final class Coverage {
		int comparisons;
		int masked;
		int routed;
		int misrouted;
		int misdirected;
		int claimed;

		@Override
		public String toString() {
			return "comparisons " + comparisons + ", masked " + masked + ", routed " + routed + ", misrouted " + misrouted
					+ ", misdirected " + misdirected + ", claimed " + claimed;
		}
	}

	static final Coverage coverage = new Coverage();

	void book(String trackingId, String origin, String destination, Instant deadline) {
		script.add("book " + trackingId + " " + origin + " -> " + destination + " by " + deadline);
		theirs.book(trackingId, origin, destination, deadline);
		ours.book(trackingId, origin, destination, deadline);
	}

	void assignToRoute(String trackingId, List<Leg> legs) {
		script.add("route " + trackingId + " " + legs);
		theirs.assignToRoute(trackingId, legs);
		ours.assignToRoute(trackingId, legs);
	}

	void specifyNewRoute(String trackingId, String origin, String destination, Instant deadline) {
		script.add("respecify " + trackingId + " " + origin + " -> " + destination + " by " + deadline);
		theirs.specifyNewRoute(trackingId, origin, destination, deadline);
		ours.specifyNewRoute(trackingId, origin, destination, deadline);
	}

	void handle(String trackingId, HandlingType type, String location, Optional<String> voyageNumber,
			Instant completion) {
		script.add("handle " + trackingId + " " + type + " " + location + voyageNumber.map(v -> " on " + v).orElse("")
				+ " at " + completion);
		theirs.handle(trackingId, type, location, voyageNumber, completion, completion);
		ours.handle(trackingId, type, location, voyageNumber, completion, completion);
	}

	/**
	 * The two deliveries agree on every field but the ones a known divergence
	 * (ledger entry 9) covers for this cargo's state: those are masked, and
	 * the mask names its reason in the script on failure.
	 */
	void assertAgree(String trackingId) {
		Snapshot t = theirs.delivery(trackingId);
		Snapshot o = ours.delivery(trackingId);
		List<String> masked = new ArrayList<>();
		List<Leg> legs = ours.tracking().itineraryOf(trackingId);
		Optional<HandlingEvent> last = ours.tracking().lastEventOf(trackingId);

		if (legs.isEmpty() && last.isPresent()) {
			masked.add("routingStatus: unrouted cargo inspected after a handling reads MISROUTED there, NOT_ROUTED here");
			t = new Snapshot(o.routingStatus(), t.transportStatus(), t.lastKnownLocation(), t.currentVoyage(),
					t.misdirected(), t.eta(), t.nextExpectedActivity(), t.unloadedAtDestination());
		}
		if (last.isPresent() && callsTwice(legs, last.get())) {
			masked.add("nextExpectedActivity: after a LOAD or UNLOAD at a port the itinerary calls at twice,"
					+ " the first such leg there, the leg the voyage matched here");
			t = new Snapshot(t.routingStatus(), t.transportStatus(), t.lastKnownLocation(), t.currentVoyage(),
					t.misdirected(), t.eta(), o.nextExpectedActivity(), t.unloadedAtDestination());
		}
		coverage.comparisons++;
		coverage.masked += masked.isEmpty() ? 0 : 1;
		coverage.routed += o.routingStatus().equals("ROUTED") ? 1 : 0;
		coverage.misrouted += o.routingStatus().equals("MISROUTED") ? 1 : 0;
		coverage.misdirected += o.misdirected() ? 1 : 0;
		coverage.claimed += o.transportStatus().equals("CLAIMED") ? 1 : 0;
		assertThat(o).as("after:\n  " + String.join("\n  ", script)
				+ (masked.isEmpty() ? "" : "\nmasked:\n  " + String.join("\n  ", masked))
				+ "\ntheirs: " + t + "\nours:   " + o).isEqualTo(t);
	}

	private static boolean callsTwice(List<Leg> legs, HandlingEvent last) {
		String port = last.getLocation();
		switch (last.getType()) {
		case LOAD:
			return legs.stream().filter(l -> l.getLoadLocation().equals(port)).count() > 1;
		case UNLOAD:
			return legs.stream().filter(l -> l.getUnloadLocation().equals(port)).count() > 1;
		default:
			return false;
		}
	}
}
