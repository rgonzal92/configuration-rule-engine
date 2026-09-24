package dev.rgonz.cre.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Stores each guest catalog's pending batch as JSON operations with version counters. */
@Repository
public class DraftRepository {
  private final JdbcClient jdbc;
  private final JsonMapper json;

  public DraftRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Starts an empty draft for a new guest catalog. */
  public void create(UUID catalogId, long baseRevision) {
    jdbc.sql(
            "INSERT INTO pending_batch (catalog_id, operations, draft_version, base_revision)"
                + " VALUES (?, '[]'::jsonb, 0, ?)")
        .params(catalogId, baseRevision)
        .update();
  }

  Optional<Draft> find(UUID catalogId) {
    return select(catalogId, "");
  }

  /** Reads the draft and locks its row until the transaction ends. */
  Optional<Draft> lock(UUID catalogId) {
    return select(catalogId, " FOR UPDATE");
  }

  private Optional<Draft> select(UUID catalogId, String locking) {
    return jdbc.sql("SELECT * FROM pending_batch WHERE catalog_id = ?" + locking)
        .param(catalogId)
        .query(
            (row, index) ->
                new Draft(
                    catalogId,
                    read(row.getString("operations")),
                    row.getLong("draft_version"),
                    row.getLong("base_revision"),
                    row.getObject("checked_draft_version", Long.class),
                    row.getObject("checked_revision", Long.class)))
        .optional();
  }

  /** Replaces the operations under a new version and forgets any earlier check. */
  void replace(UUID catalogId, List<Operation> operations, long draftVersion, long baseRevision) {
    jdbc.sql(
            """
            UPDATE pending_batch
            SET operations = ?::jsonb, draft_version = ?, base_revision = ?,
                checked_draft_version = NULL, checked_revision = NULL
            WHERE catalog_id = ?
            """)
        .params(write(operations), draftVersion, baseRevision, catalogId)
        .update();
  }

  /** Records that this draft version passed a check at this catalog revision. */
  void markChecked(UUID catalogId, long draftVersion, long revision) {
    setChecked(catalogId, draftVersion, revision);
  }

  void clearChecked(UUID catalogId) {
    setChecked(catalogId, null, null);
  }

  private void setChecked(UUID catalogId, Long draftVersion, Long revision) {
    jdbc.sql(
            "UPDATE pending_batch SET checked_draft_version = ?, checked_revision = ?"
                + " WHERE catalog_id = ?")
        .params(draftVersion, revision, catalogId)
        .update();
  }

  private String write(List<Operation> operations) {
    return json.writeValueAsString(operations.stream().map(OperationJson::of).toList());
  }

  private List<Operation> read(String operations) {
    return Arrays.stream(json.readValue(operations, OperationJson[].class))
        .map(OperationJson::toOperation)
        .toList();
  }
}
