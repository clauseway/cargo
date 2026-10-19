package org.clauseway.shipping;

// ABOUTME: Delivery.derivedFrom as one rule: the delivery row the cargo's rules
// ABOUTME: calculate now is the row its commands store and the tracking reads back.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.shipping.HandlingType.LOAD;
import static org.clauseway.shipping.HandlingType.RECEIVE;
import static org.clauseway.shipping.Hours.hour;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.solving.Row;
import org.clauseway.logic.unification.structures.LOption;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.shipping.cargo.Cargo;
import org.junit.Test;

public class ProgressTest {

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
						new Leg(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2)),
						new Leg(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4)))).get()
				.withHandlingEvent("CARGO1", RECEIVE, SHANGHAI, hour(0), hour(0))
				.withHandlingEvent("CARGO1", LOAD, SHANGHAI, VOYAGE, hour(1), hour(1))
				.inspectCargo("CARGO1").get();
		assertThat(s.commit().isSuccess()).isTrue();
		return store;
	}

	@Test
	public void theCalculatedRowIsTheStoredDelivery() {
		SharedDatabase store = underway();
		Transaction db = Transaction.recording(store.open("read"));
		Unifiable<RoutingStatus> routingStatus = lvar();
		Unifiable<TransportStatus> transportStatus = lvar();
		Unifiable<LOption<String>> lastKnownLocation = lvar();
		Unifiable<LOption<String>> currentVoyage = lvar();
		Unifiable<Boolean> misdirected = lvar();
		Unifiable<LOption<Instant>> eta = lvar();
		Unifiable<LOption<HandlingType>> nextType = lvar();
		Unifiable<LOption<String>> nextLocation = lvar();
		Unifiable<LOption<String>> nextVoyage = lvar();
		Unifiable<Boolean> unloadedAtDestination = lvar();
		List<Row> rows = Query.of(new Cargo(db).getProgress().calculated(lval("CARGO1"), routingStatus, transportStatus,
						lastKnownLocation, currentVoyage, misdirected, eta, nextType, nextLocation, nextVoyage, unloadedAtDestination))
				.select(routingStatus, transportStatus, lastKnownLocation, currentVoyage, misdirected, eta,
						nextType, nextLocation, nextVoyage, unloadedAtDestination).rows()
				.collect(java.util.stream.Collectors.toList());
		assertThat(rows).hasSize(1);
		Row row = rows.get(0);
		assertThat(row.get(routingStatus).get()).isEqualTo(RoutingStatus.ROUTED);
		assertThat(row.get(transportStatus).get()).isEqualTo(TransportStatus.ONBOARD_CARRIER);
		assertThat(row.get(lastKnownLocation).get().toOptional()).contains(SHANGHAI);
		assertThat(row.get(currentVoyage).get().toOptional()).contains(VOYAGE);
		assertThat(row.get(misdirected).get()).isFalse();
		assertThat(row.get(eta).get().toOptional()).contains(hour(4));
		assertThat(row.get(nextType).get().toOptional()).contains(HandlingType.UNLOAD);
		assertThat(row.get(nextLocation).get().toOptional()).contains(ROTTERDAM);
		assertThat(row.get(nextVoyage).get().toOptional()).contains(VOYAGE);
		assertThat(row.get(unloadedAtDestination).get()).isFalse();

		assertThat(Shipping.over(db).tracking().deliveryOf("CARGO1")).isEqualTo(new Delivery(
				RoutingStatus.ROUTED, TransportStatus.ONBOARD_CARRIER, Optional.of(SHANGHAI), Optional.of(VOYAGE),
				false, Optional.of(hour(4)), Optional.of(new HandlingActivity(HandlingType.UNLOAD, ROTTERDAM, Optional.of(VOYAGE))),
				false));
	}

	/** Before any handling, the partial fields are none and the next activity is the plan's first step. */
	@Test
	public void theCalculatedRowSaysAbsenceAsNone() {
		SharedDatabase store = SharedDatabase.empty();
		Shipping s = SampleLocations.declaredIn(Shipping.over(Transaction.recording(store.open("seed"))))
				.bookCargo("CARGO1", SHANGHAI, GOTHENBURG, hour(10)).get();
		assertThat(s.commit().isSuccess()).isTrue();
		Transaction db = Transaction.recording(store.open("read"));
		Unifiable<LOption<String>> lastKnownLocation = lvar();
		Unifiable<LOption<Instant>> eta = lvar();
		Unifiable<LOption<HandlingType>> nextType = lvar();
		Unifiable<Boolean> misdirected = lvar();
		List<Row> rows = Query.of(new Cargo(db).getProgress().calculated(lval("CARGO1"), lvar(), lvar(),
						lastKnownLocation, lvar(), misdirected, eta, nextType, lvar(), lvar(), lvar()))
				.select(lastKnownLocation, misdirected, eta, nextType).rows()
				.collect(java.util.stream.Collectors.toList());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).get(lastKnownLocation).get().toOptional()).isEmpty();
		assertThat(rows.get(0).get(misdirected).get()).isFalse();
		assertThat(rows.get(0).get(eta).get().toOptional()).isEmpty();
		assertThat(rows.get(0).get(nextType).get().toOptional()).isEmpty();
	}
}
