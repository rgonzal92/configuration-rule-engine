package dev.rgonz.cre.persistence;

import dev.rgonz.cre.domain.Workspace;
import dev.rgonz.cre.domain.WorkspaceKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes workspace rows. */
@Repository
public class WorkspaceRepository {
  /** Arbitrary advisory-lock key that serializes guest quota checks with guest creation. */
  private static final long GUEST_CREATION_LOCK = 0x4352_4547_5545_5354L;

  private final JdbcClient jdbc;

  public WorkspaceRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Workspace> findById(UUID id) {
    return jdbc.sql("SELECT * FROM workspace WHERE id = ?").param(id).query(this::map).optional();
  }

  public Workspace findShowcase() {
    return jdbc.sql("SELECT * FROM workspace WHERE kind = 'SHOWCASE'").query(this::map).single();
  }

  /** Blocks other guest creations until the current transaction ends. */
  public void lockGuestCreation() {
    jdbc.sql("SELECT pg_advisory_xact_lock(?)").param(GUEST_CREATION_LOCK).query().singleRow();
  }

  public long countGuestsCreatedAfter(Instant since) {
    return jdbc.sql("SELECT count(*) FROM workspace WHERE kind = 'GUEST' AND created_at > ?")
        .param(Timestamp.from(since))
        .query(Long.class)
        .single();
  }

  public void insert(Workspace workspace) {
    jdbc.sql(
            """
            INSERT INTO workspace (id, kind, owner_id, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """)
        .params(
            workspace.id(),
            workspace.kind().name(),
            workspace.ownerId(),
            Timestamp.from(workspace.createdAt()),
            workspace.expiresAt() == null ? null : Timestamp.from(workspace.expiresAt()))
        .update();
  }

  /** Deletes guest workspaces whose expiry is at or before {@code now}. */
  public int deleteGuestsExpiredAt(Instant now) {
    return jdbc.sql("DELETE FROM workspace WHERE kind = 'GUEST' AND expires_at <= ?")
        .param(Timestamp.from(now))
        .update();
  }

  private Workspace map(ResultSet row, int index) throws SQLException {
    var expires = row.getTimestamp("expires_at");

    return new Workspace(
        row.getObject("id", UUID.class),
        WorkspaceKind.valueOf(row.getString("kind")),
        row.getObject("owner_id", UUID.class),
        row.getTimestamp("created_at").toInstant(),
        expires == null ? null : expires.toInstant());
  }
}
