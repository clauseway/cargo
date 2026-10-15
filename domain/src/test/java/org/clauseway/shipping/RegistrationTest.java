package org.clauseway.shipping;

// ABOUTME: dddsample's HandlingEventFactoryTest and the HandlingEvent
// ABOUTME: constructor guards: a handling registers only against known facts.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.shipping.HandlingType.CLAIM;
import static org.clauseway.shipping.HandlingType.LOAD;
import static org.clauseway.shipping.HandlingType.RECEIVE;
import static org.clauseway.shipping.Hours.hour;
import static org.clauseway.shipping.Refused.denials;
import java.util.Optional;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.Cargo;
import org.clauseway.shipping.cargo.Itinerary;
import org.clauseway.shipping.cargo.Routing;
import org.clauseway.shipping.handling.History;
import org.clauseway.shipping.location.Locations;
import org.clauseway.shipping.voyage.Voyages;
import org.junit.Test;

public class RegistrationTest {

	private static final String STOCKHOLM = "SESTO";
	private static final String HELSINKI = "FIHEL";

	private static Shipping world() {
		return Shipping.empty()
				.withLocation(STOCKHOLM, "Stockholm")
				.withLocation(HELSINKI, "Helsinki")
				.withVoyage("CM001")
				.withCarrierMovement("CM001", STOCKHOLM, HELSINKI, hour(1), hour(5))
				.bookCargo("ABC", STOCKHOLM, HELSINKI, hour(100)).get();
	}

	/** The aggregate guards its own writes: registering against an unknown cargo is refused with no facade in between. */
	@Test
	public void theHistoryRefusesAnUnknownCargoWithoutTheFacade() {
		Transaction db = Transaction.recording(SharedDatabase.empty().open("direct"));
		Locations locations = new Locations(db);
		Voyages voyages = new Voyages(db);
		Itinerary itinerary = new Itinerary(db);
		Cargo cargo = new Cargo(db, itinerary, new Routing(db, itinerary, voyages, locations));
		History history = new History(db, cargo, locations, voyages);
		assertThat(history.register("ABC", RECEIVE, STOCKHOLM, Optional.empty(), hour(1), hour(2)).getCause())
				.hasMessageContaining("unknown cargo");
	}

	@Test
	public void createHandlingEventWithCarrierMovement() {
		Shipping s = world().registerHandling("ABC", LOAD, STOCKHOLM, Optional.of("CM001"), hour(1), hour(2)).get();
		assertThat(s.tracking().handlingHistoryOf("ABC"))
				.containsExactly(new HandlingEvent(LOAD, STOCKHOLM, Optional.of("CM001"), hour(1)));
	}

	@Test
	public void createHandlingEventWithoutCarrierMovement() {
		Shipping s = world().registerHandling("ABC", CLAIM, STOCKHOLM, Optional.empty(), hour(1), hour(2)).get();
		assertThat(s.tracking().handlingHistoryOf("ABC"))
				.containsExactly(new HandlingEvent(CLAIM, STOCKHOLM, Optional.empty(), hour(1)));
	}

	@Test
	public void createHandlingEventUnknownLocation() {
		assertThat(denials(world().registerHandling("ABC", LOAD, "NOEXIST", Optional.of("CM001"), hour(1), hour(2)))).containsExactly("unknown location");
	}

	@Test
	public void createHandlingEventUnknownCarrierMovement() {
		assertThat(denials(world().registerHandling("ABC", LOAD, STOCKHOLM, Optional.of("NOEXIST"), hour(1), hour(2)))).containsExactly("unknown voyage");
	}

	@Test
	public void createHandlingEventUnknownTrackingId() {
		assertThat(denials(world().registerHandling("NOEXIST", LOAD, STOCKHOLM, Optional.of("CM001"), hour(1), hour(2)))).containsExactly("unknown cargo");
	}

	@Test
	public void aLoadNeedsAVoyage() {
		assertThat(denials(world().registerHandling("ABC", LOAD, STOCKHOLM, Optional.empty(), hour(1), hour(2)))).containsExactly("voyage required");
	}

	@Test
	public void aReceiptMustNotNameAVoyage() {
		assertThat(denials(world().registerHandling("ABC", RECEIVE, STOCKHOLM, Optional.of("CM001"), hour(1), hour(2)))).containsExactly("voyage prohibited");
	}

	@Test
	public void everyViolatedRuleIsNamed() {
		assertThat(denials(world().registerHandling("NOEXIST", LOAD, "NOEXIST", Optional.empty(), hour(1), hour(2))))
				.containsExactlyInAnyOrder("unknown cargo", "unknown location", "voyage required");
	}
}
