package dev.rgonz.cre;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the application over real HTTP against one shared PostgreSQL container with a test clock.
 * The container is started once and never stopped between classes, so the cached Spring context
 * stays connected to it.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "cre.guest.purge-interval=1h")
@Import(ApplicationIT.ClockConfig.class)
abstract class ApplicationIT {
  @ServiceConnection
  static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18.6");

  static {
    DATABASE.start();
  }

  static final Instant START = Instant.parse("2026-03-01T12:00:00Z");

  @LocalServerPort int port;
  @Autowired MutableClock clock;
  @Autowired JdbcClient jdbc;

  /** Every test starts at the same instant with no guests or sessions. */
  @BeforeEach
  void resetGuests() {
    clock.set(START);
    jdbc.sql("DELETE FROM spring_session").update();
    jdbc.sql("DELETE FROM workspace WHERE kind = 'GUEST'").update();
  }

  GuestClient visitor() {
    return new GuestClient("http://127.0.0.1:" + port);
  }

  long guestCount() {
    return jdbc.sql("SELECT count(*) FROM workspace WHERE kind = 'GUEST'")
        .query(Long.class)
        .single();
  }

  /** Lets tests move application time without waiting. */
  static final class MutableClock extends Clock {
    private volatile Instant now = START;

    void set(Instant instant) {
      now = instant;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }
  }

  /** Replaces the system clock with the test clock. */
  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }
}
