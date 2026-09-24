package dev.rgonz.cre.suggestion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Records live assistant requests so their limits hold across restarts and instances. */
@Repository
class SuggestionAttemptRepository {
  /** Arbitrary advisory-lock key that serializes limit checks with attempt reservation. */
  private static final long ATTEMPT_LOCK = 0x4352_4541_5454_454DL;

  private final JdbcClient jdbc;

  SuggestionAttemptRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Blocks other reservations until the current transaction ends. */
  void lockAttempts() {
    jdbc.sql("SELECT pg_advisory_xact_lock(?)").param(ATTEMPT_LOCK).query().singleRow();
  }

  long countForWorkspace(UUID workspaceId) {
    return jdbc.sql("SELECT count(*) FROM suggestion_attempt WHERE workspace_id = ?")
        .param(workspaceId)
        .query(Long.class)
        .single();
  }

  long countSince(Instant since) {
    return jdbc.sql("SELECT count(*) FROM suggestion_attempt WHERE attempted_at >= ?")
        .param(Timestamp.from(since))
        .query(Long.class)
        .single();
  }

  void insert(UUID workspaceId, Instant attemptedAt) {
    jdbc.sql("INSERT INTO suggestion_attempt (workspace_id, attempted_at) VALUES (?, ?)")
        .params(workspaceId, Timestamp.from(attemptedAt))
        .update();
  }

  int deleteBefore(Instant cutoff) {
    return jdbc.sql("DELETE FROM suggestion_attempt WHERE attempted_at < ?")
        .param(Timestamp.from(cutoff))
        .update();
  }
}
