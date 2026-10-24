package org.clauseway.shipping;

// ABOUTME: dddsample's CargoInspectionService: after a handling, the cargo is
// ABOUTME: inspected and the notices its derived state warrants are raised.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.shipping.handling.HandlingType.LOAD;
import static org.clauseway.shipping.handling.HandlingType.RECEIVE;
import static org.clauseway.shipping.handling.HandlingType.UNLOAD;
import static org.clauseway.shipping.Hours.hour;
import static org.clauseway.shipping.cargo.Notice.ARRIVED;
import static org.clauseway.shipping.cargo.Notice.MISDIRECTED;

import java.util.Arrays;
import org.junit.Test;
import org.clauseway.shipping.cargo.dto.LegDto;

public class InspectionTest {

	private static final String SHANGHAI = "CNSHA";
	private static final String ROTTERDAM = "NLRTM";
	private static final String GOTHENBURG = "SEGOT";
	private static final String HELSINKI = "FIHEL";
	private static final String VOYAGE = "0123";
	private static final String WRONG_VOYAGE = "666";

	private static Shipping routed() {
		return SampleLocations.declaredIn(Shipping.empty())
				.withVoyage(VOYAGE)
				.withCarrierMovement(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2))
				.withCarrierMovement(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4))
				.withVoyage(WRONG_VOYAGE)
				.withCarrierMovement(WRONG_VOYAGE, SHANGHAI, HELSINKI, hour(1), hour(2))
				.bookCargo("CARGO1", SHANGHAI, GOTHENBURG, hour(10)).get()
				.assignToRoute("CARGO1", Arrays.asList(
						new LegDto(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2)),
						new LegDto(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4)))).get();
	}

	/** The handling is registered on its own side; the cargo learns of it when inspected, as CargoInspectionService does. */
	@Test
	public void aHandlingReachesTheDeliveryWhenTheCargoIsInspected() {
		Shipping received = routed().withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0));
		assertThat(received.tracking().deliveryOf("CARGO1").getLastKnownLocation()).isEmpty();
		assertThat(received.inspectCargo("CARGO1").get().tracking().deliveryOf("CARGO1").getLastKnownLocation())
				.contains(SHANGHAI);
	}

	@Test
	public void aCargoOnTrackRaisesNothing() {
		assertThat(routed().tracking().noticesOf("CARGO1")).isEmpty();
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, VOYAGE, hour(1), hour(1))
				.inspectCargo("CARGO1").get().tracking().noticesOf("CARGO1")).isEmpty();
	}

	@Test
	public void loadingOntoTheWrongVoyageRaisesMisdirected() {
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, WRONG_VOYAGE, hour(1), hour(1))
				.inspectCargo("CARGO1").get().tracking().noticesOf("CARGO1")).containsExactly(MISDIRECTED);
	}

	@Test
	public void unloadingAtTheDestinationRaisesArrived() {
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, VOYAGE, hour(1), hour(1))
				.withHandlingEvent("CARGO1", UNLOAD, ROTTERDAM, VOYAGE, hour(2), hour(2))
				.withHandlingEvent("CARGO1", LOAD, ROTTERDAM, VOYAGE, hour(3), hour(3))
				.withHandlingEvent("CARGO1", UNLOAD, GOTHENBURG, VOYAGE, hour(4), hour(4))
				.inspectCargo("CARGO1").get().tracking().noticesOf("CARGO1")).containsExactly(ARRIVED);
	}

	/** Arriving off the wrong ship is both: dddsample raises both events too. */
	@Test
	public void arrivingOffTheWrongVoyageRaisesBoth() {
		assertThat(routed()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, WRONG_VOYAGE, hour(1), hour(1))
				.withHandlingEvent("CARGO1", UNLOAD, GOTHENBURG, WRONG_VOYAGE, hour(4), hour(4))
				.inspectCargo("CARGO1").get().tracking().noticesOf("CARGO1")).containsExactlyInAnyOrder(MISDIRECTED, ARRIVED);
	}
}
