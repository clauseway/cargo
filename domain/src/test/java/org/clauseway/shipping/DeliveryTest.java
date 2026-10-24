package org.clauseway.shipping;

// ABOUTME: Delivery.derivedFrom as one rule: the delivery row the cargo's rules
// ABOUTME: derive now is the row its commands store and the tracking reads back.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.shipping.handling.HandlingType.LOAD;
import static org.clauseway.shipping.handling.HandlingType.RECEIVE;
import static org.clauseway.shipping.Hours.hour;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.clauseway.logic.solving.Query;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.Delivery;
import org.junit.Test;
import org.clauseway.shipping.cargo.dto.DeliveryDto;
import org.clauseway.shipping.cargo.dto.HandlingActivityDto;
import org.clauseway.shipping.handling.HandlingType;
import org.clauseway.shipping.cargo.dto.LegDto;
import org.clauseway.shipping.cargo.RoutingStatus;
import org.clauseway.shipping.cargo.TransportStatus;

public class DeliveryTest {

	private static final String SHANGHAI = "CNSHA";
	private static final String ROTTERDAM = "NLRTM";
	private static final String GOTHENBURG = "SEGOT";
	private static final String VOYAGE = "0123";

	/** Routed Shanghai to Gothenburg, received and loaded at Shanghai, inspected. */
	private static SharedDatabase underway() {
		SharedDatabase store = SharedDatabase.empty();
		Shipping s = SampleLocations.declaredIn(Shipping.over(Transaction.recording(store.open("seed"))))
				.withVoyage(VOYAGE)
				.withCarrierMovement(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2))
				.withCarrierMovement(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4))
				.bookCargo("CARGO1", SHANGHAI, GOTHENBURG, hour(10)).get()
				.assignToRoute("CARGO1", Arrays.asList(
						new LegDto(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2)),
						new LegDto(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4)))).get()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, VOYAGE, hour(1), hour(1))
				.inspectCargo("CARGO1").get();
		assertThat(s.commit().isSuccess()).isTrue();
		return store;
	}

	@Test
	public void theDerivedRowIsTheStoredDelivery() {
		SharedDatabase store = underway();
		Transaction db = Transaction.recording(store.open("read"));
		DeliveryDto underway = new DeliveryDto(
				RoutingStatus.ROUTED, TransportStatus.ONBOARD_CARRIER, Optional.of(SHANGHAI), Optional.of(VOYAGE),
				false, Optional.of(hour(4)), Optional.of(new HandlingActivityDto(HandlingType.UNLOAD, ROTTERDAM, Optional.of(VOYAGE))),
				false);
		assertThat(derived(db, "CARGO1")).containsExactly(underway);
		assertThat(Shipping.over(db).tracking().deliveryOf("CARGO1")).isEqualTo(underway);
	}

	/** The rows the derived delivery answers for the cargo, as values. */
	private static List<DeliveryDto> derived(Transaction db, String trackingId) {
		Delivery delivery = Delivery.fresh(lval(trackingId));
		return Query.of(delivery.derived(db)).select(delivery).stream().collect(Collectors.toList());
	}

	/** Booked and not routed: nothing is known, so every partial field is empty and both flags are off. */
	@Test
	public void theDerivedRowSaysAbsenceAsEmpty() {
		SharedDatabase store = SharedDatabase.empty();
		Shipping s = SampleLocations.declaredIn(Shipping.over(Transaction.recording(store.open("seed"))))
				.bookCargo("CARGO1", SHANGHAI, GOTHENBURG, hour(10)).get();
		assertThat(s.commit().isSuccess()).isTrue();
		Transaction db = Transaction.recording(store.open("read"));
		assertThat(derived(db, "CARGO1")).containsExactly(new DeliveryDto(
				RoutingStatus.NOT_ROUTED, TransportStatus.NOT_RECEIVED, Optional.empty(), Optional.empty(),
				false, Optional.empty(), Optional.empty(), false));
	}
}
