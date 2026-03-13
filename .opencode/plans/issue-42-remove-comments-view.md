# Plan: Remove PullRequestCommentsView and Migrate Functionality

**Issue:** [#42](https://github.com/Philipp0205/eclipse-pull-request-plugin/issues/42)
**Repository:** Philipp0205/eclipse-pull-request-plugin
**Labels:** enhancement
**Complexity:** Large
**Estimated tasks:** 14

---

## Overview

The standalone `PullRequestCommentsView` (~2157 lines) is a redundant `ViewPart`
now that comment rendering has moved to inline painted annotations in the
compare editor. This plan covers deleting the view, removing all dead-code
references, cleaning up the perspective layout, and implementing the two
remaining features that only existed in the view: **filter toggles**
("Show All Comments" and "Hide Resolved Comments") in the inline compare
editor.

**Out of scope (follow-up issue):** Create Task / Blocker severity toggle.
This is a Bitbucket-specific feature that will be tracked separately.

---

## Design Decisions

| Decision | Choice |
|----------|--------|
| Bottom pane in perspective | Remove the entire bottom folder (no Properties Sheet) |
| HIT_SELECT action | Remove entirely (no click-to-select on comment headers) |
| Inline filtering | Implement now (required by acceptance criteria) |
| Severity/task toggle | Defer to follow-up issue |

---

## Task Breakdown

### Phase 1: Delete the View and Clean Up References

#### Task 1 — Delete `PullRequestCommentsView.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestCommentsView.java`

- Delete the entire file (~2157 lines).

#### Task 2 — Remove view registration from `plugin.xml`

**File:** `org.eclipse.egit.pullrequest/plugin.xml` (lines 72-79)

- Remove the `<view>` element for `PullRequestCommentsView`.
- Keep all other view registrations intact.

#### Task 3 — Remove NLS entry from `plugin.properties`

**File:** `org.eclipse.egit.pullrequest/plugin.properties` (line 26)

- Remove the line: `PullRequestCommentsView = Pull Request Comments`

#### Task 4 — Clean up `PullRequestPerspectiveFactory.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestPerspectiveFactory.java`

Changes:
- Remove the entire "bottom" folder creation (lines 47-54) that contains
  `PullRequestCommentsView.VIEW_ID` and `IPageLayout.ID_PROP_SHEET`.
- Remove the `addShowViewShortcut` call for `PullRequestCommentsView.VIEW_ID`
  (line 60).
- Remove the `addShowViewShortcut` call for Properties
  (`IPageLayout.ID_PROP_SHEET`) since the bottom pane is gone.
- The editor area gets full height now (no bottom split).

#### Task 5 — Clean up `PullRequestListView.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestListView.java` (line 640)

- Remove the `page.showView(PullRequestCommentsView.VIEW_ID)` call.
- Remove the import of `PullRequestCommentsView` if it becomes unused.

#### Task 6 — Clean up `PullRequestChangedFilesView.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestChangedFilesView.java` (lines 379-386)

- Remove the block that finds the `PullRequestCommentsView` and calls
  `onCommentsLoaded()`.
- Remove the import of `PullRequestCommentsView` if it becomes unused.

#### Task 7 — Clean up `CommentViewSynchronizer.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentViewSynchronizer.java`

Changes:
- Remove `selectCommentInView(PullRequestComment)` method (lines 154-171).
- Remove `refreshCommentsView()` method (lines 179-197).
- Remove the `refreshCommentsView(freshComments)` call (line 106).
- Remove any imports that become unused (e.g., `IWorkbenchPage`,
  `IViewPart`, `PullRequestCommentsView`).
- If the class becomes empty or only has trivial wiring, consider whether it
  should be deleted or kept for the remaining `filterCommentsForCurrentFile()`
  logic.

#### Task 8 — Clean up `PullRequestCompareEditorInput.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestCompareEditorInput.java` (line 164)

- Remove or update the Javadoc `{@link PullRequestCommentsView}` reference.

### Phase 2: Remove the Select Action

#### Task 9 — Remove `onSelect` from `CommentActionHandler.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentActionHandler.java`

- Remove `onSelect(PullRequestComment)` method from the interface (line 52).

#### Task 10 — Remove `HIT_SELECT` from `CommentHitTestManager.java`

**File:** `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentHitTestManager.java`

- Remove `HIT_SELECT` constant (line 37).
- Remove the `HIT_SELECT` case from the `dispatchAction()` switch statement
  (lines 193-195 approximately).

#### Task 11 — Remove select handler and hit regions from overlay/renderer

**Files:**

1. **`CommentOverlayInstaller.java`** — Remove the `onSelect` override
   from the anonymous `CommentActionHandler` in `createActionHandler()`
   (lines 787-789).

2. **`CommentPaintRenderer.java`** — Remove any hit region registration
   for `HIT_SELECT` (the clickable header area). Look for calls to
   `hitTestManager.addRegion(...)` with `HIT_SELECT`.

### Phase 3: Implement Inline Filtering

#### Task 12 — Add "Hide Resolved Comments" toggle to the inline renderer

This filter hides comments with state `"RESOLVED"` from the inline overlay.
The toggle should default to `true` (resolved comments hidden by default),
matching the old view behavior.

**Implementation approach:**

1. **`CommentOverlayInstaller.java`** — Add a `hideResolvedComments` boolean
   field (default `true`). In the method that installs/renders comments
   (likely `showAllComments()` or similar), filter out comments where
   `comment.getState()` equals `"RESOLVED"` when the flag is true. Add a
   public setter `setHideResolvedComments(boolean)` that updates the flag
   and triggers a re-render.

2. **`PullRequestCompareEditorInput.java`** (or the appropriate editor input
   class) — Add a toolbar contribution or action bar contribution with a
   checked toggle action for "Hide Resolved Comments". Wire it to call
   `CommentOverlayInstaller.setHideResolvedComments()`.

3. **`PRText.java` / `prtext.properties`** — Add NLS string:
   `CommentFilter_HideResolved = Hide Resolved Comments`

#### Task 13 — Add "Show All Comments" toggle to the inline renderer

This toggle controls whether the inline renderer shows comments for all files
or only for the currently displayed file. Default is `false` (show only
current file comments).

**Implementation approach:**

1. **`CommentOverlayInstaller.java`** — Add a `showAllComments` boolean field
   (default `false`). When `false`, only comments matching the current file
   path are rendered. When `true`, all comments are rendered (this may need
   visual distinction to show comments from other files). Add a public setter
   `setShowAllComments(boolean)` that triggers re-render.

2. **`PullRequestCompareEditorInput.java`** — Add a toolbar toggle action
   for "Show All Comments". Wire to
   `CommentOverlayInstaller.setShowAllComments()`.

3. **`PRText.java` / `prtext.properties`** — Add NLS string:
   `CommentFilter_ShowAll = Show All Comments`

**Note:** The "Show All Comments" toggle in the inline compare editor context
means showing comments from all files vs. only the file currently open in the
compare editor. This needs to be validated against how
`CommentOverlayInstaller` receives its comment list — it may already only
receive file-specific comments, in which case the full comment list needs to
be made accessible.

### Phase 4: Verify and Test

#### Task 14 — Run existing tests, verify no dead references, add filter tests

- Run `mvn clean verify` to ensure no compilation errors.
- Run `mvn test` in the test module to verify existing tests pass.
- Grep for `PullRequestCommentsView.VIEW_ID` and the view ID string to
  verify no dead references remain.
- If filtering logic is testable without UI harness, add unit tests for the
  comment filtering (hide resolved, show all).
- Verify that `CommentActionHandler` interface changes don't break existing
  test compilation.

---

## Files Summary

### Files to delete (1)
- `PullRequestCommentsView.java`

### Files to modify (up to 13)
| File | Phase | Changes |
|------|-------|---------|
| `plugin.xml` | 1 | Remove view registration |
| `plugin.properties` | 1 | Remove NLS entry |
| `PullRequestPerspectiveFactory.java` | 1 | Remove bottom folder and shortcuts |
| `PullRequestListView.java` | 1 | Remove showView call |
| `PullRequestChangedFilesView.java` | 1 | Remove onCommentsLoaded block |
| `CommentViewSynchronizer.java` | 1 | Remove dead methods |
| `PullRequestCompareEditorInput.java` | 1, 3 | Remove Javadoc ref, add toolbar actions |
| `CommentActionHandler.java` | 2 | Remove onSelect from interface |
| `CommentHitTestManager.java` | 2 | Remove HIT_SELECT |
| `CommentOverlayInstaller.java` | 2, 3 | Remove onSelect, add filtering |
| `CommentPaintRenderer.java` | 2 | Remove select hit region |
| `PRText.java` | 3 | Add filter NLS strings |
| `prtext.properties` | 3 | Add filter NLS strings |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| "Show All Comments" toggle may need access to full comment list that isn't available in the overlay installer | Trace how comments flow from the client through to the installer; may need to pass the full PR comment list alongside file-specific comments |
| Removing HIT_SELECT may affect comment header click behavior | Verify that comment header is not the only interactive area; reply/edit/delete/resolve links remain clickable |
| Perspective change may confuse existing users | The perspective factory only affects new workspace layouts; existing users keep their customized layout |

---

## Follow-Up Issues to Create

1. **Create Task / Blocker severity toggle in inline renderer** — Migrate the
   severity toggle (`NORMAL` <-> `BLOCKER`) from the deleted view to the
   inline comment system. This is Bitbucket-specific.
