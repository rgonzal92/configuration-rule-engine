package dev.rgonz.cre.catalog;

import dev.rgonz.cre.catalog.CatalogViews.ApplyRequest;
import dev.rgonz.cre.catalog.CatalogViews.ApplyResult;
import dev.rgonz.cre.catalog.CatalogViews.CheckView;
import dev.rgonz.cre.catalog.CatalogViews.DraftRequest;
import dev.rgonz.cre.catalog.CatalogViews.DraftView;
import dev.rgonz.cre.core.ApiException;
import dev.rgonz.cre.workspace.WorkspaceRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stages, checks, and applies a guest catalog's pending batch. Every write holds the catalog row
 * lock, so saves, checks, and applies for one catalog run one at a time. Revisions are compared as
 * primitive {@code long} values, never as boxed objects.
 */
@Service
class DraftService {
  private final CatalogRepository catalogs;
  private final DraftRepository drafts;
  private final ReceiptRepository receipts;
  private final WorkspaceRepository workspaces;
  private final CatalogAccess access;
  private final JsonMapper json;
  private final Clock clock;

  public DraftService(
      CatalogRepository catalogs,
      DraftRepository drafts,
      ReceiptRepository receipts,
      WorkspaceRepository workspaces,
      CatalogAccess access,
      JsonMapper json,
      Clock clock) {
    this.catalogs = catalogs;
    this.drafts = drafts;
    this.receipts = receipts;
    this.workspaces = workspaces;
    this.access = access;
    this.json = json;
    this.clock = clock;
  }

  /** Repeatable read keeps the draft and the revision it is compared with consistent. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public DraftView read(UUID catalogId, Authentication authentication) {
    var revision = access.writable(catalogId, authentication).catalog().revision();

    return view(drafts.find(catalogId).orElseThrow(ApiException::notFound), revision);
  }

  /** Replaces the draft only if the browser saw the current draft and catalog versions. */
  @Transactional
  public DraftView save(UUID catalogId, DraftRequest request, Authentication authentication) {
    access.writable(catalogId, authentication);

    if (request.expectedDraftVersion() == null
        || request.expectedRevision() == null
        || request.operations() == null) {
      throw new ApiException(
          400, "INVALID_REQUEST", "Send the expected versions and the list of changes");
    }

    long revision = catalogs.lockRevision(catalogId).orElseThrow(ApiException::notFound);
    var draft = drafts.lock(catalogId).orElseThrow(ApiException::notFound);

    if (request.expectedRevision() != revision) {
      throw new ApiException(409, "CATALOG_CHANGED", "The catalog was changed in another tab.");
    }

    if (request.expectedDraftVersion() != draft.draftVersion()) {
      throw new ApiException(
          409, "DRAFT_CONFLICT", "The pending changes were edited in another tab.");
    }

    var snapshot = snapshot(catalogId);
    var operations = parse(request.operations());

    var errors = DraftRules.validate(snapshot, operations);
    if (!errors.isEmpty()) {
      throw ApiException.invalidDraft(errors);
    }

    drafts.replace(catalogId, operations, draft.draftVersion() + 1, revision);

    return view(drafts.find(catalogId).orElseThrow(), revision);
  }

  /** Checks the whole draft and remembers a valid result for this exact draft and revision. */
  @Transactional
  public CheckView check(UUID catalogId, Authentication authentication) {
    access.writable(catalogId, authentication);

    long revision = catalogs.lockRevision(catalogId).orElseThrow(ApiException::notFound);
    var draft = drafts.lock(catalogId).orElseThrow(ApiException::notFound);

    if (draft.operations().isEmpty()) {
      throw new ApiException(400, "EMPTY_DRAFT", "Stage at least one change before checking");
    }

    if (draft.baseRevision() != revision) {
      throw new ApiException(
          409, "STALE_DRAFT", "The catalog changed after these changes were staged");
    }

    var result = checkBatch(snapshot(catalogId), draft.operations());

    if (result.valid()) {
      drafts.markChecked(catalogId, draft.draftVersion(), revision);
    } else {
      drafts.clearChecked(catalogId);
    }

    return new CheckView(draft.draftVersion(), revision, result);
  }

  /**
   * Applies the checked draft in one transaction. The receipt is looked up before the draft is
   * read, so a retry after a lost response still returns the first result once the draft is
   * cleared.
   */
  @Transactional
  public ApplyResult apply(UUID catalogId, ApplyRequest request, Authentication authentication) {
    var allowed = access.writable(catalogId, authentication);
    var workspace = allowed.workspace();

    if (request.commandId() == null || request.checkedDraftVersion() == null) {
      throw new ApiException(400, "INVALID_REQUEST", "Send a command ID and the checked version");
    }

    catalogs.lockWorkspaceOf(catalogId);
    long revision = catalogs.lockRevision(catalogId).orElseThrow(ApiException::notFound);

    var receipt = receipts.find(workspace.id(), request.commandId());
    if (receipt.isPresent()) {
      var earlier = receipt.get();
      boolean sameCommand =
          earlier.catalogId().equals(catalogId)
              && earlier.draftVersion() == request.checkedDraftVersion();

      if (!sameCommand) {
        throw new ApiException(
            409, "COMMAND_REUSED", "This command ID was already used for other changes");
      }

      return json.readValue(earlier.resultJson(), ApplyResult.class);
    }

    var draft = drafts.lock(catalogId).orElseThrow(ApiException::notFound);
    requireCurrentCheck(draft, request.checkedDraftVersion(), revision);

    var result = checkBatch(snapshot(catalogId), draft.operations());
    if (!result.valid()) {
      throw staleCheck();
    }

    catalogs.applyOperations(catalogId, draft.operations());

    // The workspace is read again because it may have expired while this request ran.
    var stillLive =
        workspaces.findById(workspace.id()).filter(w -> w.isLiveAt(clock.instant())).isPresent();
    if (!stillLive) {
      throw new ApiException(401, "SESSION_EXPIRED", "Your guest workspace expired");
    }

    var newRevision = catalogs.incrementRevision(catalogId);
    var newDraftVersion = draft.draftVersion() + 1;
    drafts.replace(catalogId, List.of(), newDraftVersion, newRevision);

    var applied = new ApplyResult(newRevision, newDraftVersion, result.added(), result.removed());
    receipts.save(
        workspace.id(),
        request.commandId(),
        catalogId,
        request.checkedDraftVersion(),
        json.writeValueAsString(applied));

    return applied;
  }

  /** Exact counting has a size limit, which the shipped catalogs stay well inside. */
  private static CheckResult checkBatch(CatalogSnapshot snapshot, List<Operation> operations) {
    try {
      return BatchChecker.check(snapshot, operations);
    } catch (IllegalStateException exception) {
      throw new ApiException(422, "CATALOG_TOO_LARGE", exception.getMessage());
    }
  }

  private static void requireCurrentCheck(Draft draft, long checkedDraftVersion, long revision) {
    if (draft.draftVersion() != checkedDraftVersion || !draft.isCheckedAt(revision)) {
      throw staleCheck();
    }
  }

  private static ApiException staleCheck() {
    return new ApiException(409, "CHECK_STALE", "Check the pending changes again before applying");
  }

  private CatalogSnapshot snapshot(UUID catalogId) {
    return catalogs.snapshot(catalogId).orElseThrow(ApiException::notFound);
  }

  private static List<Operation> parse(List<OperationJson> operations) {
    var parsed = new ArrayList<Operation>();
    var errors = new ArrayList<String>();

    for (int i = 0; i < operations.size(); i++) {
      try {
        var operation = operations.get(i);
        if (operation == null) {
          throw new IllegalArgumentException("the change is empty");
        }

        parsed.add(operation.toOperation());
      } catch (IllegalArgumentException exception) {
        errors.add("Change " + (i + 1) + ": " + exception.getMessage());
      }
    }

    if (!errors.isEmpty()) {
      throw ApiException.invalidDraft(errors);
    }

    return parsed;
  }

  private static DraftView view(Draft draft, long revision) {
    return new DraftView(
        draft.draftVersion(),
        draft.baseRevision(),
        draft.isCheckedAt(revision),
        draft.operations().stream().map(OperationJson::of).toList());
  }
}
