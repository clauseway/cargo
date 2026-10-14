package org.clauseway.cargo;

// ABOUTME: dddsample's RouteSpecificationTest: one itinerary against four
// ABOUTME: specifications — origin, destination and deadline each decide.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.Hours.hour;
import static org.clauseway.cargo.RoutingStatus.MISROUTED;
import static org.clauseway.cargo.RoutingStatus.NOT_ROUTED;
import static org.clauseway.cargo.RoutingStatus.ROUTED;

import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;

public class RouteSpecificationTest {

	private static final String HONGKONG = "CNHKG";
	private static final String TOKYO = "JNTKO";
	private static final String NEWYORK = "USNYC";
	private static final String DALLAS = "USDAL";
	private static final String CHICAGO = "USCHI";
	private static final String HANGZHOU = "CNHGH";

	/** Hong Kong to New York on V001, New York to Chicago on V002, arriving at hour 20. */
	private static Shipping routed(String origin, String destination, Instant deadline) {
		return Shipping.empty()
				.withVoyage("V001")
				.withCarrierMovement("V001", HONGKONG, TOKYO, hour(1), hour(5))
				.withCarrierMovement("V001", TOKYO, NEWYORK, hour(6), hour(10))
				.withCarrierMovement("V001", NEWYORK, HONGKONG, hour(11), hour(14))
				.withVoyage("V002")
				.withCarrierMovement("V002", DALLAS, NEWYORK, hour(6), hour(7))
				.withCarrierMovement("V002", NEWYORK, CHICAGO, hour(12), hour(20))
				.bookCargo("ABC", origin, destination, deadline).get()
				.assignToRoute("ABC", Arrays.asList(
						new Leg("V001", HONGKONG, NEWYORK, hour(1), hour(10)),
						new Leg("V002", NEWYORK, CHICAGO, hour(12), hour(20))));
	}

	@Test
	public void isSatisfiedBy_Success() {
		assertThat(routed(HONGKONG, CHICAGO, hour(30)).tracking().routingStatusOf("ABC")).isEqualTo(ROUTED);
	}

	@Test
	public void isSatisfiedBy_WrongOrigin() {
		assertThat(routed(HANGZHOU, CHICAGO, hour(30)).tracking().routingStatusOf("ABC")).isEqualTo(MISROUTED);
	}

	@Test
	public void isSatisfiedBy_WrongDestination() {
		assertThat(routed(HONGKONG, DALLAS, hour(30)).tracking().routingStatusOf("ABC")).isEqualTo(MISROUTED);
	}

	@Test
	public void isSatisfiedBy_MissedDeadline() {
		assertThat(routed(HONGKONG, CHICAGO, hour(15)).tracking().routingStatusOf("ABC")).isEqualTo(MISROUTED);
	}

	@Test
	public void aCargoWithoutAnItineraryIsNotRouted() {
		Shipping booked = Shipping.empty().bookCargo("ABC", HONGKONG, CHICAGO, hour(30)).get();
		assertThat(booked.tracking().routingStatusOf("ABC")).isEqualTo(NOT_ROUTED);
	}
}
