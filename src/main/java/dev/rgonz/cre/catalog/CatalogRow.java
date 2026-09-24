package dev.rgonz.cre.catalog;

import dev.rgonz.cre.workspace.WorkspaceKind;
import java.util.UUID;

/** A catalog's identity, owning workspace, and current revision. */
record CatalogRow(
    UUID id, UUID workspaceId, WorkspaceKind workspaceKind, String name, long revision) {}
