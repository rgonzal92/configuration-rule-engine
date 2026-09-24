package dev.rgonz.cre.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Remembers each applied command so a retried request gets its original result. */
@Repository
class ReceiptRepository {
  /** What a command applied and the JSON result it returned. */
  public record Receipt(UUID catalogId, long draftVersion, String resultJson) {}

  private final JdbcClient jdbc;

  public ReceiptRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Receipt> find(UUID workspaceId, UUID commandId) {
    return jdbc.sql(
            "SELECT catalog_id, draft_version, result::text AS result FROM apply_receipt"
                + " WHERE workspace_id = ? AND command_id = ?")
        .params(workspaceId, commandId)
        .query(
            (row, index) ->
                new Receipt(
                    row.getObject("catalog_id", UUID.class),
                    row.getLong("draft_version"),
                    row.getString("result")))
        .optional();
  }

  public void save(
      UUID workspaceId, UUID commandId, UUID catalogId, long draftVersion, String resultJson) {
    jdbc.sql(
            """
            INSERT INTO apply_receipt (workspace_id, command_id, catalog_id, draft_version, result)
            VALUES (?, ?, ?, ?, ?::jsonb)
            """)
        .params(workspaceId, commandId, catalogId, draftVersion, resultJson)
        .update();
  }
}
