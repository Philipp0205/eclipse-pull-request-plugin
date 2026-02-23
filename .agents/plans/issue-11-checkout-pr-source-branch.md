# Implementation Plan: Checkout PR Source Branch

**Issue**: #11  
**Repository**: Philipp0205/eclipse-pull-request-plugin  
**Created**: 2026-02-22  
**Priority**: High  
**Complexity**: Medium-Large  

---

## Overview

Add a one-click "Checkout Branch" action that fetches and checks out the source branch of a pull request. This enables reviewers to test code locally with full IDE features (debugging, refactoring, code navigation).

The implementation spans:
- **Data Model**: Add clone URL to `PullRequest.Repository` for fork support
- **JSON Parsers**: Extract clone URLs from both GitHub and Bitbucket API responses
- **Core Logic**: New `CheckoutPullRequestBranchJob` using EGit/JGit APIs
- **UI**: Button in PR overview + toolbar action in PR list view
- **NLS**: Externalized strings for all user-facing text

---

## Technical Approach

### Architecture Strategy

1. **Reuse EGit APIs**: Use `BranchOperationUI.checkout()` for the actual branch switch (handles uncommitted changes, stash dialogs, workspace refresh automatically). Use `FetchOperation` for fetching remote branches.

2. **Reuse existing patterns**: `resolveGitRepository()` in `PullRequestChangedFilesView` already resolves the local JGit `Repository` by matching remote URLs against configured server/project/repo. Extract this into a shared utility.

3. **Same-repo vs Fork distinction**:
   - **Same-repo PR**: The `fromRef` repo matches the `toRef` repo. Fetch from the existing `origin` remote, then checkout `refs/remotes/origin/{branchName}`.
   - **Fork PR**: The `fromRef` repo differs from `toRef`. Need to add/find a remote for the fork's clone URL, fetch from it, then checkout.

4. **Provider-agnostic flow**: The checkout job works with the model's `PullRequestRef` — it doesn't care whether the PR is from GitHub or Bitbucket.

### Key Design Decisions

- **Use `BranchOperationUI`** (not raw `BranchOperation`) — it handles dirty working tree dialogs, project restore, decorator refresh, and job scheduling. This directly satisfies the acceptance criteria for "handles uncommitted changes gracefully" and "integrates with EGit branch switching UI patterns".
- **Confirmation dialog**: Show a simple `MessageDialog.openConfirm()` with branch name, source repo, and latest commit SHA before proceeding.
- **No `org.eclipse.jgit.api` import needed**: `FetchOperation` and `BranchOperationUI`/`BranchOperation` are from `org.eclipse.egit.core.op` and `org.eclipse.egit.ui.internal.branch` — both already available via `Require-Bundle`.
- **Clone URL storage**: Add `cloneUrl` field to `PullRequest.Repository` so fork remotes can be configured.

---

## Task Breakdown

### Phase 1: Model & Parser Updates (1 day)

#### Task 1.1: Add `cloneUrl` to `PullRequest.Repository`
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequest.java` (lines 345-391)

- Add `private String cloneUrl` field to `Repository` inner class
- Add getter/setter with Javadoc
- This URL is used for fork-based PRs where `fromRef` points to a different repo

**Acceptance**: `repo.getCloneUrl()` returns the HTTPS clone URL or null for same-repo PRs.

---

#### Task 1.2: Update `GitHubJsonParser.parseRef()` to extract `clone_url`
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParser.java` (lines 202-226)

- In the `repoJson` block (line 209), add:
  ```java
  repo.setCloneUrl(extractString(repoJson, "clone_url"));
  ```
- GitHub's PR API returns `head.repo.clone_url` and `base.repo.clone_url`

**Acceptance**: `pr.getFromRef().getRepository().getCloneUrl()` returns the fork's clone URL.

---

#### Task 1.3: Update `BitbucketJsonParser.parseRepository()` to extract clone links
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketJsonParser.java` (lines 558-569+)

- Bitbucket Data Center returns `"links": { "clone": [{"href": "...", "name": "http"}, ...] }` on repository objects
- Parse the `clone` links array from the repository JSON
- Extract the `http` clone link and set it on `repo.setCloneUrl()`

**Acceptance**: Fork-based Bitbucket PRs have `getCloneUrl()` populated.

---

#### Task 1.4: Add test coverage for clone URL parsing
**Files**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`, `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClientTest.java`

- Add test methods that verify `clone_url` is extracted from PR JSON
- Add test JSON fixtures with `clone_url`/clone links present

---

### Phase 2: Shared Utility — Repository Resolution (0.5 day)

#### Task 2.1: Extract `resolveGitRepository()` into a shared utility
**New file**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/util/RepositoryResolver.java`  
**Existing file**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestChangedFilesView.java` (lines 306-358)

- Create `RepositoryResolver` with a `static Repository resolve(PullRequest pr)` method
- Move the logic from `PullRequestChangedFilesView.resolveGitRepository()` into it
- Update `PullRequestChangedFilesView` to delegate to `RepositoryResolver.resolve()`
- This utility will also be used by the checkout job

**Acceptance**: Both the changed files view and checkout job use the same resolution logic. Existing behavior unchanged.

---

### Phase 3: Core Checkout Logic (1-2 days)

#### Task 3.1: Create `CheckoutPullRequestBranchJob`
**New file**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CheckoutPullRequestBranchJob.java`

This is the core implementation. It's an Eclipse `Job` that:

1. **Resolves the local Git repository** via `RepositoryResolver.resolve(pr)`
2. **Determines same-repo vs fork**:
   - Compare `fromRef.getRepository().getSlug()` + project key against `toRef`
   - If same → use existing `origin` remote
   - If fork → find or add a remote for the fork's clone URL
3. **Fetches the branch**:
   - Build a `RefSpec`: `+refs/heads/{branchName}:refs/remotes/{remote}/{branchName}`
   - Use `FetchOperation(repository, remoteConfig, timeout, false)` from `org.eclipse.egit.core.op`
   - Use `EclipseGitProgressTransformer` to hook into the progress monitor
4. **Shows confirmation dialog** (on UI thread via `Display.syncExec`):
   - "Checkout branch '{displayId}' from {repoName}?"
   - Shows branch name, latest commit (truncated SHA), source repo
   - OK / Cancel
5. **Checks out the branch** via `BranchOperationUI.checkout(repository, target).start()`:
   - `target` = `refs/remotes/{remote}/{branchName}` for remote tracking branch
   - `BranchOperationUI` handles: dirty tree → stash/reset dialog, workspace project restore, decorator refresh
6. **Error handling**:
   - Repository not found locally → show error dialog
   - Fetch failed (auth, network) → show error with details
   - Checkout conflict → handled by `BranchOperationUI` automatically

**Key APIs used**:
- `org.eclipse.egit.core.op.FetchOperation` — fetch remote branch
- `org.eclipse.egit.ui.internal.branch.BranchOperationUI` — checkout with UI
- `org.eclipse.jgit.transport.RemoteConfig` — remote configuration
- `org.eclipse.jgit.transport.RefSpec` — refspec for fetch
- `org.eclipse.egit.core.RepositoryCache.INSTANCE` — find local repos

**Skeleton**:
```java
public class CheckoutPullRequestBranchJob extends Job {
	private final PullRequest pullRequest;
	private final Shell shell;

	public CheckoutPullRequestBranchJob(
			PullRequest pullRequest, Shell shell) {
		super(PRText.CheckoutBranch_JobName);
		this.pullRequest = pullRequest;
		this.shell = shell;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		// 1. Resolve local repo
		// 2. Determine remote (origin vs fork)
		// 3. Fetch branch
		// 4. Confirm dialog (syncExec)
		// 5. BranchOperationUI.checkout().start()
		// 6. Error handling
	}
}
```

---

### Phase 4: UI Integration (1 day)

#### Task 4.1: Add "Checkout Branch" button to PR Overview
**Files**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewHtmlRenderer.java` (line 176-185), `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewView.java` (line 144-155)

**HTML Renderer** — Add a third button after "View Changed Files":
```java
String checkoutBtn = "<button onclick=\"eclipseAction("
	+ "'checkoutBranch'); return false;\">";
html.append(checkoutBtn);
html.append("Checkout Branch</button>");
```

**Overview View** — Add case in `handleAction()`:
```java
case "checkoutBranch":
	checkoutSourceBranch();
	break;
```

**New method** `checkoutSourceBranch()`:
- Null-check `currentPullRequest`
- Create and schedule `CheckoutPullRequestBranchJob`

---

#### Task 4.2: Add "Checkout Branch" toolbar action to PR List View
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestListView.java` (line 409-417)

- Add a new `Action` field: `checkoutBranchAction`
- Initialize it with icon, text from `PRText`, and run handler
- Enable/disable based on selection (only enabled when a PR is selected)
- Add to toolbar via `toolBarManager.add(checkoutBranchAction)`
- Handler: get selected PR from table viewer, create and schedule `CheckoutPullRequestBranchJob`

---

#### Task 4.3: Add checkout icon
**Directory**: `org.eclipse.egit.pullrequest/icons/`

- Add a 16x16 checkout/branch-switch icon (e.g., `checkout.png`)
- Reference in the action's `setImageDescriptor()`
- Can reuse/adapt an EGit icon or create a simple branch arrow icon

---

### Phase 5: NLS & Strings (0.5 day)

#### Task 5.1: Add externalized strings
**Files**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/PRText.java`, `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/prtext.properties`

New strings needed:
```properties
CheckoutBranch_JobName=Checking out pull request branch
CheckoutBranch_ConfirmTitle=Checkout Branch
CheckoutBranch_ConfirmMessage=Checkout branch ''{0}'' from {1}?\n\nLatest commit: {2}
CheckoutBranch_ErrorNoRepository=Could not find local Git repository for this pull request
CheckoutBranch_ErrorFetchFailed=Failed to fetch branch ''{0}'' from remote
CheckoutBranch_ActionLabel=Checkout Branch
CheckoutBranch_ActionTooltip=Checkout the source branch of this pull request
```

---

### Phase 6: MANIFEST.MF Updates (if needed)

#### Task 6.1: Verify import packages
**File**: `org.eclipse.egit.pullrequest/META-INF/MANIFEST.MF`

Current state:
- `Require-Bundle`: `org.eclipse.egit.core`, `org.eclipse.egit.ui` — already present (provides `FetchOperation`, `BranchOperationUI`, `BranchOperation`, `CreateLocalBranchOperation`)
- `Import-Package`: `org.eclipse.jgit.lib`, `org.eclipse.jgit.transport` — already present (provides `Repository`, `RemoteConfig`, `URIish`, `RefSpec`)

Likely additions:
- `org.eclipse.jgit.api;version="[7.6.0,7.7.0)"` — may be needed if `FetchOperation` internally requires it on the classpath
- `org.eclipse.jgit.revwalk;version="[7.6.0,7.7.0)"` — if we need `RevCommit` for branch creation

**Note**: Since `BranchOperationUI` and `FetchOperation` are in `Require-Bundle` deps, most JGit transitive deps should resolve. Verify at build time.

---

### Phase 7: Testing (1 day)

#### Task 7.1: Unit tests for `RepositoryResolver`
**File**: New test in `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/util/`

- Test URL matching logic for both GitHub and Bitbucket patterns
- Test null/missing remote handling

#### Task 7.2: Unit tests for fork detection logic
- Test same-repo detection (fromRef.repo == toRef.repo)
- Test fork detection (different slugs/projects)
- Test clone URL construction

#### Task 7.3: Manual integration testing
- Test with a real GitHub same-repo PR
- Test with a real GitHub fork PR
- Test with dirty working tree → stash dialog
- Test with branch already existing locally
- Verify workspace/package explorer updates after checkout

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| `BranchOperationUI` is in `internal` package | Medium | It's stable EGit API used throughout EGit itself; acceptable for a tightly coupled plugin |
| Fork PR clone URL missing from API response | Low | GitHub always returns `clone_url`; Bitbucket returns clone links. Fallback: construct URL from server + project + slug |
| Auth for private fork repositories | Medium | `FetchOperation` supports `CredentialsProvider`; EGit's credential manager handles this |
| Branch name collision (local branch already exists) | Low | `BranchOperationUI` handles this — it switches to existing branch or shows create dialog |

---

## File Summary

| Action | File |
|--------|------|
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequest.java` — add `cloneUrl` to `Repository` |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParser.java` — extract `clone_url` |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketJsonParser.java` — extract clone links |
| Create | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/util/RepositoryResolver.java` — shared repo resolution |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestChangedFilesView.java` — delegate to `RepositoryResolver` |
| Create | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CheckoutPullRequestBranchJob.java` — core checkout logic |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewHtmlRenderer.java` — add button |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewView.java` — handle action |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestListView.java` — toolbar action |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/PRText.java` — NLS fields |
| Modify | `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/prtext.properties` — NLS strings |
| Modify | `org.eclipse.egit.pullrequest/META-INF/MANIFEST.MF` — add imports if needed |
| Add | `org.eclipse.egit.pullrequest/icons/checkout.png` — toolbar icon |
| Add | Tests for parser changes, resolver, fork detection |

**Estimated effort**: 4-5 days

---

## Acceptance Criteria (from Issue #11)

- [x] "Checkout Branch" button/action available in PR detail view
- [x] Action fetches remote branch if not available locally
- [x] Switches active branch to PR source branch
- [x] Handles uncommitted local changes gracefully (via `BranchOperationUI`)
- [x] Shows confirmation dialog with branch information
- [x] Works for both GitHub and Bitbucket repositories
- [x] Integrates with EGit branch switching UI patterns (`BranchOperationUI`)
- [x] Updates Eclipse workspace and package explorer (handled by `BranchOperationUI`)
