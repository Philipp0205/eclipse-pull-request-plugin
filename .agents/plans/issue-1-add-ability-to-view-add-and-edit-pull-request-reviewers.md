# Implementation Plan: Add Pull Request Reviewer Management

**Issue**: #1  
**Repository**: Philipp0205/eclipse-pull-request-plugin  
**Author**: Philipp K. (@Philipp0205)  
**Created**: 2026-02-19  
**Updated**: 2026-02-22  
**Priority**: High  
**Complexity**: Large  
**Status**: ✅ **IMPLEMENTATION COMPLETE** - All core features implemented and ready for testing

---

## Current Implementation Status

### ✅ **COMPLETED** (All 6 Phases)

**Phase 1: Foundation - Data Model & Parsing** ✅
- Extended `PullRequest` model with reviewers field
- Updated `BitbucketJsonParser` to parse reviewers
- Updated `GitHubJsonParser` to parse reviewers (including teams)
- All unit tests passing

**Phase 2: API Client Implementation** ✅
- Added reviewer management methods to `IPullRequestClient` interface
- Implemented all methods in `BitbucketClient`
- Implemented all methods in `GitHubClient`

**Phase 3: UI Display Layer** ✅
- Added "Reviewers" column to `PullRequestListView`
- Added reviewers section to `PullRequestOverviewView`
- All UI strings externalized to `prtext.properties` and `PRText.java`

**Phase 4: Interactive Management** ✅
- Created `ReviewerManagementDialog.java` with full add/remove functionality
- Created `ManageReviewersAction.java`

**Phase 5: Quick Actions** ✅
- Created `AddMyselfAsReviewerAction.java`

**Phase 6: Testing & Documentation** ✅
- Added comprehensive unit tests for both parsers
- All tests in `GitHubJsonParserTest.java` (5 new test cases)
- All tests in `BitbucketJsonParserTest.java` (6 new test cases)

**UI Wiring** ✅ **JUST COMPLETED**
- Added "Manage Reviewers" button to `PullRequestOverviewView`
- Added context menu to `PullRequestListView` with reviewer actions
- Users can now access reviewer management via:
  - **Right-click menu** in Pull Request List View
  - **"Manage Reviewers" button** in Pull Request Overview View
  - **Context menu** with "Add Myself as Reviewer" quick action

### 📍 Where Users Can Edit Reviewers Now

**Option 1: From Pull Request List View**
1. Right-click on any pull request in the list
2. Select "Manage Reviewers" from context menu
3. Add or remove reviewers in the dialog

**Option 2: From Pull Request Overview View**
1. Double-click a pull request to open the overview
2. Click the "Manage Reviewers" button in the Actions section
3. Add or remove reviewers in the dialog

**Quick Action: Add Yourself as Reviewer**
1. Right-click on a pull request in the list
2. Select "Add Myself as Reviewer" from context menu
3. You're automatically added without opening a dialog

---

## Overview

This issue adds comprehensive reviewer management functionality to the Eclipse Pull Request Plugin, enabling users to view, add, and remove reviewers for pull requests in both GitHub and Bitbucket Data Center. This is a core feature for PR review workflows and will significantly enhance the plugin's usability.

The implementation spans multiple layers:
- **Data Model**: Extending the PullRequest model to store reviewer information
- **API Clients**: Implementing reviewer operations for both GitHub and Bitbucket
- **UI Layer**: Creating views, columns, dialogs, and actions for reviewer management
- **Testing**: Ensuring reliability across both providers

---

## Technical Approach

### Architecture Strategy

1. **Provider-Agnostic Model**: The `PullRequest` and `PullRequestParticipant` models will remain provider-neutral, storing reviewer data in a common format that works for both GitHub and Bitbucket.

2. **Interface-Based API**: The `IPullRequestClient` interface will define standard methods for reviewer operations (`getReviewers()`, `addReviewer()`, `removeReviewer()`), with provider-specific implementations handling API differences.

3. **Layered UI Updates**: 
   - Basic display (reviewers column in list view)
   - Interactive management (dialog for add/remove)
   - Quick actions (context menu shortcuts)

4. **Incremental Development**: Build foundation (model + parsing) → API operations → UI display → Interactive management → Polish

### Key Design Decisions

- **Reviewer Status Handling**: Use icons/decorators to indicate approval status (✓ for approved, ○ for pending)
- **Permission Validation**: Perform client-side checks before API calls to provide immediate feedback
- **Error Handling**: Graceful degradation if reviewer operations fail (e.g., permission denied)
- **Refresh Strategy**: Auto-refresh affected views after reviewer modifications

---

## Task Breakdown

### Phase 1: Foundation - Data Model & Parsing (2-3 days)

#### Task 1.1: Extend PullRequest Model
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequest.java`

- Add `private List<PullRequestParticipant> reviewers` field
- Add getter: `public List<PullRequestParticipant> getReviewers()`
- Add setter: `public void setReviewers(List<PullRequestParticipant> reviewers)`
- Ensure defensive copying to prevent external modification
- Update constructor/builder if applicable
- Add Javadoc explaining reviewer vs. participant distinction

**Acceptance**: 
- Reviewers list is properly encapsulated
- Null-safety is enforced (return empty list instead of null)

---

#### Task 1.2: Update BitbucketJsonParser for Reviewers
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketJsonParser.java`

- Locate the `parsePullRequest()` method
- Add parsing logic for the `reviewers` array from JSON response
- For each reviewer object:
  - Parse `user` object → `PullRequestParticipant`
  - Parse `role` field (should be "REVIEWER")
  - Parse `approved` boolean status
  - Parse `status` field (APPROVED, UNAPPROVED, NEEDS_WORK)
- Set the reviewers list on the `PullRequest` object
- Handle missing/null reviewers array gracefully

**Example Bitbucket JSON Structure**:
```json
{
  "id": 101,
  "reviewers": [
    {
      "user": {
        "name": "jsmith",
        "displayName": "John Smith",
        "emailAddress": "john@example.com"
      },
      "role": "REVIEWER",
      "approved": true,
      "status": "APPROVED"
    }
  ]
}
```

**Acceptance**:
- Parser correctly extracts all reviewer fields
- Empty reviewer arrays don't cause errors
- Unit tests pass (add to `BitbucketClientTest`)

---

#### Task 1.3: Create GitHub Reviewer Parser
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParser.java`

- Add method: `public static List<PullRequestParticipant> parseReviewers(JsonNode reviewersNode)`
- Parse GitHub's `requested_reviewers` array
- Handle both individual users and teams (teams have different structure)
- Map GitHub user data to `PullRequestParticipant`:
  - `login` → username
  - `name` or `login` → displayName
  - Set role as "REVIEWER"
- For teams: include team indicator in participant data
- Note: GitHub requested_reviewers are pending; approved reviewers come from reviews

**GitHub API Response Structure**:
```json
{
  "requested_reviewers": [
    {
      "login": "octocat",
      "id": 1,
      "type": "User"
    }
  ],
  "requested_teams": [
    {
      "name": "core-team",
      "id": 1,
      "slug": "core-team"
    }
  ]
}
```

**Acceptance**:
- Both users and teams are parsed correctly
- Parser handles empty arrays
- Unit tests cover edge cases

---

### Phase 2: API Client Implementation (3-4 days)

#### Task 2.1: Define IPullRequestClient Interface Methods
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/IPullRequestClient.java`

Add the following methods to the interface:

```java
/**
 * Retrieves the list of reviewers for a pull request.
 *
 * @param pullRequestId
 *            the pull request identifier
 * @return list of reviewers, never null
 * @throws IOException
 *             if the request fails
 */
List<PullRequestParticipant> getReviewers(String pullRequestId) throws IOException;

/**
 * Adds a reviewer to a pull request.
 *
 * @param pullRequestId
 *            the pull request identifier
 * @param username
 *            the username to add as reviewer
 * @throws IOException
 *             if the request fails
 */
void addReviewer(String pullRequestId, String username) throws IOException;

/**
 * Removes a reviewer from a pull request.
 *
 * @param pullRequestId
 *            the pull request identifier
 * @param username
 *            the username to remove
 * @throws IOException
 *             if the request fails
 */
void removeReviewer(String pullRequestId, String username) throws IOException;

/**
 * Adds multiple reviewers to a pull request.
 *
 * @param pullRequestId
 *            the pull request identifier
 * @param usernames
 *            the list of usernames to add as reviewers
 * @throws IOException
 *             if the request fails
 */
void addReviewers(String pullRequestId, List<String> usernames) throws IOException;
```

**Acceptance**:
- Interface compiles without errors
- Javadoc is complete and follows Eclipse style
- Methods throw appropriate exceptions

---

#### Task 2.2: Implement Bitbucket Reviewer Operations
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClient.java`

**Sub-task 2.2a: Implement getReviewers()**
- Usually reviewers come with the full PR payload, so extract from existing response
- If needed, make GET request to `/rest/api/1.0/projects/{project}/repos/{repo}/pull-requests/{id}`
- Parse using `BitbucketJsonParser`
- Return reviewer list

**Sub-task 2.2b: Implement addReviewer()**
- Construct URL: `/rest/api/1.0/projects/{project}/repos/{repo}/pull-requests/{id}/participants/{username}`
- Make PUT request with JSON body:
  ```json
  {
    "user": {
      "name": "{username}"
    },
    "role": "REVIEWER"
  }
  ```
- Handle HTTP responses:
  - 200/201: Success
  - 401: Authentication failure
  - 403: Permission denied
  - 404: PR or user not found
- Throw descriptive `IOException` on failure

**Sub-task 2.2c: Implement removeReviewer()**
- Construct URL: `/rest/api/1.0/projects/{project}/repos/{repo}/pull-requests/{id}/participants/{username}`
- Make DELETE request
- Handle similar HTTP response codes
- Note: Can only remove reviewers who haven't approved

**Sub-task 2.2d: Implement addReviewers()**
- Iterate through usernames list
- Call `addReviewer()` for each
- Consider batching if Bitbucket API supports it (check docs)
- Collect errors and throw aggregated exception if any fail

**Testing**:
- Add tests to `BitbucketClientTest` using mock HTTP responses
- Test error scenarios (404, 403, etc.)
- Verify request URLs and payloads are correct

**Acceptance**:
- All methods implemented with proper error handling
- Tests pass
- Code follows project style (tabs, 80-char lines, no wildcards)

---

#### Task 2.3: Implement GitHub Reviewer Operations
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubClient.java`

**Sub-task 2.3a: Implement getReviewers()**
- Make GET request to `/repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers`
- Parse response using `GitHubJsonParser`
- Merge with completed reviews from `/repos/{owner}/{repo}/pulls/{pull_number}/reviews`
- Return combined list with approval status

**Sub-task 2.3b: Implement addReviewer()**
- Construct URL: `/repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers`
- Make POST request with JSON body:
  ```json
  {
    "reviewers": ["{username}"]
  }
  ```
- Handle authentication via existing OAuth mechanism
- Process response and handle errors

**Sub-task 2.3c: Implement removeReviewer()**
- Construct URL: `/repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers`
- Make DELETE request with JSON body:
  ```json
  {
    "reviewers": ["{username}"]
  }
  ```
- Handle response codes appropriately

**Sub-task 2.3d: Implement addReviewers()**
- GitHub API already supports batch operations
- Make single POST request with all usernames in `reviewers` array
- More efficient than Bitbucket approach

**Testing**:
- Add tests to `GitHubJsonParserTest`
- Mock GitHub API responses
- Test team reviewer handling
- Verify OAuth headers are included

**Acceptance**:
- All methods work with GitHub API
- Handles both users and teams
- Error handling is robust

---

### Phase 3: UI Display Layer (2-3 days)

#### Task 3.1: Add Reviewers Column to Pull Request List View
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestListView.java`

- Locate the `createColumns()` or similar method where table columns are defined
- Add new `TableViewerColumn` for "Reviewers"
- Set column label provider to display reviewer names:
  - Format: "John Smith, Jane Doe (✓)" where ✓ indicates approved
  - Truncate if too many reviewers, show count: "+3 more"
- Add tooltip support showing full reviewer list with approval status
- Set appropriate column width (e.g., 200px)
- Make column sortable if applicable

**Implementation Details**:
```java
// Column label provider pseudocode
public String getText(Object element) {
    PullRequest pr = (PullRequest) element;
    List<PullRequestParticipant> reviewers = pr.getReviewers();
    if (reviewers.isEmpty()) {
        return ""; //$NON-NLS-1$
    }
    // Format reviewer names with status indicators
    return formatReviewers(reviewers);
}
```

**Acceptance**:
- Column appears in PR list view
- Reviewer names display correctly
- Approval status is indicated with visual markers
- Tooltip shows complete information
- Column respects Eclipse theming

---

#### Task 3.2: Add Reviewer Section to Pull Request Details View
**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestDetailsView.java` (or equivalent)

- Add "Reviewers" section to the details panel
- Display each reviewer with:
  - Name
  - Approval status (icon + text)
  - Avatar if available (optional enhancement)
- Group by status: Approved, Pending, Rejected
- Add refresh mechanism to update when reviewers change

**Acceptance**:
- Reviewers section is visible in details view
- Information is clear and well-formatted
- Updates when PR is refreshed

---

### Phase 4: Interactive Management (3-4 days)

#### Task 4.1: Create Reviewer Management Dialog
**New File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/ReviewerManagementDialog.java`

Create an SWT/JFace dialog with the following features:

**Dialog Layout**:
```
+-------------------------------------------+
| Manage Reviewers for PR #123             |
+-------------------------------------------+
| Current Reviewers:                        |
| [ ] John Smith (Approved)      [Remove]  |
| [ ] Jane Doe (Pending)         [Remove]  |
+-------------------------------------------+
| Add Reviewers:                            |
| [Search users.....................] [Add] |
|                                           |
| Suggestions:                              |
| [ ] Alice Johnson                         |
| [ ] Bob Williams                          |
+-------------------------------------------+
|                          [Cancel] [Apply] |
+-------------------------------------------+
```

**Components**:
1. **Current Reviewers List**: 
   - TableViewer showing existing reviewers
   - Checkbox selection for bulk remove
   - "Remove" button for selected reviewers

2. **Add Reviewers Section**:
   - Text field with autocomplete for user search
   - "Add" button to add selected user
   - Suggestion list of frequent collaborators

3. **Action Buttons**:
   - "Cancel": Close without changes
   - "Apply": Submit changes to API

**Implementation Notes**:
- Extend `org.eclipse.jface.dialogs.Dialog`
- Use `TableViewer` for reviewer lists
- Implement autocomplete using `ContentProposalAdapter`
- Show progress indicator during API calls
- Display errors in dialog if operations fail

**String Externalization**:
- Add all UI strings to `prtext.properties`
- Use `PRText` class for loading strings

**Acceptance**:
- Dialog opens and displays current reviewers
- Users can add new reviewers via search
- Users can remove existing reviewers
- Changes are applied via API calls
- Errors are handled gracefully
- Dialog follows Eclipse UI conventions

---

#### Task 4.2: Implement User Search/Autocomplete
**Enhancement to ReviewerManagementDialog or separate utility class**

- Create method to fetch available users from the repository
- For Bitbucket: Use `/rest/api/1.0/projects/{project}/repos/{repo}/permissions/users`
- For GitHub: Use `/repos/{owner}/{repo}/collaborators`
- Implement caching to avoid repeated API calls
- Filter users based on text input
- Display suggestions in dropdown/list

**Acceptance**:
- Autocomplete suggests valid users
- Search is reasonably fast (<1 second)
- Works for both GitHub and Bitbucket

---

#### Task 4.3: Wire Dialog to Menu/Action
**Files**: 
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/actions/ManageReviewersAction.java` (new)
- `org.eclipse.egit.pullrequest/plugin.xml`

**Create Action Class**:
- Extend `org.eclipse.jface.action.Action` or `org.eclipse.ui.actions.BaseSelectionListenerAction`
- Override `run()` method to:
  1. Get selected `PullRequest` from view
  2. Validate user has permissions (optional: call API to check)
  3. Open `ReviewerManagementDialog`
  4. Refresh view after dialog closes

**Register in plugin.xml**:
- Add to context menu of PullRequestListView
- Add to toolbar of PullRequestDetailsView
- Assign keyboard shortcut (e.g., Ctrl+Shift+R)

**Icon**:
- Create/find suitable icon (e.g., person with plus sign)
- Place in `org.eclipse.egit.pullrequest/icons/`
- Reference in plugin.xml

**Acceptance**:
- Action appears in context menu when right-clicking PR
- Action is enabled only when a PR is selected
- Dialog opens when action is triggered
- View refreshes after changes

---

### Phase 5: Quick Actions & Polish (1-2 days)

#### Task 5.1: Add "Add Myself as Reviewer" Quick Action
**File**: New action class `AddMyselfAsReviewerAction.java`

- Create action that adds current authenticated user as reviewer
- No dialog needed - direct API call
- Show confirmation message on success
- Handle error if user already is a reviewer
- Add to context menu and toolbar

**Acceptance**:
- One-click adds current user as reviewer
- Appropriate feedback is shown
- Works for both providers

---

#### Task 5.2: Implement Auto-Refresh After Changes
**Files**: Various view classes

- After adding/removing reviewers, trigger refresh of:
  - Pull request list view
  - Pull request details view
  - Affected `PullRequest` object in memory
- Use Eclipse Jobs API for background refresh
- Show progress indicator during refresh

**Acceptance**:
- UI updates automatically after reviewer changes
- No manual refresh required
- User sees updated reviewer list immediately

---

#### Task 5.3: Add Keyboard Shortcuts
**File**: `plugin.xml`

Define keyboard bindings for reviewer actions:
- `Ctrl+Shift+R`: Manage Reviewers
- `Ctrl+Alt+R`: Add Myself as Reviewer

Register in appropriate command/keybinding extensions.

**Acceptance**:
- Keyboard shortcuts work in PR views
- Shortcuts are documented in help/tooltip text

---

### Phase 6: Testing & Documentation (2 days)

#### Task 6.1: Write Comprehensive Unit Tests

**For BitbucketClient**:
- Test parsing of reviewer data
- Test addReviewer() with mock HTTP responses
- Test removeReviewer() with various response codes
- Test error handling (404, 403, 500)

**For GitHubClient**:
- Test parsing requested_reviewers and teams
- Test batch add operations
- Test removal operations
- Test OAuth authentication inclusion

**For UI Components**:
- Test dialog opens with correct data
- Test action enablement logic
- Test string externalization (no hardcoded strings)

**Acceptance**:
- All new code has >80% test coverage
- Tests pass in headless mode (`mvn test`)
- No failing tests introduced

---

#### Task 6.2: Manual Testing Checklist

Test the following scenarios manually:

**Display Tests**:
- [ ] Reviewers column shows in PR list view
- [ ] Approval status indicators appear correctly
- [ ] Tooltip shows full reviewer information
- [ ] Details view displays reviewer section

**Functional Tests**:
- [ ] Open Manage Reviewers dialog from context menu
- [ ] Add a single reviewer via dialog
- [ ] Add multiple reviewers at once
- [ ] Remove a reviewer
- [ ] Add yourself as reviewer with quick action
- [ ] Keyboard shortcuts work

**Provider-Specific Tests**:
- [ ] Test all features with Bitbucket repository
- [ ] Test all features with GitHub repository
- [ ] Test with GitHub teams as reviewers

**Error Handling Tests**:
- [ ] Try to add non-existent user (should show error)
- [ ] Try to remove reviewer without permission (should show error)
- [ ] Test with no internet connection
- [ ] Test with invalid credentials

**Acceptance**:
- All manual test cases pass
- No crashes or unhandled exceptions
- Error messages are clear and helpful

---

#### Task 6.3: Update Documentation

**Files to update**:
- `README.md`: Add reviewer management to feature list
- User documentation (if exists): Add section on reviewer operations
- Code comments: Ensure all public methods have Javadoc

**String Externalization**:
- Verify all UI strings are in `prtext.properties`
- Add comments for translators where context is needed

**Acceptance**:
- Documentation is updated and accurate
- All strings are externalized
- Code is well-commented

---

## Dependencies

### External Libraries/APIs
- **Bitbucket Data Center REST API 1.0**: Used for all Bitbucket operations
  - Endpoints: `/rest/api/1.0/projects/{project}/repos/{repo}/pull-requests/*`
  - Authentication: Basic Auth or Personal Access Token
  
- **GitHub REST API v3**: Used for all GitHub operations
  - Endpoints: `/repos/{owner}/{repo}/pulls/{pull_number}/*`
  - Authentication: OAuth Device Flow (already implemented)

### Internal Dependencies
- **EGit Core**: For Git repository operations (already dependency)
- **JGit**: For Git data structures (already dependency)
- **Eclipse Platform UI**: For SWT/JFace widgets (already dependency)

### Model Dependencies
- `PullRequestParticipant`: Already exists, will be reused for reviewers
- `PullRequest`: Will be extended to include reviewers list

### No Blocking Issues
- All required infrastructure is already in place
- No other open issues need to be resolved first

---

## Testing Strategy

### Unit Testing
- **JSON Parsing Tests**: Verify BitbucketJsonParser and GitHubJsonParser correctly handle reviewer data
- **API Client Tests**: Mock HTTP responses and test client methods
- **Model Tests**: Ensure PullRequest correctly stores/retrieves reviewers

### Integration Testing
- **Bitbucket Integration**: Test against real Bitbucket Data Center instance (if available)
- **GitHub Integration**: Test against real GitHub repository
- **Cross-Provider**: Ensure UI works consistently for both providers

### UI Testing
- **Manual Testing**: Test all dialogs and actions in Eclipse IDE
- **Accessibility**: Verify keyboard navigation works
- **Theming**: Test with different Eclipse themes (light/dark)

### Test Coverage Goals
- Unit test coverage: >80% for new code
- All public API methods have tests
- Error paths are tested (exceptions, invalid input)

---

## Potential Challenges & Mitigation

### Challenge 1: GitHub vs Bitbucket Reviewer Model Differences
**Issue**: GitHub distinguishes between "requested reviewers" (pending) and "reviews" (completed). Bitbucket combines them in the participants array.

**Mitigation**: 
- Design `PullRequestParticipant` to include status field that can represent both models
- In GitHub client, merge data from both endpoints
- Document the difference in code comments

### Challenge 2: Permission Validation
**Issue**: Different users have different permissions to add/remove reviewers.

**Mitigation**:
- Implement client-side permission checks before API calls
- Handle 403 Forbidden responses gracefully with user-friendly messages
- Disable actions when user lacks permissions (check PR author, repo admin role)

### Challenge 3: User Search Performance
**Issue**: Large organizations may have thousands of users, making autocomplete slow.

**Mitigation**:
- Implement client-side caching of user lists
- Use pagination if API supports it
- Limit autocomplete to recent collaborators or repo contributors
- Show loading indicator during search

### Challenge 4: Team Reviewers (GitHub)
**Issue**: GitHub supports team reviewers, which are different from individual users.

**Mitigation**:
- Extend `PullRequestParticipant` to include type field (USER vs TEAM)
- Handle teams separately in UI (different icon, no removal allowed, etc.)
- Document team support limitations in Bitbucket client

### Challenge 5: Reviewer Status Updates
**Issue**: Reviewers can approve/reject after being added, requiring status updates.

**Mitigation**:
- Implement periodic refresh of PR data
- Add manual refresh action
- Consider implementing webhook listeners (future enhancement)

### Challenge 6: String Externalization Completeness
**Issue**: Easy to accidentally hardcode strings in UI.

**Mitigation**:
- Use Eclipse's "Externalize Strings" wizard
- Code review checklist includes checking for `//$NON-NLS-1$` tags
- Run static analysis to detect non-externalized strings

---

## Estimated Complexity

**Overall Complexity**: **Large** (8-12 development days)

**Breakdown by Phase**:
- Phase 1 (Model & Parsing): **Small** (2-3 days)
- Phase 2 (API Implementation): **Medium** (3-4 days)
- Phase 3 (UI Display): **Small-Medium** (2-3 days)
- Phase 4 (Interactive Management): **Medium** (3-4 days)
- Phase 5 (Quick Actions): **Small** (1-2 days)
- Phase 6 (Testing & Docs): **Small-Medium** (2 days)

**Risk Factors**:
- First major feature touching both providers (learning curve)
- UI complexity for reviewer management dialog
- Testing across both GitHub and Bitbucket requires setup

**Confidence Level**: **High** - Well-defined requirements, existing codebase provides patterns to follow, no major technical unknowns.

---

## Questions to Clarify

1. **User Search Scope**: Should autocomplete search all organization users, or only repository contributors? (Performance implication)

2. **Reviewer Removal Rules**: Can any reviewer be removed, or only pending reviewers? (Bitbucket may restrict removal of approved reviewers)

3. **Bulk Operations UI**: Should the dialog support adding multiple reviewers at once, or one at a time?

4. **GitHub Team Support**: Should we fully support GitHub team reviewers, or just individual users initially?

5. **Permissions Caching**: Should we cache permission checks, or verify on every action?

6. **Refresh Strategy**: Should reviewer data auto-refresh periodically, or only on manual refresh/action?

---

## Acceptance Criteria Summary

✅ **Display**:
- [ ] Reviewers column appears in Pull Request List View
- [ ] Approval status is clearly indicated (icons/text)
- [ ] Reviewer details visible in Pull Request Details View
- [ ] Tooltip shows complete reviewer information

✅ **Management**:
- [ ] "Manage Reviewers" dialog opens and displays current reviewers
- [ ] Users can add reviewers via search/autocomplete
- [ ] Users can remove reviewers (with permission validation)
- [ ] Changes are applied successfully via API calls

✅ **Quick Actions**:
- [ ] "Add Myself as Reviewer" action works with one click
- [ ] Keyboard shortcuts are functional
- [ ] Context menu actions are available

✅ **Cross-Provider**:
- [ ] All features work for Bitbucket Data Center
- [ ] All features work for GitHub
- [ ] GitHub team reviewers are handled appropriately

✅ **Quality**:
- [ ] UI updates automatically after reviewer changes
- [ ] Appropriate error messages for permission issues
- [ ] All strings are externalized (no hardcoded text)
- [ ] Code follows project style guide (tabs, 80-char, no wildcards)
- [ ] Unit tests pass (`mvn test`)
- [ ] Manual testing checklist complete

✅ **Documentation**:
- [ ] Javadoc on all public methods
- [ ] README updated with feature description
- [ ] Code comments explain complex logic

---

## Implementation Order Recommendation

For maximum efficiency and early feedback, implement in this order:

1. **Start with Backend** (Phases 1-2): Get data model and API working first
   - This allows testing with API calls before UI is ready
   - Can validate data parsing with unit tests

2. **Add Basic Display** (Phase 3): Show reviewers in existing views
   - Provides visible progress early
   - Validates that data flows correctly through layers

3. **Build Interactive Features** (Phase 4): Dialogs and management
   - Core functionality that provides value to users
   - Most complex phase, benefits from having foundation solid

4. **Polish with Quick Actions** (Phase 5): Shortcuts and UX improvements
   - Nice-to-have features that enhance usability
   - Can be done incrementally

5. **Comprehensive Testing** (Phase 6): Validate everything works
   - Catch integration issues
   - Ensure quality before release

---

## Related Files Reference

### Files to Modify
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequest.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequestParticipant.java` (may need status field)
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/IPullRequestClient.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClient.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketJsonParser.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubClient.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParser.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestListView.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestDetailsView.java`
- `org.eclipse.egit.pullrequest/plugin.xml`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/prtext.properties`

### New Files to Create
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/ReviewerManagementDialog.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/actions/ManageReviewersAction.java`
- `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/actions/AddMyselfAsReviewerAction.java`
- `org.eclipse.egit.pullrequest/icons/reviewer-add.png` (or similar)

### Test Files
- `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClientTest.java`
- `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`
- New: `ReviewerManagementDialogTest.java`

---

## Notes for Implementation

- **Code Style**: Remember to use TABS (not spaces), 80-character lines, explicit imports (no wildcards)
- **Licensing**: Add EPL-2.0 header to all new files
- **String Externalization**: Mark all non-translatable strings with `//$NON-NLS-1$`
- **Error Logging**: Use `Activator.logError()` instead of `printStackTrace()`
- **Javadoc**: Required for all public and protected members, use Eclipse format
- **Testing**: Run `mvn clean test` frequently during development
- **Git Commits**: Use descriptive commit messages following project conventions

---

**Plan Created**: 2026-02-19  
**Plan Version**: 1.0  
**Ready for Implementation**: Yes
