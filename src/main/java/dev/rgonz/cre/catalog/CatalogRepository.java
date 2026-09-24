package dev.rgonz.cre.catalog;

import dev.rgonz.cre.catalog.Operation.Create;
import dev.rgonz.cre.catalog.Operation.Delete;
import dev.rgonz.cre.catalog.Operation.Update;
import dev.rgonz.cre.workspace.WorkspaceKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes catalogs, their features, and their active relationship groups. */
@Repository
public class CatalogRepository {
  /** The read-only showcase laptop catalog, which is also copied for every new guest. */
  static final UUID LAPTOP_SEED = UUID.fromString("00000000-0000-0000-0000-00000000c001");

  /** The revision every new guest catalog starts at. */
  public static final long FIRST_REVISION = 1;

  private static final String CATALOG_COLUMNS =
      "c.id, c.workspace_id, w.kind, c.name, c.revision FROM catalog c"
          + " JOIN workspace w ON w.id = c.workspace_id";

  private final JdbcClient jdbc;

  public CatalogRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<CatalogRow> find(UUID catalogId) {
    return jdbc.sql("SELECT " + CATALOG_COLUMNS + " WHERE c.id = ?")
        .param(catalogId)
        .query(this::mapRow)
        .optional();
  }

  /** The showcase catalogs plus, when given, the guest workspace's own catalog first. */
  List<CatalogRow> findVisible(UUID guestWorkspaceId) {
    return jdbc.sql(
            "SELECT "
                + CATALOG_COLUMNS
                + " WHERE w.kind = 'SHOWCASE' OR c.workspace_id = ?::uuid ORDER BY c.sort_order")
        .param(guestWorkspaceId == null ? null : guestWorkspaceId.toString())
        .query(this::mapRow)
        .list();
  }

  /**
   * Share-locks the catalog's workspace until the transaction ends. Taking it before the catalog
   * lock matches the order a workspace purge uses, so the two cannot deadlock.
   */
  void lockWorkspaceOf(UUID catalogId) {
    jdbc.sql(
            "SELECT w.id FROM workspace w JOIN catalog c ON c.workspace_id = w.id"
                + " WHERE c.id = ? FOR SHARE OF w")
        .param(catalogId)
        .query(UUID.class)
        .optional();
  }

  /**
   * Locks the catalog row until the transaction ends and returns its current revision, or nothing
   * if the catalog was deleted after the caller last read it.
   */
  Optional<Long> lockRevision(UUID catalogId) {
    return jdbc.sql("SELECT revision FROM catalog WHERE id = ? FOR UPDATE")
        .param(catalogId)
        .query(Long.class)
        .optional();
  }

  long incrementRevision(UUID catalogId) {
    return jdbc.sql("UPDATE catalog SET revision = revision + 1 WHERE id = ? RETURNING revision")
        .param(catalogId)
        .query(Long.class)
        .single();
  }

  public Optional<CatalogSnapshot> snapshot(UUID catalogId) {
    var revision =
        jdbc.sql("SELECT revision FROM catalog WHERE id = ?").param(catalogId).query(Long.class);

    return revision
        .optional()
        .map(
            current ->
                new CatalogSnapshot(catalogId, current, features(catalogId), groups(catalogId)));
  }

  private List<Feature> features(UUID catalogId) {
    return jdbc.sql("SELECT id, code, name FROM feature WHERE catalog_id = ? ORDER BY position")
        .param(catalogId)
        .query(
            (row, index) ->
                new Feature(
                    row.getObject("id", UUID.class), row.getString("code"), row.getString("name")))
        .list();
  }

  private List<RelationshipGroup> groups(UUID catalogId) {
    var targets = new HashMap<UUID, List<UUID>>();
    jdbc.sql(
            "SELECT group_id, target_feature_id FROM relationship_target"
                + " WHERE catalog_id = ? ORDER BY position")
        .param(catalogId)
        .query(
            row -> {
              targets
                  .computeIfAbsent(row.getObject("group_id", UUID.class), id -> new ArrayList<>())
                  .add(row.getObject("target_feature_id", UUID.class));
            });

    return jdbc.sql(
            "SELECT id, source_feature_id, kind FROM relationship_group"
                + " WHERE catalog_id = ? ORDER BY seq")
        .param(catalogId)
        .query(
            (row, index) -> {
              var id = row.getObject("id", UUID.class);

              return new RelationshipGroup(
                  id,
                  row.getObject("source_feature_id", UUID.class),
                  RelationshipKind.valueOf(row.getString("kind")),
                  targets.getOrDefault(id, List.of()));
            })
        .list();
  }

  /**
   * Copies the laptop seed into a new catalog for a guest workspace. Features keep their IDs;
   * groups get new ones so each guest's groups are distinct.
   */
  public UUID cloneLaptopSeed(UUID workspaceId) {
    var catalogId = UUID.randomUUID();

    jdbc.sql(
            "INSERT INTO catalog (id, workspace_id, name, sort_order, revision)"
                + " SELECT ?, ?, name, 0, ? FROM catalog WHERE id = ?")
        .params(catalogId, workspaceId, FIRST_REVISION, LAPTOP_SEED)
        .update();

    jdbc.sql(
            "INSERT INTO feature (catalog_id, id, code, name, position)"
                + " SELECT ?, id, code, name, position FROM feature WHERE catalog_id = ?")
        .params(catalogId, LAPTOP_SEED)
        .update();

    for (var group : groups(LAPTOP_SEED)) {
      insertGroup(catalogId, group.sourceId(), group.kind(), group.targetIds());
    }

    return catalogId;
  }

  /** Writes a batch's changes to the active groups. */
  void applyOperations(UUID catalogId, List<Operation> operations) {
    for (var operation : operations) {
      switch (operation) {
        case Create create ->
            insertGroup(catalogId, create.sourceId(), create.kind(), create.targetIds());

        case Update update -> {
          jdbc.sql("DELETE FROM relationship_target WHERE catalog_id = ? AND group_id = ?")
              .params(catalogId, update.groupId())
              .update();

          insertTargets(catalogId, update.groupId(), update.targetIds());
        }

        case Delete delete ->
            jdbc.sql("DELETE FROM relationship_group WHERE catalog_id = ? AND id = ?")
                .params(catalogId, delete.groupId())
                .update();
      }
    }
  }

  private void insertGroup(
      UUID catalogId, UUID sourceId, RelationshipKind kind, List<UUID> targetIds) {
    var groupId = UUID.randomUUID();

    jdbc.sql(
            "INSERT INTO relationship_group (catalog_id, id, source_feature_id, kind)"
                + " VALUES (?, ?, ?, ?)")
        .params(catalogId, groupId, sourceId, kind.name())
        .update();

    insertTargets(catalogId, groupId, targetIds);
  }

  private void insertTargets(UUID catalogId, UUID groupId, List<UUID> targetIds) {
    for (int i = 0; i < targetIds.size(); i++) {
      jdbc.sql(
              "INSERT INTO relationship_target (catalog_id, group_id, target_feature_id, position)"
                  + " VALUES (?, ?, ?, ?)")
          .params(catalogId, groupId, targetIds.get(i), i + 1)
          .update();
    }
  }

  private CatalogRow mapRow(ResultSet row, int index) throws SQLException {
    return new CatalogRow(
        row.getObject("id", UUID.class),
        row.getObject("workspace_id", UUID.class),
        WorkspaceKind.valueOf(row.getString("kind")),
        row.getString("name"),
        row.getLong("revision"));
  }
}
