package org.clauseway.cargo;

// ABOUTME: dddsample's HandlingEventFactoryTest and the HandlingEvent
// ABOUTME: constructor guards: a handling registers only against known facts.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.HandlingType.CLAIM;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static org.clauseway.cargo.Hours.hour;
import java.util.Optional;
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
		assertThat(world().tracking().registrationDenials("ABC", LOAD, "NOEXIST", Optional.of("CM001"))).containsExactly("unknown location");
		assertThat(world().registerHandling("ABC", LOAD, "NOEXIST", Optional.of("CM001"), hour(1), hour(2)).isFailure()).isTrue();
	}

	@Test
	public void createHandlingEventUnknownCarrierMovement() {
		assertThat(world().tracking().registrationDenials("ABC", LOAD, STOCKHOLM, Optional.of("NOEXIST"))).containsExactly("unknown voyage");
	}

	@Test
	public void createHandlingEventUnknownTrackingId() {
		assertThat(world().tracking().registrationDenials("NOEXIST", LOAD, STOCKHOLM, Optional.of("CM001"))).containsExactly("unknown cargo");
	}

	@Test
	public void aLoadNeedsAVoyage() {
		assertThat(world().tracking().registrationDenials("ABC", LOAD, STOCKHOLM, Optional.empty())).containsExactly("voyage required");
	}

	@Test
	public void aReceiptMustNotNameAVoyage() {
		assertThat(world().tracking().registrationDenials("ABC", RECEIVE, STOCKHOLM, Optional.of("CM001"))).containsExactly("voyage prohibited");
	}

	@Test
	public void everyViolatedRuleIsNamed() {
		assertThat(world().tracking().registrationDenials("NOEXIST", LOAD, "NOEXIST", Optional.empty()))
				.containsExactlyInAnyOrder("unknown cargo", "unknown location", "voyage required");
	}
}
