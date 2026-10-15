package org.clauseway.cargo.oracle;

// ABOUTME: dddsample's lifecycle scenario run through both implementations,
// ABOUTME: the deliveries compared after every step rather than asserted by hand.

import static org.clauseway.cargo.HandlingType.CLAIM;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static org.clauseway.cargo.HandlingType.UNLOAD;
import static se.citerus.dddsample.application.util.DateUtils.toDate;

import java.util.List;
import java.util.Optional;
import org.clauseway.cargo.Leg;
import org.junit.Test;

public class LifecycleScenarioOracleTest {

	private static final String HONGKONG = "CNHKG";
	private static final String STOCKHOLM = "SESTO";
	private static final String CHICAGO = "USCHI";
	private static final String TOKYO = "JNTKO";
	private static final String HAMBURG = "DEHAM";
	private static final String NEWYORK = "USNYC";

	@Test
	public void cargoFromHongkongToStockholm() {
		Worlds w = new Worlds();
		String id = "ABC";

		w.book(id, HONGKONG, STOCKHOLM, toDate("2009-03-18"));
		w.assertAgree(id);

		w.assignToRoute(id, List.of(
				new Leg("V100", HONGKONG, NEWYORK, toDate("2009-03-03"), toDate("2009-03-09")),
				new Leg("V200", NEWYORK, CHICAGO, toDate("2009-03-10"), toDate("2009-03-14")),
				new Leg("V200", CHICAGO, STOCKHOLM, toDate("2009-03-07"), toDate("2009-03-11"))));
		w.assertAgree(id);

		w.handle(id, RECEIVE, HONGKONG, Optional.empty(), toDate("2009-03-01"));
		w.assertAgree(id);
		w.handle(id, LOAD, HONGKONG, Optional.of("V100"), toDate("2009-03-03"));
		w.assertAgree(id);
		w.handle(id, UNLOAD, TOKYO, Optional.of("V100"), toDate("2009-03-05"));
		w.assertAgree(id);

		w.specifyNewRoute(id, TOKYO, STOCKHOLM, toDate("2009-03-18"));
		w.assertAgree(id);
		w.assignToRoute(id, List.of(
				new Leg("V300", TOKYO, HAMBURG, toDate("2009-03-08"), toDate("2009-03-12")),
				new Leg("V400", HAMBURG, STOCKHOLM, toDate("2009-03-14"), toDate("2009-03-15"))));
		w.assertAgree(id);

		w.handle(id, LOAD, TOKYO, Optional.of("V300"), toDate("2009-03-08"));
		w.assertAgree(id);
		w.handle(id, UNLOAD, HAMBURG, Optional.of("V300"), toDate("2009-03-12"));
		w.assertAgree(id);
		w.handle(id, LOAD, HAMBURG, Optional.of("V400"), toDate("2009-03-14"));
		w.assertAgree(id);
		w.handle(id, UNLOAD, STOCKHOLM, Optional.of("V400"), toDate("2009-03-15"));
		w.assertAgree(id);
		w.handle(id, CLAIM, STOCKHOLM, Optional.empty(), toDate("2009-03-16"));
		w.assertAgree(id);
	}
}
