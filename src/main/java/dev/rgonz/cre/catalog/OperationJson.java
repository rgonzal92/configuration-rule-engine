package dev.rgonz.cre.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.rgonz.cre.catalog.Operation.Create;
import dev.rgonz.cre.catalog.Operation.Delete;
import dev.rgonz.cre.catalog.Operation.Update;
import java.util.List;
import java.util.UUID;

/**
 * The JSON shape of one staged change, used both in API bodies and in the stored draft.
 *
 * @param type {@code CREATE}, {@code UPDATE}, or {@code DELETE}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record OperationJson(
    String type,
    UUID groupId,
    UUID sourceFeatureId,
    RelationshipKind kind,
    List<UUID> targetFeatureIds) {

  public static OperationJson of(Operation operation) {
    return switch (operation) {
      case Create create ->
          new OperationJson("CREATE", null, create.sourceId(), create.kind(), create.targetIds());
      case Update update ->
          new OperationJson("UPDATE", update.groupId(), null, null, update.targetIds());
      case Delete delete -> new OperationJson("DELETE", delete.groupId(), null, null, null);
    };
  }

  /**
   * Converts this JSON shape into a domain operation.
   *
   * @throws IllegalArgumentException for an unknown type or a missing target entry
   */
  public Operation toOperation() {
    if (targetFeatureIds != null && targetFeatureIds.contains(null)) {
      throw new IllegalArgumentException("a target feature is missing");
    }

    return switch (type == null ? "" : type) {
      case "CREATE" -> new Create(sourceFeatureId, kind, targetFeatureIds);
      case "UPDATE" -> new Update(groupId, targetFeatureIds);
      case "DELETE" -> new Delete(groupId);
      default -> throw new IllegalArgumentException("unknown change type");
    };
  }
}
