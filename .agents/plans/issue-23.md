# Implementation Plan: Issue #23

**Title**: Add commit range review support for multi-cycle PR reviews
**Issue Link**: https://github.com/Philipp0205/eclipse-pull-request-plugin/issues/23
**Created**: 2026-02-25
**Labels**: enhancement

---

## Overview

Add the ability to review changes between specific commits within a pull request. Currently the plugin always compares the entire PR (source branch HEAD vs target branch). This feature enables reviewers to select a commit range and see only incremental changes -- critical for multi-cycle review workflows where a reviewer returns after the author has pushed new commits.

The implementation is split into four phases, each delivering incremental value.

## Requirements Analysis

**Key Requirements:**
- Display the chronological list of commits in a PR
- Allow selecting a commit range (from/to) within the PR
- Filter the Changed Files view to show only files changed in the selected range
- Update the Compare Editor to diff the selected commit range instead of full branch comparison
- Filter inline comments to show only those relevant to the selected range
- Work for both GitHub and Bitbucket Data Center providers

**Acceptance Criteria (from issue):**

Phase 1:
- [ ] Commits view displays all commits in chronological order
- [ ] Commit list shows SHA (short), message (first line), author, and timestamp
- [ ] Double-clicking a commit shows full details (message, changed files)
- [ ] Works for both GitHub and Bitbucket providers

Phase 2:
- [ ] User can select a commit range (start and end)
- [ ] Changed Files view updates to show only files changed in selected range
- [ ] Clear visual indication of which range is currently selected
- [ ] "View entire PR" option to reset to full diff

Phase 3:
- [ ] Compare editor title shows commit SHAs instead of branch names
- [ ] Diff accurately reflects changes between selected commits
- [ ] Opening a file from filtered Changed Files view shows correct range diff
- [ ] Inline comments are filtered to relevant commits

**Dependencies:**
- No new external library dependencies required
- Existing `HttpURLConnection`-based HTTP layer is sufficient
- Existing Eclipse Compare framework integration is reused

**Issue Type:** Enhancement (feature request)

## Technical Approach

### Architecture Decisions

1. **Follow existing provider-agnostic pattern**: New methods go into `IPullRequestClient` interface; `GitHubClient` and `BitbucketClient` each implement their own API calls.

2. **Reuse hand-rolled JSON parsing**: The project does not use Gson/Jackson. Both `GitHubJsonParser` and `BitbucketJsonParser` use custom `extractString()`, `extractObject()`, `extractLong()`, `findMatchingBrace()` methods. New commit parsing must follow the same approach.

3. **Leverage existing `getFileContent(commitId, path)`**: This method already accepts arbitrary commit SHAs, so the compare editor can already load file contents at specific commits. The main work is wiring commit range selection to the diff viewer.

4. **Bitbucket's native range support**: Bitbucket Data Center's `/pull-requests/{id}/changes` endpoint supports `sinceId` and `untilId` query parameters natively -- commit range diffs are straightforward.

5. **GitHub compare endpoint**: GitHub doesn't support range params on the PR files endpoint. Use `GET /repos/{owner}/{repo}/compare/{base}...{head}` for commit range diffs, parsing the `files` array from the response.

6. **View communication via Eclipse selection service**: The new Commits View communicates selected commit range to the Changed Files View using Eclipse's `ISelectionService` -- the same mechanism used by existing views.

### Affected Components

| Component | Action | Complexity |
|-----------|--------|------------|
| `PullRequestCommit.java` (model) | **Create** | Low |
| `IPullRequestClient.java` | **Modify** -- add 2 methods | Low |
| `GitHubClient.java` | **Modify** -- implement 2 methods | Medium |
| `GitHubJsonParser.java` | **Modify** -- add `parseCommits()`, `parseCompareFiles()` | Medium |
| `BitbucketClient.java` | **Modify** -- implement 2 methods | Medium |
| `BitbucketJsonParser.java` | **Modify** -- add `parseCommits()` | Medium |
| `PullRequestCommitsView.java` (UI) | **Create** | High |
| `PullRequestChangedFilesView.java` | **Modify** -- accept range, filter files | Medium |
| `PullRequestCompareEditorInput.java` | **Modify** -- accept commit SHAs | Medium |
| `plugin.xml` | **Modify** -- register new view | Low |
| `PRText.java` + `prtext.properties` | **Modify** -- add NLS strings | Low |
| `MANIFEST.MF` | No changes needed | -- |
| Tests | **Create** -- parser tests for new JSON | Medium |

### Potential Challenges

1. **GitHub compare endpoint pagination**: The compare endpoint returns max 300 files. For very large ranges, pagination via `page`/`per_page` may be needed.
2. **Merge commits**: PR commit lists may include merge commits. These should be visually distinguishable and optionally filterable.
3. **Comment anchoring across ranges**: Inline comments reference specific file lines. When viewing a subset of commits, a comment's line may not exist in the range diff. Need graceful handling (show as "outdated" or hide).
4. **Force-pushed branches**: If the PR branch was force-pushed, commit history may have been rewritten. The API still returns the current commits, but previously reviewed commits may be gone.

## Implementation Steps

### Phase 1: Basic Commit History Display

#### Step 1.1: Create `PullRequestCommit` Model
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequestCommit.java` (new)
**Complexity**: Low

Create a simple POJO following the existing model patterns (`PullRequest.java`, `ChangedFile.java`):

```java
public class PullRequestCommit {
    private final String id;          // full SHA
    private final String message;     // full commit message
    private final String authorName;
    private final String authorEmail;
    private final long authorDate;    // epoch millis
    private final List<String> parents; // parent SHAs

    // Constructor, getters
    // getShortId() -- returns first 7 chars of SHA
    // getFirstLine() -- returns first line of message
    // isMergeCommit() -- parents.size() > 1
}
```

Follow project conventions:
- EPL-2.0 license header
- Javadoc on all public members
- Immutable (all fields `final`, set via constructor)
- Use `Collections.unmodifiableList()` for parents

#### Step 1.2: Add `getPullRequestCommits()` to Client Interface
**File**: `IPullRequestClient.java` -- insert after existing methods (~line 367)
**Complexity**: Low

```java
/**
 * Get the list of commits in the pull request.
 *
 * @param pullRequestId
 *            the pull request ID
 * @return list of commits in chronological order
 * @throws IOException
 *             if an I/O error occurs
 */
List<PullRequestCommit> getPullRequestCommits(long pullRequestId)
        throws IOException;
```

#### Step 1.3: Implement GitHub Commit Fetching
**File**: `GitHubClient.java` -- add new method
**Complexity**: Medium

API endpoint: `GET /repos/{owner}/{repo}/pulls/{number}/commits`
- Use existing `doGetAllPages()` for pagination (reuses `Link` header parsing)
- Response is a JSON array of commit objects

**File**: `GitHubJsonParser.java` -- add `parseCommits(String json)` and `parseCommit(String json)`
**Complexity**: Medium

Extract from each commit object:
- `sha` -> `id`
- `commit.message` -> `message`
- `commit.author.name` -> `authorName`
- `commit.author.email` -> `authorEmail`
- `commit.author.date` -> `authorDate` (parse ISO 8601)
- `parents[].sha` -> `parents` list

Use existing patterns: `extractString()`, `extractObject()`, `extractArray()`, `findMatchingBrace()`.

#### Step 1.4: Implement Bitbucket Commit Fetching
**File**: `BitbucketClient.java` -- add new method
**Complexity**: Medium

API endpoint: `/rest/api/1.0/projects/{key}/repos/{slug}/pull-requests/{id}/commits`
- Response uses Bitbucket's paginated `values` array pattern
- Handle pagination with `start`/`limit` parameters

**File**: `BitbucketJsonParser.java` -- add `parseCommits(String json)` and `parseCommit(String json)`
**Complexity**: Medium

Bitbucket commit object fields:
- `id` -> `id` (SHA)
- `message` -> `message`
- `author.name` -> `authorName`
- `author.emailAddress` -> `authorEmail`
- `authorTimestamp` -> `authorDate` (epoch millis)
- `parents[].id` -> `parents` list

#### Step 1.5: Create `PullRequestCommitsView`
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestCommitsView.java` (new)
**Complexity**: High

Follow patterns from existing `PullRequestChangedFilesView` (891 lines) and `PullRequestCommentsView` (1594 lines):

- Extend `ViewPart`
- Use `TableViewer` with columns: SHA (short), Message (first line), Author, Date (relative)
- Load commits via `IPullRequestClient.getPullRequestCommits()` in a background `Job`
- Listen for PR selection changes via `ISelectionService` (same as other views)
- Toolbar actions: Refresh, Toggle merge commits
- Double-click handler: Show full commit message in a detail panel or popup
- Register view ID constant: `"org.eclipse.egit.pullrequest.commitsView"`

#### Step 1.6: Register View in Plugin Configuration
**File**: `plugin.xml` -- add new `<view>` element inside the existing `<extension point="org.eclipse.ui.views">` block
**Complexity**: Low

Add the view registration after the existing three views (around line 25):
```xml
<view
    id="org.eclipse.egit.pullrequest.commitsView"
    name="%PullRequestCommitsView.name"
    category="org.eclipse.egit.pullrequest.viewCategory"
    class="org.eclipse.egit.pullrequest.internal.ui.PullRequestCommitsView"
    icon="icons/obj16/pull-request.png" />
```

Add to perspective layout in the `<perspectiveExtension>` (around line 58).

**File**: `plugin.properties` -- add:
```properties
PullRequestCommitsView.name=Pull Request Commits
```

#### Step 1.7: Add NLS Strings
**File**: `PRText.java` -- add static String fields
**File**: `prtext.properties` -- add corresponding entries
**Complexity**: Low

New keys needed:
```properties
PullRequestCommitsView_Title=Commits
PullRequestCommitsView_ColumnSha=SHA
PullRequestCommitsView_ColumnMessage=Message
PullRequestCommitsView_ColumnAuthor=Author
PullRequestCommitsView_ColumnDate=Date
PullRequestCommitsView_RefreshTooltip=Refresh commits
PullRequestCommitsView_ToggleMergeCommits=Show merge commits
PullRequestCommitsView_Loading=Loading commits...
PullRequestCommitsView_NoCommits=No commits found
PullRequestCommitsView_Error=Error loading commits
```

#### Step 1.8: Add Tests for Commit Parsing
**File**: `org.eclipse.egit.pullrequest.test/src/.../github/GitHubJsonParserTest.java` -- add test methods
**File**: `org.eclipse.egit.pullrequest.test/src/.../bitbucket/BitbucketClientTest.java` -- add test methods
**Complexity**: Medium

Test cases:
- Parse single commit with all fields
- Parse commit list (multiple commits)
- Parse merge commit (multiple parents)
- Parse commit with multi-line message (verify first-line extraction)
- Handle missing optional fields gracefully

---

### Phase 2: Commit Range Selection

#### Step 2.1: Add Range Selection UI to Commits View
**File**: `PullRequestCommitsView.java` -- modify
**Complexity**: High

Add commit range selection mechanism:
- Two combo/dropdown selectors in a toolbar area: "From" and "To"
- "View Entire PR" button/action to clear range selection
- Selected range visually highlighted in the table
- Fire selection change event when range changes

Design decision: Use **two CCombo dropdowns** above the table:
```
[From: abc1234 - Initial impl  ] [To: 789abcd - Add tests  ] [Clear]
```

#### Step 2.2: Add `getPullRequestChangesInRange()` to Client Interface
**File**: `IPullRequestClient.java` -- insert after `getPullRequestCommits()`
**Complexity**: Low

```java
/**
 * Get changed files in a commit range within a pull request.
 *
 * @param pullRequestId
 *            the pull request ID
 * @param sinceCommitId
 *            the start commit SHA (exclusive)
 * @param untilCommitId
 *            the end commit SHA (inclusive)
 * @return list of files changed in the range
 * @throws IOException
 *             if an I/O error occurs
 */
List<ChangedFile> getPullRequestChangesInRange(
        long pullRequestId, String sinceCommitId,
        String untilCommitId) throws IOException;
```

#### Step 2.3: Implement GitHub Range Diff
**File**: `GitHubClient.java` -- add new method
**Complexity**: Medium

API endpoint: `GET /repos/{owner}/{repo}/compare/{base}...{head}`
- `base` = sinceCommitId, `head` = untilCommitId
- Response has `files` array (same format as PR files endpoint)
- Reuse existing `GitHubJsonParser.parseChangedFiles()` or create `parseCompareFiles()`

Note: The compare endpoint caps at 300 files.

#### Step 2.4: Implement Bitbucket Range Diff
**File**: `BitbucketClient.java` -- add new method
**Complexity**: Medium

Bitbucket natively supports range params on the existing changes endpoint:
```
GET /rest/api/1.0/projects/{key}/repos/{slug}/pull-requests/{id}/changes?sinceId={sha}&untilId={sha}
```
- Reuse existing `BitbucketJsonParser.parseChangedFiles()` as-is

#### Step 2.5: Wire Range Selection to Changed Files View
**File**: `PullRequestChangedFilesView.java` -- modify `loadPullRequest()` method (~lines 323-409)
**Complexity**: Medium

- Listen for range selection events from the Commits View
- When a range is selected, call `getPullRequestChangesInRange()` instead of `getPullRequestChanges()`
- When range is cleared, revert to `getPullRequestChanges()` (full PR diff)
- Store current range as view state: `currentFromCommit`, `currentToCommit`
- Update view title/description to show range info

#### Step 2.6: Add Tests for Range Diff Parsing
**Complexity**: Medium

- Test GitHub compare endpoint response parsing
- Test Bitbucket range params produce correct URL
- Test empty range returns no files
- Test range with renames/moves

---

### Phase 3: Range-Aware Compare Editor

#### Step 3.1: Extend Compare Editor to Accept Commit SHAs
**File**: `PullRequestCompareEditorInput.java` -- modify constructor and `createCompareInput()` (~lines 186-238)
**Complexity**: Medium

Add an overloaded constructor:
```java
PullRequestCompareEditorInput(IPullRequestClient client,
        PullRequest pullRequest, PullRequestChangedFile file,
        String fromCommitId, String toCommitId)
```

In `createCompareInput()`:
- If `fromCommitId`/`toCommitId` are set, use them instead of branch displayIds
- Update the compare editor title to show commit info

#### Step 3.2: Update File Opening from Changed Files View
**File**: `PullRequestChangedFilesView.java` -- modify `openCompareEditor()` (~lines 411-501)
**Complexity**: Low

- Pass the current range's commit SHAs to `PullRequestCompareEditorInput`
- When no range is selected, pass null (existing behavior)

#### Step 3.3: Filter Inline Comments by Commit Range
**File**: `PullRequestChangedFilesView.java` and/or `PullRequestCommentsView.java`
**Complexity**: High

Recommendation: Start with filtering comments to only those on files present in the current changed files list. This handles the majority of cases without requiring complex line mapping.

#### Step 3.4: Add NLS Strings for Range Display
**Files**: `PRText.java`, `prtext.properties`
**Complexity**: Low

```properties
PullRequestCompareEditor_RangeTitle={0}..{1}
PullRequestChangedFilesView_RangeLabel=Showing changes: {0}..{1} ({2} commits)
PullRequestChangedFilesView_FullPR=Showing all changes
```

---

### Phase 4: Review Cycle Tracking (Future)

This phase is marked as a future enhancement.

#### Step 4.1: Fetch Review Timeline
- GitHub: `GET /repos/{owner}/{repo}/pulls/{number}/reviews` returns reviews with `submitted_at` and `commit_id`
- Bitbucket: Activity endpoint includes review events

#### Step 4.2: "Changes Since Last Review" Quick-Select
- Determine the commit SHA at which the current user last submitted a review
- Auto-select the range from that commit to PR HEAD
- Add toolbar button: "Changes since my last review"

#### Step 4.3: Visual Indicators
- Badge on commits not yet reviewed
- Color coding in commit list
- Persist review state locally or derive from API data

## Testing Strategy

### Unit Tests

| Test Class | Test Focus | Priority |
|------------|-----------|----------|
| `GitHubJsonParserTest` | `parseCommits()` -- single, multiple, merge commits | High |
| `GitHubJsonParserTest` | `parseCompareFiles()` -- compare endpoint response format | High |
| `BitbucketClientTest` | `parseCommits()` -- single, multiple, merge commits | High |
| `BitbucketClientTest` | Range URL construction with `sinceId`/`untilId` params | High |
| New: `PullRequestCommitTest` | Model construction, `getShortId()`, `isMergeCommit()` | Medium |

### Integration/Manual Testing

1. Open a PR with 5+ commits in the Pull Request perspective
2. Verify the Commits View shows all commits in chronological order
3. Select a commit range using the From/To dropdowns
4. Verify Changed Files view updates to show only files in the range
5. Open a file from the filtered list -- verify diff shows range changes
6. Click "View Entire PR" -- verify it resets to full diff
7. Test with GitHub PRs and Bitbucket PRs separately
8. Test edge cases:
   - PR with single commit
   - PR with merge commits
   - PR with 100+ commits (pagination)
   - Selecting the same commit as From and To

### Edge Cases to Consider

- Force-pushed branches (commit history rewritten)
- PRs with no commits yet (draft PRs)
- Network errors during commit fetching
- Very long commit messages (truncation in table)
- Commits by different authors
- Time zones in commit timestamps

## Acceptance Criteria Checklist

### Phase 1
- [ ] `PullRequestCommit` model class created with all required fields
- [ ] `getPullRequestCommits()` added to `IPullRequestClient`
- [ ] GitHub implementation fetches commits via REST API with pagination
- [ ] Bitbucket implementation fetches commits with pagination
- [ ] `PullRequestCommitsView` displays commits in a table
- [ ] View registered in `plugin.xml` and added to perspective
- [ ] All user-facing strings externalized via `PRText`/NLS
- [ ] JSON parser tests pass for both providers
- [ ] `mvn clean verify` passes

### Phase 2
- [ ] Commit range selection UI (From/To dropdowns) works
- [ ] `getPullRequestChangesInRange()` added to client interface
- [ ] GitHub implementation uses compare endpoint
- [ ] Bitbucket implementation uses `sinceId`/`untilId` params
- [ ] Changed Files view updates based on selected range
- [ ] "View Entire PR" resets to full diff
- [ ] Range selection persists during view lifecycle
- [ ] Tests pass for range diff parsing

### Phase 3
- [ ] Compare editor accepts commit SHAs for left/right sides
- [ ] Editor title shows commit info when range is active
- [ ] Comments filtered to files in current range
- [ ] Default behavior (no range) is unchanged

### Standard Checks
- [ ] All new files have EPL-2.0 license headers
- [ ] Tab indentation used throughout (no spaces)
- [ ] Lines stay under 80 characters
- [ ] No wildcard imports
- [ ] Javadoc on all public/protected members
- [ ] No `System.out.println()` -- use `Activator.log*()` methods
- [ ] Non-translatable strings marked with `// $NON-NLS-1$`

## Additional Considerations

### Performance
- **Commit list caching**: Cache the commit list per PR. Invalidate on refresh action or when PR data changes.
- **Background loading**: All API calls must run in Eclipse `Job` instances to avoid blocking the UI thread.
- **Lazy diff loading**: Only fetch the range diff when a range is actually selected.
- **Pagination**: GitHub returns max 250 commits per page; Bitbucket uses `start`/`limit`. Both clients must handle multi-page responses.

### Security
- No new authentication surfaces -- reuses existing token-based auth.
- Commit SHAs are passed as URL path segments; ensure proper URL encoding.

### Backward Compatibility
- **Fully backward compatible**: Default behavior (no range selected) produces identical results to current implementation.
- **No API changes to existing methods**: `getPullRequestChanges()` remains unchanged. New methods are additive.
- **View layout**: New Commits View is added to the perspective but existing view positions are not changed.
- **Minimum Eclipse version**: No new Eclipse platform APIs needed beyond what's already in the target platform.

### Future Extensibility
- The `PullRequestCommit` model can later be extended with `changedFiles` count, `stats`, or `verified` fields.
- Phase 4 (review cycle tracking) builds naturally on Phase 1-3 infrastructure.
- The commit range concept could later support "Compare with working tree" for local changes.
- The architecture supports adding more providers (GitLab, Azure DevOps) since the interface is provider-agnostic.

### Dependencies on Other Issues
- No blocking dependencies identified.
- The existing `feature/issue-02-submit-reviews` branch may interact with Phase 4 but does not block Phases 1-3.
