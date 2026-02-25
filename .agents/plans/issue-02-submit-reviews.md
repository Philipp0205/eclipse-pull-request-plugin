# Issue #2: Submit Reviews (Approve / Request Changes)

## Overview

Add the ability for users to approve a pull request, request changes, or leave
a review comment directly from the Eclipse plugin. GitHub and Bitbucket have
different APIs for this:

- **GitHub**: `POST /repos/{owner}/{repo}/pulls/{number}/reviews` with event
  `APPROVE`, `REQUEST_CHANGES`, or `COMMENT`, plus an optional body message.
- **Bitbucket**: `POST .../pull-requests/{id}/approve` to approve,
  `DELETE .../pull-requests/{id}/approve` to unapprove. Bitbucket Data Center
  does not have a native "request changes" concept; the closest equivalent is
  setting the participant status to `NEEDS_WORK` via
  `PUT .../pull-requests/{id}/participants/{username}`.

## Dependencies

- None. This is a foundational feature that other issues benefit from.
- **Issue #6 (Changes Since Last Review)** benefits from this being done first,
  since submitting a review naturally records the "last reviewed" commit.

## Implementation Order

Implement **before** Issue #6.

---

## Step 1: Add capability flags to `PullRequestProviderCapabilities`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/PullRequestProviderCapabilities.java`

Add two new boolean fields and update the constructor + factory.

```java
// After line 27 (private final List<String> supportedStates;)
private final boolean supportsReviewSubmission;
private final boolean supportsRequestChanges;
```

Update constructor (line 41):
```java
public PullRequestProviderCapabilities(
		boolean supportsTaskSeverity,
		boolean supportsCommentState,
		boolean supportsReviewSubmission,
		boolean supportsRequestChanges,
		String... supportedStates) {
	this.supportsTaskSeverity = supportsTaskSeverity;
	this.supportsCommentState = supportsCommentState;
	this.supportsReviewSubmission = supportsReviewSubmission;
	this.supportsRequestChanges = supportsRequestChanges;
	this.supportedStates = Collections
			.unmodifiableList(Arrays.asList(supportedStates));
}
```

Add getters:
```java
/**
 * @return true if the provider supports submitting formal
 *         reviews (approve, request changes, comment)
 */
public boolean supportsReviewSubmission() {
	return supportsReviewSubmission;
}

/**
 * @return true if the provider supports the "request changes"
 *         review action
 */
public boolean supportsRequestChanges() {
	return supportsRequestChanges;
}
```

Update `forProvider()` factory (line 80):
```java
case BITBUCKET:
	return new PullRequestProviderCapabilities(
			true, true, true, true,
			"OPEN", "MERGED", "DECLINED", "ALL"); //$NON-NLS-1$ ...
case GITHUB:
	return new PullRequestProviderCapabilities(
			false, true, true, true,
			"open", "closed", "all"); //$NON-NLS-1$ ...
default:
	return new PullRequestProviderCapabilities(
			false, false, false, false, "OPEN"); //$NON-NLS-1$
```

---

## Step 2: Add review methods to `IPullRequestClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/IPullRequestClient.java`

Add after line 331 (before `getProviderType()`):

```java
/**
 * Submits a review for a pull request. The review event determines
 * the action: approve, request changes, or leave a comment.
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @param event
 *            the review event: "APPROVE", "REQUEST_CHANGES",
 *            or "COMMENT"
 * @param body
 *            optional review body text, may be null for
 *            approvals
 * @throws IOException
 *             if the request fails
 * @throws UnsupportedOperationException
 *             if the provider does not support review
 *             submission
 */
void submitReview(long pullRequestId, @NonNull String event,
		@Nullable String body) throws IOException;

/**
 * Removes the current user's approval from a pull request.
 * For GitHub this dismisses the review; for Bitbucket this
 * removes the approval.
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @throws IOException
 *             if the request fails
 */
void unapproveReview(long pullRequestId) throws IOException;
```

---

## Step 3: Implement in `GitHubClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubClient.java`

Add before the `getReviewers()` method (before line 1119):

```java
@Override
public void submitReview(long pullRequestId,
		@NonNull String event, @Nullable String body)
		throws IOException {
	String path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
			+ "/pulls/" + pullRequestId + "/reviews"; //$NON-NLS-1$ //$NON-NLS-2$

	StringBuilder json = new StringBuilder();
	json.append("{\"event\":\""); //$NON-NLS-1$
	json.append(escapeJson(event));
	json.append("\""); //$NON-NLS-1$
	if (body != null && !body.isEmpty()) {
		json.append(",\"body\":\""); //$NON-NLS-1$
		json.append(escapeJson(body));
		json.append("\""); //$NON-NLS-1$
	}
	json.append("}"); //$NON-NLS-1$

	doPost(path, json.toString());
}

@Override
public void unapproveReview(long pullRequestId)
		throws IOException {
	// GitHub: dismiss the latest APPROVED review by current user
	// First, list reviews to find the latest approval
	String path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
			+ "/pulls/" + pullRequestId + "/reviews"; //$NON-NLS-1$ //$NON-NLS-2$
	String response = doGet(path);

	// Find the latest review with state "APPROVED" by current user
	String currentUser = getCurrentUser();
	long reviewId = findLatestApprovalReviewId(
			response, currentUser);
	if (reviewId == -1) {
		return; // No approval to dismiss
	}

	// Dismiss the review
	String dismissPath = path + "/" + reviewId; //$NON-NLS-1$
	String body = "{\"message\":\"Review dismissed\"}"; //$NON-NLS-1$
	// GitHub uses PUT to dismiss
	doPatch(dismissPath + "/dismissals", body); //$NON-NLS-1$
}

/**
 * Finds the ID of the latest APPROVED review by the given user
 * from a reviews JSON array response.
 *
 * @param reviewsJson
 *            the JSON array of reviews
 * @param username
 *            the username to match
 * @return the review ID, or -1 if not found
 */
private long findLatestApprovalReviewId(String reviewsJson,
		String username) {
	long latestId = -1;
	int idx = 0;
	while (true) {
		int objStart = reviewsJson.indexOf('{', idx);
		if (objStart == -1) {
			break;
		}
		int objEnd = GitHubJsonParser.findMatchingBrace(
				reviewsJson, objStart);
		if (objEnd == -1) {
			break;
		}
		String obj = reviewsJson.substring(
				objStart, objEnd + 1);
		String state = GitHubJsonParser
				.extractStringValue(obj, "state"); //$NON-NLS-1$
		String login = extractNestedLogin(obj);
		if ("APPROVED".equals(state) //$NON-NLS-1$
				&& username.equals(login)) {
			long id = GitHubJsonParser
					.extractLongValue(obj, "id"); //$NON-NLS-1$
			if (id > latestId) {
				latestId = id;
			}
		}
		idx = objEnd + 1;
	}
	return latestId;
}

/**
 * Extracts the "login" field from the nested "user" object.
 */
private String extractNestedLogin(String json) {
	int userIdx = json.indexOf("\"user\""); //$NON-NLS-1$
	if (userIdx == -1) {
		return null;
	}
	int braceStart = json.indexOf('{', userIdx);
	if (braceStart == -1) {
		return null;
	}
	int braceEnd = GitHubJsonParser.findMatchingBrace(
			json, braceStart);
	if (braceEnd == -1) {
		return null;
	}
	String userObj = json.substring(
			braceStart, braceEnd + 1);
	return GitHubJsonParser
			.extractStringValue(userObj, "login"); //$NON-NLS-1$
}
```

**Note**: `GitHubJsonParser.findMatchingBrace()` and
`GitHubJsonParser.extractStringValue()` may need to be made
`package-private` or `public static` if they are currently `private`.
Check `GitHubJsonParser.java` and adjust visibility.

---

## Step 4: Implement in `BitbucketClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClient.java`

Add before the `getReviewers()` method (before line 523):

```java
@Override
public void submitReview(long pullRequestId,
		@NonNull String event, @Nullable String body)
		throws IOException {
	String currentUser = getCurrentUser();
	if ("APPROVE".equals(event)) { //$NON-NLS-1$
		// POST .../approve
		String url = serverUrl + API_BASE_PATH
				+ "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
				+ "/approve"; //$NON-NLS-1$
		executePost(url, ""); //$NON-NLS-1$
	} else if ("REQUEST_CHANGES".equals(event)) { //$NON-NLS-1$
		// PUT participant status to NEEDS_WORK
		String url = serverUrl + API_BASE_PATH
				+ "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
				+ "/participants/" + currentUser; //$NON-NLS-1$
		String json = "{\"user\":{\"name\":\"" //$NON-NLS-1$
				+ escapeJson(currentUser)
				+ "\"},\"status\":\"NEEDS_WORK\"}"; //$NON-NLS-1$
		executePut(url, json);
	} else if ("COMMENT".equals(event) //$NON-NLS-1$
			&& body != null && !body.isEmpty()) {
		// Add a general comment with the review body
		addComment(pullRequestId, body, -1);
	}
}

@Override
public void unapproveReview(long pullRequestId)
		throws IOException {
	// DELETE .../approve
	String url = serverUrl + API_BASE_PATH
			+ "/projects/" + projectKey //$NON-NLS-1$
			+ "/repos/" + repositorySlug //$NON-NLS-1$
			+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
			+ "/approve"; //$NON-NLS-1$
	executeDelete(url);
}
```

---

## Step 5: Add NLS strings

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/PRText.java`

Add before the `static {}` block (before line 206):

```java
/** */
public static String SubmitReview_ApproveAction;

/** */
public static String SubmitReview_RequestChangesAction;

/** */
public static String SubmitReview_CommentAction;

/** */
public static String SubmitReview_UnapproveAction;

/** */
public static String SubmitReview_JobName;

/** */
public static String SubmitReview_Error;

/** */
public static String SubmitReview_Success;

/** */
public static String SubmitReview_DialogTitle;

/** */
public static String SubmitReview_DialogMessage;

/** */
public static String SubmitReview_ApproveTooltip;

/** */
public static String SubmitReview_RequestChangesTooltip;
```

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/prtext.properties`

Append:

```properties
SubmitReview_ApproveAction=Approve
SubmitReview_RequestChangesAction=Request Changes
SubmitReview_CommentAction=Comment
SubmitReview_UnapproveAction=Unapprove
SubmitReview_JobName=Submitting review
SubmitReview_Error=Failed to submit review
SubmitReview_Success=Review submitted successfully
SubmitReview_DialogTitle=Submit Review
SubmitReview_DialogMessage=Enter an optional comment for your review:
SubmitReview_ApproveTooltip=Approve this pull request
SubmitReview_RequestChangesTooltip=Request changes on this pull request
```

---

## Step 6: Add review buttons to `PullRequestOverviewView`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestOverviewView.java`

### 6a. Increase button row columns

In `renderActions()` at line 408, change `numColumns(6)` to `numColumns(8)`:

```java
GridLayoutFactory.fillDefaults().numColumns(8)
		.spacing(8, 0).applyTo(buttonRow);
```

### 6b. Add Approve and Request Changes buttons

After the `checkoutBtn` block (after line 445), add:

```java
if (client != null
		&& client.getCapabilities()
				.supportsReviewSubmission()) {
	Button approveBtn = toolkit.createButton(buttonRow,
			PRText.SubmitReview_ApproveAction, SWT.PUSH);
	approveBtn.setToolTipText(
			PRText.SubmitReview_ApproveTooltip);
	approveBtn.addListener(SWT.Selection,
			e -> submitReview("APPROVE")); //$NON-NLS-1$

	Button requestChangesBtn = toolkit.createButton(
			buttonRow,
			PRText.SubmitReview_RequestChangesAction,
			SWT.PUSH);
	requestChangesBtn.setToolTipText(
			PRText.SubmitReview_RequestChangesTooltip);
	requestChangesBtn.addListener(SWT.Selection,
			e -> submitReview("REQUEST_CHANGES")); //$NON-NLS-1$
}
```

### 6c. Add `submitReview()` method

Add a new private method in PullRequestOverviewView:

```java
private void submitReview(String event) {
	if (currentPullRequest == null || client == null) {
		return;
	}

	// For REQUEST_CHANGES, prompt for a body message
	String body = null;
	if ("REQUEST_CHANGES".equals(event)) { //$NON-NLS-1$
		MultiLineInputDialog dialog = new MultiLineInputDialog(
				getSite().getShell(),
				PRText.SubmitReview_DialogTitle,
				PRText.SubmitReview_DialogMessage,
				""); //$NON-NLS-1$
		if (dialog.open() != Window.OK) {
			return;
		}
		body = dialog.getValue();
	}

	final String reviewBody = body;
	Job job = new Job(PRText.SubmitReview_JobName) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				client.submitReview(
						currentPullRequest.getId(),
						event, reviewBody);
				Display.getDefault().asyncExec(
						() -> refreshView());
				return Status.OK_STATUS;
			} catch (IOException e) {
				Activator.logError(
						PRText.SubmitReview_Error, e);
				return new Status(IStatus.ERROR,
						Activator.PLUGIN_ID,
						PRText.SubmitReview_Error, e);
			}
		}
	};
	job.setUser(true);
	job.schedule();
}
```

---

## Step 7: Add review actions to Changed Files view toolbar

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestChangedFilesView.java`

In `createPartControl()`, after `createMarkAllUnreadAction()` at line 179,
add:

```java
createReviewActions();
```

Add new method:

```java
/**
 * Creates Approve and Request Changes toolbar actions.
 */
private void createReviewActions() {
	Action approveAction = new Action(
			PRText.SubmitReview_ApproveAction) {
		@Override
		public void run() {
			submitReviewFromToolbar("APPROVE"); //$NON-NLS-1$
		}
	};
	approveAction.setToolTipText(
			PRText.SubmitReview_ApproveTooltip);

	Action requestChangesAction = new Action(
			PRText.SubmitReview_RequestChangesAction) {
		@Override
		public void run() {
			submitReviewFromToolbar(
					"REQUEST_CHANGES"); //$NON-NLS-1$
		}
	};
	requestChangesAction.setToolTipText(
			PRText.SubmitReview_RequestChangesTooltip);

	IToolBarManager toolbar = getViewSite()
			.getActionBars().getToolBarManager();
	toolbar.add(new Separator());
	toolbar.add(approveAction);
	toolbar.add(requestChangesAction);
}

private void submitReviewFromToolbar(String event) {
	if (currentPullRequest == null || client == null) {
		return;
	}
	// Reuse same pattern as overview view
	String body = null;
	if ("REQUEST_CHANGES".equals(event)) { //$NON-NLS-1$
		MultiLineInputDialog dialog =
				new MultiLineInputDialog(
						getSite().getShell(),
						PRText.SubmitReview_DialogTitle,
						PRText.SubmitReview_DialogMessage,
						""); //$NON-NLS-1$
		if (dialog.open() != Window.OK) {
			return;
		}
		body = dialog.getValue();
	}
	final String reviewBody = body;
	Job job = new Job(PRText.SubmitReview_JobName) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				client.submitReview(
						currentPullRequest.getId(),
						event, reviewBody);
				return Status.OK_STATUS;
			} catch (IOException e) {
				Activator.logError(
						PRText.SubmitReview_Error, e);
				return new Status(IStatus.ERROR,
						Activator.PLUGIN_ID,
						PRText.SubmitReview_Error, e);
			}
		}
	};
	job.setUser(true);
	job.schedule();
}
```

---

## Step 8: Add tests

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`

Add a test method for parsing review response JSON:

```java
@Test
public void testParseReviewResponse() {
	String json = "{\"id\":12345,\"state\":\"APPROVED\","
			+ "\"user\":{\"login\":\"reviewer1\"}}";
	// Verify extractStringValue works on review state
	String state = GitHubJsonParser
			.extractStringValue(json, "state");
	assertThat(state, equalTo("APPROVED"));
}
```

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/client/PullRequestProviderCapabilitiesTest.java` (new file)

```java
@Test
public void testGitHubCapabilitiesSupportsReview() {
	PullRequestProviderCapabilities caps =
			PullRequestProviderCapabilities.forProvider(
					PullRequestProviderType.GITHUB);
	assertThat(caps.supportsReviewSubmission(),
			equalTo(true));
	assertThat(caps.supportsRequestChanges(),
			equalTo(true));
}

@Test
public void testBitbucketCapabilitiesSupportsReview() {
	PullRequestProviderCapabilities caps =
			PullRequestProviderCapabilities.forProvider(
					PullRequestProviderType.BITBUCKET);
	assertThat(caps.supportsReviewSubmission(),
			equalTo(true));
	assertThat(caps.supportsRequestChanges(),
			equalTo(true));
}
```

---

## Verification

1. `mvn clean verify -DskipTests` — build succeeds
2. `cd org.eclipse.egit.pullrequest.test && mvn test` — all tests pass
3. Manual: Open a PR in the overview view, verify Approve and Request Changes
   buttons appear, click each and verify the API call succeeds
