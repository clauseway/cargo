package org.clauseway.shipping;

// ABOUTME: Route finding: the itineraries the schedule offers a specification —
// ABOUTME: sailings chained in time, one voyage per leg, landing before the deadline.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.shipping.Hours.hour;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.clauseway.shipping.cargo.dto.LegDto;

public class RouteFindingTest {

	private static final String HONGKONG = "CNHKG";
	private static final String TOKYO = "JNTKO";
	private static final String NEWYORK = "USNYC";
	private static final String CHICAGO = "USCHI";
	private static final String DALLAS = "USDAL";
	private static final String GOTHENBURG = "SEGOT";

	/**
	 * V001 sails Hong Kong, Tokyo, New York, Hong Kong; V002 Dallas, New
	 * York, Chicago; V003 is a direct Hong Kong to New York; V004 leaves New
	 * York for Chicago at hour 9, which only V003's arrival makes; V005 goes
	 * on from Chicago to Dallas. Nothing sails to Gothenburg.
	 */
	private static Shipping booked(String origin, String destination, Instant deadline) {
		return SampleLocations.declaredIn(Shipping.empty())
				.withVoyage("V001")
				.withCarrierMovement("V001", HONGKONG, TOKYO, hour(1), hour(5))
				.withCarrierMovement("V001", TOKYO, NEWYORK, hour(6), hour(10))
				.withCarrierMovement("V001", NEWYORK, HONGKONG, hour(11), hour(14))
				.withVoyage("V002")
				.withCarrierMovement("V002", DALLAS, NEWYORK, hour(6), hour(7))
				.withCarrierMovement("V002", NEWYORK, CHICAGO, hour(12), hour(20))
				.withVoyage("V003")
				.withCarrierMovement("V003", HONGKONG, NEWYORK, hour(2), hour(9))
				.withVoyage("V004")
				.withCarrierMovement("V004", NEWYORK, CHICAGO, hour(9), hour(15))
				.withVoyage("V005")
				.withCarrierMovement("V005", CHICAGO, DALLAS, hour(21), hour(24))
				.bookCargo("ABC", origin, destination, deadline).get();
	}

	private static final LegDto V001_TO_NEWYORK = new LegDto("V001", HONGKONG, NEWYORK, hour(1), hour(10));
	private static final LegDto V003_TO_NEWYORK = new LegDto("V003", HONGKONG, NEWYORK, hour(2), hour(9));
	private static final LegDto V002_TO_CHICAGO = new LegDto("V002", NEWYORK, CHICAGO, hour(12), hour(20));
	private static final LegDto V004_TO_CHICAGO = new LegDto("V004", NEWYORK, CHICAGO, hour(9), hour(15));
	private static final LegDto V005_TO_DALLAS = new LegDto("V005", CHICAGO, DALLAS, hour(21), hour(24));

	@SafeVarargs
	private static List<List<LegDto>> routes(List<LegDto>... routes) {
		return Arrays.asList(routes);
	}

	/** A leg stays aboard its voyage past Tokyo; a connection waits for the arrival; the same voyage is never re-boarded. */
	@Test
	public void everyChainOfSailingsThatMakesTheDeadline() {
		assertThat(booked(HONGKONG, CHICAGO, hour(30)).tracking().routeCandidatesOf("ABC")).hasSize(3).containsOnlyElementsOf(routes(
				Arrays.asList(V001_TO_NEWYORK, V002_TO_CHICAGO),
				Arrays.asList(V003_TO_NEWYORK, V002_TO_CHICAGO),
				Arrays.asList(V003_TO_NEWYORK, V004_TO_CHICAGO)));
	}

	@Test
	public void theDeadlineCutsTheLateArrivals() {
		assertThat(booked(HONGKONG, CHICAGO, hour(18)).tracking().routeCandidatesOf("ABC")).containsExactlyElementsOf(routes(
				Arrays.asList(V003_TO_NEWYORK, V004_TO_CHICAGO)));
		assertThat(booked(HONGKONG, CHICAGO, hour(15)).tracking().routeCandidatesOf("ABC")).isEmpty();
	}

	@Test
	public void aRouteConnectsAsOftenAsItMust() {
		assertThat(booked(HONGKONG, DALLAS, hour(30)).tracking().routeCandidatesOf("ABC")).hasSize(3).containsOnlyElementsOf(routes(
				Arrays.asList(V001_TO_NEWYORK, V002_TO_CHICAGO, V005_TO_DALLAS),
				Arrays.asList(V003_TO_NEWYORK, V002_TO_CHICAGO, V005_TO_DALLAS),
				Arrays.asList(V003_TO_NEWYORK, V004_TO_CHICAGO, V005_TO_DALLAS)));
	}

	@Test
	public void noSailingReachesGothenburg() {
		assertThat(booked(HONGKONG, GOTHENBURG, hour(30)).tracking().routeCandidatesOf("ABC")).isEmpty();
	}

	@Test
	public void aDirectSailingIsAOneLegRoute() {
		assertThat(booked(HONGKONG, NEWYORK, hour(30)).tracking().routeCandidatesOf("ABC")).hasSize(2).containsOnlyElementsOf(routes(
				Arrays.asList(V001_TO_NEWYORK),
				Arrays.asList(V003_TO_NEWYORK)));
	}

	@Test
	public void anUnknownCargoHasNoRoutes() {
		assertThat(Shipping.empty().tracking().routeCandidatesOf("NOPE")).isEmpty();
	}
}
