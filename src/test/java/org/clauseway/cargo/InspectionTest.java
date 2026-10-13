package org.clauseway.cargo;

// ABOUTME: dddsample's CargoInspectionService: after a handling, the cargo is
// ABOUTME: inspected and the notices its derived state warrants are raised.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static org.clauseway.cargo.HandlingType.UNLOAD;
import static org.clauseway.cargo.Hours.hour;
import static org.clauseway.cargo.Notice.ARRIVED;
import static org.clauseway.cargo.Notice.MISDIRECTED;

import java.util.Arrays;
import org.junit.Test;

public class InspectionTest {

	private static final String SHANGHAI = "CNSHA";
	private static final String ROTTERDAM = "NLRTM";
	private static final String GOTHENBURG = "SEGOT";
	private static final String HELSINKI = "FIHEL";
	private static final String VOYAGE = "0123";
	private static final String WRONG_VOYAGE = "666";

	private static Shipping routed() {
		return Shipping.empty()
				.withVoyage(VOYAGE)
				.withCarrierMovement(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2))
				.withCarrierMovement(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4))
				.withVoyage(WRONG_VOYAGE)
				.withCarrierMovement(WRONG_VOYAGE, SHANGHAI, HELSINKI, hour(1), hour(2))
				.bookCargo("CARGO1", SHANGHAI, GOTHENBURG, hour(10)).get()
				.assignToRoute("CARGO1", Arrays.asList(
						new Leg(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2)),
						new Leg(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4))));
	}

	@Test
	public void aCargoOnTrackRaisesNothing() {
		assertThat(routed().inspect("CARGO1")).isEmpty();
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, VOYAGE, hour(1), hour(1))
				.inspect("CARGO1")).isEmpty();
	}

	@Test
	public void loadingOntoTheWrongVoyageRaisesMisdirected() {
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, WRONG_VOYAGE, hour(1), hour(1))
				.inspect("CARGO1")).containsExactly(MISDIRECTED);
	}

	@Test
	public void unloadingAtTheDestinationRaisesArrived() {
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, VOYAGE, hour(1), hour(1))
				.withHandlingEvent("CARGO1", UNLOAD, ROTTERDAM, VOYAGE, hour(2), hour(2))
				.withHandlingEvent("CARGO1", LOAD, ROTTERDAM, VOYAGE, hour(3), hour(3))
				.withHandlingEvent("CARGO1", UNLOAD, GOTHENBURG, VOYAGE, hour(4), hour(4))
				.inspect("CARGO1")).containsExactly(ARRIVED);
	}

	/** Arriving off the wrong ship is both: dddsample raises both events too. */
	@Test
	public void arrivingOffTheWrongVoyageRaisesBoth() {
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, WRONG_VOYAGE, hour(1), hour(1))
				.withHandlingEvent("CARGO1", UNLOAD, GOTHENBURG, WRONG_VOYAGE, hour(4), hour(4))
				.inspect("CARGO1")).containsExactlyInAnyOrder(MISDIRECTED, ARRIVED);
	}
}
