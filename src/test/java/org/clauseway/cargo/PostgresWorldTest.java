package org.clauseway.cargo;

// ABOUTME: The shipping domain over PostgreSQL: the same facade on the rented
// ABOUTME: serializable tier — rules over SQL regions, and SSI as the certifier.

import static org.assertj.core.api.Assertions.assertThat;
import static org.clauseway.cargo.HandlingType.LOAD;
import static org.clauseway.cargo.HandlingType.RECEIVE;
import static org.clauseway.cargo.HandlingType.UNLOAD;
import static org.clauseway.cargo.Hours.hour;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.pldb.transaction.Transaction;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresWorldTest {

	private static final String SHANGHAI = "CNSHA";
	private static final String ROTTERDAM = "NLRTM";
	private static final String GOTHENBURG = "SEGOT";
	private static final String HELSINKI = "FIHEL";
	private static final String VOYAGE = "0123";
	private static final String WRONG_VOYAGE = "666";

	private static PostgreSQLContainer<?> postgres;
	private final List<Shipping> opened = new ArrayList<>();

	@BeforeClass
	public static void startPostgres() {
		Assume.assumeTrue("Docker unavailable — the Postgres world needs it",
				DockerClientFactory.instance().isDockerAvailable());
		postgres = new PostgreSQLContainer<>("postgres:16-alpine");
		postgres.start();
	}

	@AfterClass
	public static void stopPostgres() {
		if (postgres != null) {
			postgres.stop();
		}
	}

	/** The schema by pldb's convention: table = relation, column = property; unquoted, so case folds. */
	@Before
	public void resetSchema() throws SQLException {
		try (Connection admin = connect(); Statement ddl = admin.createStatement()) {
			for (String table : new String[]{"location", "voyage", "carrierMovement", "cargo",
					"routeSpecification", "leg", "handlingEvent"}) {
				ddl.execute("DROP TABLE IF EXISTS " + table);
			}
			ddl.execute("CREATE TABLE location(unlocode VARCHAR(5) NOT NULL, name VARCHAR(64) NOT NULL)");
			ddl.execute("CREATE TABLE voyage(voyageNumber VARCHAR(16) NOT NULL)");
			ddl.execute("CREATE TABLE carrierMovement(voyageNumber VARCHAR(16) NOT NULL,"
					+ " departureLocation VARCHAR(5) NOT NULL, arrivalLocation VARCHAR(5) NOT NULL,"
					+ " departureTime TIMESTAMP NOT NULL, arrivalTime TIMESTAMP NOT NULL)");
			ddl.execute("CREATE TABLE cargo(trackingId VARCHAR(16) NOT NULL, origin VARCHAR(5) NOT NULL)");
			ddl.execute("CREATE TABLE routeSpecification(trackingId VARCHAR(16) NOT NULL, origin VARCHAR(5) NOT NULL,"
					+ " destination VARCHAR(5) NOT NULL, arrivalDeadline TIMESTAMP NOT NULL)");
			ddl.execute("CREATE TABLE leg(trackingId VARCHAR(16) NOT NULL, index INT NOT NULL,"
					+ " voyageNumber VARCHAR(16) NOT NULL, loadLocation VARCHAR(5) NOT NULL,"
					+ " unloadLocation VARCHAR(5) NOT NULL, loadTime TIMESTAMP NOT NULL, unloadTime TIMESTAMP NOT NULL)");
			ddl.execute("CREATE TABLE handlingEvent(trackingId VARCHAR(16) NOT NULL, type VARCHAR(16) NOT NULL,"
					+ " location VARCHAR(5) NOT NULL, voyageNumber VARCHAR(16),"
					+ " completionTime TIMESTAMP NOT NULL, registrationTime TIMESTAMP NOT NULL)");
			admin.commit();
		}
	}

	private static Connection connect() throws SQLException {
		Connection connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		connection.setAutoCommit(false);
		return connection;
	}

	/** Every transaction this test opened is closed after it: an open one holds its locks. */
	@After
	public void closeTransactions() throws Exception {
		for (Shipping shipping : opened) {
			shipping.close();
		}
	}

	/** One transaction on its own connection: the backend's SSI certifies its reads. */
	private Shipping open(String id) throws SQLException {
		Shipping shipping = Shipping.over(Transaction.over(Schema.onSql(SerializableSource.postgres(id, connect()))));
		opened.add(shipping);
		return shipping;
	}

	private void seed() throws SQLException {
		assertThat(open("seed")
				.withLocation(SHANGHAI, "Shanghai")
				.withLocation(ROTTERDAM, "Rotterdam")
				.withLocation(GOTHENBURG, "Gothenburg")
				.withLocation(HELSINKI, "Helsinki")
				.withVoyage(VOYAGE)
				.withCarrierMovement(VOYAGE, SHANGHAI, ROTTERDAM, hour(1), hour(2))
				.withCarrierMovement(VOYAGE, ROTTERDAM, GOTHENBURG, hour(3), hour(4))
				.withVoyage(WRONG_VOYAGE)
				.withCarrierMovement(WRONG_VOYAGE, SHANGHAI, HELSINKI, hour(1), hour(2))
				.bookCargo("CARGO1", SHANGHAI, GOTHENBURG, hour(10)).get()
				.commit().isSuccess()).isTrue();
	}

	/** Booking, route finding, assignment, handling, delivery and inspection, each a committed transaction. */
	@Test
	public void theShippingStoryOverPostgres() throws SQLException {
		seed();

		Shipping routing = open("route");
		List<List<Leg>> candidates = routing.tracking().routeCandidatesOf("CARGO1");
		assertThat(candidates).hasSize(1);
		assertThat(routing.assignToRoute("CARGO1", candidates.get(0)).commit().isSuccess()).isTrue();
		assertThat(open("check").tracking().routingStatusOf("CARGO1")).isEqualTo(RoutingStatus.ROUTED);

		assertThat(open("handle")
				.registerHandling("CARGO1", RECEIVE, SHANGHAI, Optional.empty(), hour(0), hour(0)).get()
				.registerHandling("CARGO1", LOAD, SHANGHAI, Optional.of(VOYAGE), hour(1), hour(1)).get()
				.registerHandling("CARGO1", UNLOAD, GOTHENBURG, Optional.of(VOYAGE), hour(4), hour(4)).get()
				.commit().isSuccess()).isTrue();

		Shipping arrived = open("read");
		assertThat(arrived.tracking().deliveryOf("CARGO1")).isEqualTo(new Delivery(
				RoutingStatus.ROUTED, TransportStatus.IN_PORT, Optional.of(GOTHENBURG), Optional.empty(),
				false, Optional.of(hour(4)),
				Optional.of(new HandlingActivity(HandlingType.CLAIM, GOTHENBURG, Optional.empty())), true));
		assertThat(arrived.tracking().inspect("CARGO1")).containsExactly(Notice.ARRIVED);

		assertThat(open("misdirect")
				.registerHandling("CARGO1", LOAD, GOTHENBURG, Optional.of(WRONG_VOYAGE), hour(5), hour(5))
				.get().commit().isSuccess()).isTrue();
		Shipping astray = open("inspect");
		assertThat(astray.tracking().inspect("CARGO1")).containsExactly(Notice.MISDIRECTED);
		assertThat(astray.tracking().deliveryOf("CARGO1").getCurrentVoyage()).contains(WRONG_VOYAGE);
	}

	/**
	 * Two re-specifications: the second one's retraction meets a row the
	 * first deleted, and SSI refuses it in its own dialect, received as a
	 * Conflict.
	 */
	@Test
	public void twoReroutingsTheSecondIsRefusedBySsi() throws SQLException {
		seed();
		Shipping toRotterdam = open("a").specifyNewRoute("CARGO1", SHANGHAI, ROTTERDAM, hour(5)).get();
		Shipping toHelsinki = open("b").specifyNewRoute("CARGO1", SHANGHAI, HELSINKI, hour(5)).get();

		assertThat(toRotterdam.commit().isSuccess()).isTrue();
		assertThat(toHelsinki.commit().getCause()).isInstanceOf(Transaction.Conflict.class);
		assertThat(open("check").tracking().routeSpecificationOf("CARGO1"))
				.contains(new RouteSpecification(SHANGHAI, ROTTERDAM, hour(5)));
	}

	/**
	 * The route chosen for a specification another party then changes:
	 * SSI admits both commits, because the schedule is serializable as
	 * "route first, then re-specify", and the derived status says what that
	 * order means. The simulated tier refuses the same schedule
	 * (CertifiedWriteTest): its pin asks that the read still hold, which is
	 * stricter than serializability.
	 */
	@Test
	public void aRouteChosenForASpecificationTheWorldHasLeftIsSerializable() throws SQLException {
		seed();
		Shipping routing = open("route");
		List<Leg> chosen = routing.tracking().routeCandidatesOf("CARGO1").get(0);
		Shipping routed = routing.assignToRoute("CARGO1", chosen);

		assertThat(open("reroute").specifyNewRoute("CARGO1", SHANGHAI, ROTTERDAM, hour(5)).get()
				.commit().isSuccess()).isTrue();

		assertThat(routed.commit().isSuccess()).isTrue();
		assertThat(open("check").tracking().routingStatusOf("CARGO1")).isEqualTo(RoutingStatus.MISROUTED);
	}
}
