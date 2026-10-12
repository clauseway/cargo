package org.clauseway.cargo;

// ABOUTME: dddsample's HandlingEventFactoryTest and the HandlingEvent
// ABOUTME: constructor guards: a handling registers only against known facts.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.HandlingType.CLAIM;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static org.clauseway.cargo.Hours.hour;
import static org.clauseway.cargo.Schema.NO_VOYAGE;

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
		Shipping s = world().registerHandling("ABC", LOAD, STOCKHOLM, "CM001", hour(1), hour(2)).get();
		assertThat(s.handlingHistoryOf("ABC"))
				.containsExactly(new HandlingEvent(LOAD, STOCKHOLM, "CM001", hour(1)));
	}

	@Test
	public void createHandlingEventWithoutCarrierMovement() {
		Shipping s = world().registerHandling("ABC", CLAIM, STOCKHOLM, NO_VOYAGE, hour(1), hour(2)).get();
		assertThat(s.handlingHistoryOf("ABC"))
				.containsExactly(new HandlingEvent(CLAIM, STOCKHOLM, NO_VOYAGE, hour(1)));
	}

	@Test
	public void createHandlingEventUnknownLocation() {
		assertThat(world().registrationDenials("ABC", LOAD, "NOEXIST", "CM001")).containsExactly("unknown location");
		assertThat(world().registerHandling("ABC", LOAD, "NOEXIST", "CM001", hour(1), hour(2)).isFailure()).isTrue();
	}

	@Test
	public void createHandlingEventUnknownCarrierMovement() {
		assertThat(world().registrationDenials("ABC", LOAD, STOCKHOLM, "NOEXIST")).containsExactly("unknown voyage");
	}

	@Test
	public void createHandlingEventUnknownTrackingId() {
		assertThat(world().registrationDenials("NOEXIST", LOAD, STOCKHOLM, "CM001")).containsExactly("unknown cargo");
	}

	@Test
	public void aLoadNeedsAVoyage() {
		assertThat(world().registrationDenials("ABC", LOAD, STOCKHOLM, NO_VOYAGE)).containsExactly("voyage required");
	}

	@Test
	public void aReceiptMustNotNameAVoyage() {
		assertThat(world().registrationDenials("ABC", RECEIVE, STOCKHOLM, "CM001")).containsExactly("voyage prohibited");
	}

	@Test
	public void everyViolatedRuleIsNamed() {
		assertThat(world().registrationDenials("NOEXIST", LOAD, "NOEXIST", NO_VOYAGE))
				.containsExactlyInAnyOrder("unknown cargo", "unknown location", "voyage required");
	}
}
