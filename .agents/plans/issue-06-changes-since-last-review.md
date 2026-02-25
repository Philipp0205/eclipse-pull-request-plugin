# Issue #6: Changes Since Last Review

## Overview

Allow reviewers to see only the files and diffs that changed since their last
review. This requires:

1. Tracking the "last reviewed" commit SHA per pull request per user.
2. A new API method to fetch changes between two commits.
3. A toggle in the Changed Files view toolbar to switch between "all changes"
   and "changes since last review".
4. A "Mark as Reviewed" action that records the current head commit SHA.

## Dependencies

- **Depends on Issue #2 (Submit Reviews)**: When a user submits a review
  (approve/request changes), the current head commit should automatically be
  recorded as the "last reviewed" commit. Implement #2 first.
- No other dependencies.

## Implementation Order

Implement **after** Issue #2.

---

## Step 1: Create `ReviewTracker` utility class

**File** (new): `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/ReviewTracker.java`

This class stores the last-reviewed commit SHA per PR in Eclipse preferences.

```java
/*******************************************************************************
 * Copyright (C) 2026, Eclipse EGit contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.client;

import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jgit.annotations.Nullable;

/**
 * Tracks the last-reviewed commit SHA for each pull request. Stores
 * data in the plugin's preference store using a key derived from the
 * provider type and pull request ID.
 */
public class ReviewTracker {

	private static final String KEY_PREFIX =
			"reviewTracker.lastReviewedCommit."; //$NON-NLS-1$

	private ReviewTracker() {
		// Static utility class
	}

	/**
	 * Records the given commit SHA as the last-reviewed commit for
	 * the specified pull request.
	 *
	 * @param providerType
	 *            the provider type identifier (e.g., "GITHUB")
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param commitSha
	 *            the commit SHA to record
	 */
	public static void markReviewed(String providerType,
			long pullRequestId, String commitSha) {
		IPreferenceStore store = Activator.getDefault()
				.getPreferenceStore();
		String key = buildKey(providerType, pullRequestId);
		store.setValue(key, commitSha);
	}

	/**
	 * Returns the last-reviewed commit SHA for the specified pull
	 * request, or null if never reviewed.
	 *
	 * @param providerType
	 *            the provider type identifier
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @return the last-reviewed commit SHA, or null
	 */
	@Nullable
	public static String getLastReviewedCommit(
			String providerType, long pullRequestId) {
		IPreferenceStore store = Activator.getDefault()
				.getPreferenceStore();
		String key = buildKey(providerType, pullRequestId);
		String value = store.getString(key);
		if (value == null || value.isEmpty()) {
			return null;
		}
		return value;
	}

	/**
	 * Clears the last-reviewed commit for the specified pull
	 * request.
	 *
	 * @param providerType
	 *            the provider type identifier
	 * @param pullRequestId
	 *            the pull request ID or number
	 */
	public static void clearReviewed(String providerType,
			long pullRequestId) {
		IPreferenceStore store = Activator.getDefault()
				.getPreferenceStore();
		String key = buildKey(providerType, pullRequestId);
		store.setToDefault(key);
	}

	private static String buildKey(String providerType,
			long pullRequestId) {
		return KEY_PREFIX + providerType + "." //$NON-NLS-1$
				+ pullRequestId;
	}
}
```

---

## Step 2: Add `getPullRequestChangesSince()` to `IPullRequestClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/IPullRequestClient.java`

Add after `getPullRequestChanges()` (after line 76):

```java
/**
 * Retrieves changed files for a pull request since a specific
 * commit. Returns only files that changed between the given base
 * commit and the current PR head.
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @param sinceCommitSha
 *            the commit SHA to compare from (exclusive)
 * @return list of changed files since that commit
 * @throws IOException
 *             if the request fails
 */
@NonNull
List<ChangedFile> getPullRequestChangesSince(
		long pullRequestId, @NonNull String sinceCommitSha)
		throws IOException;
```

---

## Step 3: Implement in `GitHubClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubClient.java`

Add after the existing `getPullRequestChanges()` method:

```java
@Override
@NonNull
public List<ChangedFile> getPullRequestChangesSince(
		long pullRequestId,
		@NonNull String sinceCommitSha) throws IOException {
	// Use GitHub compare API: GET /repos/{owner}/{repo}/compare/{base}...{head}
	// where base = sinceCommitSha and head = PR head SHA
	PullRequest pr = getPullRequest(pullRequestId);
	String headSha = pr.getFromRef().getLatestCommit();

	String path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
			+ "/compare/" + sinceCommitSha //$NON-NLS-1$
			+ "..." + headSha; //$NON-NLS-1$
	String response = doGet(path);

	return GitHubJsonParser.parseCompareChangedFiles(
			response);
}
```

Add a new parser method in `GitHubJsonParser`:

```java
/**
 * Parses the "files" array from a GitHub compare response.
 *
 * @param json
 *            the compare API response JSON
 * @return list of changed files
 */
public static List<ChangedFile> parseCompareChangedFiles(
		String json) {
	// The compare response has a "files" array identical
	// in structure to the PR files endpoint
	int filesIdx = json.indexOf("\"files\""); //$NON-NLS-1$
	if (filesIdx == -1) {
		return Collections.emptyList();
	}
	int arrStart = json.indexOf('[', filesIdx);
	if (arrStart == -1) {
		return Collections.emptyList();
	}
	int arrEnd = findMatchingBracket(json, arrStart);
	String filesArr = json.substring(
			arrStart, arrEnd + 1);
	return parseChangedFilesArray(filesArr);
}
```

---

## Step 4: Implement in `BitbucketClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClient.java`

Add after the existing `getPullRequestChanges()` method:

```java
@Override
@NonNull
public List<ChangedFile> getPullRequestChangesSince(
		long pullRequestId,
		@NonNull String sinceCommitSha) throws IOException {
	// Bitbucket: GET .../pull-requests/{id}/changes?sinceId={sha}
	String url = serverUrl + API_BASE_PATH
			+ "/projects/" + projectKey //$NON-NLS-1$
			+ "/repos/" + repositorySlug //$NON-NLS-1$
			+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
			+ "/changes?sinceId=" //$NON-NLS-1$
			+ sinceCommitSha;
	String response = executeGet(url);
	return BitbucketJsonParser.parseChangedFiles(response);
}
```

---

## Step 5: Add NLS strings

**File**: `PRText.java` — add before `static {}` block:

```java
/** */
public static String ChangesSinceLastReview_ToggleAction;

/** */
public static String ChangesSinceLastReview_ToggleTooltip;

/** */
public static String ChangesSinceLastReview_MarkReviewedAction;

/** */
public static String ChangesSinceLastReview_MarkReviewedTooltip;

/** */
public static String ChangesSinceLastReview_NoLastReview;

/** */
public static String ChangesSinceLastReview_JobName;

/** */
public static String ChangesSinceLastReview_Error;
```

**File**: `prtext.properties` — append:

```properties
ChangesSinceLastReview_ToggleAction=Changes Since Last Review
ChangesSinceLastReview_ToggleTooltip=Show only files changed since your last review
ChangesSinceLastReview_MarkReviewedAction=Mark as Reviewed
ChangesSinceLastReview_MarkReviewedTooltip=Record the current head commit as your last reviewed commit
ChangesSinceLastReview_NoLastReview=No previous review recorded. Showing all changes.
ChangesSinceLastReview_JobName=Loading changes since last review
ChangesSinceLastReview_Error=Failed to load changes since last review
```

---

## Step 6: Add toggle and "Mark as Reviewed" to Changed Files view

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/PullRequestChangedFilesView.java`

### 6a. Add field for tracking filter state

Add fields near the top of the class:

```java
private boolean showingSinceLastReview = false;

private List<PullRequestChangedFile> allChangedFiles;
```

### 6b. Add toolbar actions

After `createMarkAllUnreadAction()` call in `createPartControl()`, add:

```java
createChangesSinceLastReviewActions();
```

New method:

```java
/**
 * Creates toggle and mark-reviewed toolbar actions.
 */
private void createChangesSinceLastReviewActions() {
	Action toggleAction = new Action(
			PRText.ChangesSinceLastReview_ToggleAction,
			IAction.AS_CHECK_BOX) {
		@Override
		public void run() {
			showingSinceLastReview = isChecked();
			if (showingSinceLastReview) {
				loadChangesSinceLastReview();
			} else {
				// Restore full list
				changedFiles.clear();
				if (allChangedFiles != null) {
					changedFiles.addAll(allChangedFiles);
				}
				changedFilesViewer.refresh();
			}
		}
	};
	toggleAction.setToolTipText(
			PRText.ChangesSinceLastReview_ToggleTooltip);

	Action markReviewedAction = new Action(
			PRText.ChangesSinceLastReview_MarkReviewedAction) {
		@Override
		public void run() {
			markCurrentCommitAsReviewed();
		}
	};
	markReviewedAction.setToolTipText(
			PRText.ChangesSinceLastReview_MarkReviewedTooltip);

	IToolBarManager toolbar = getViewSite()
			.getActionBars().getToolBarManager();
	toolbar.add(new Separator());
	toolbar.add(toggleAction);
	toolbar.add(markReviewedAction);
}
```

### 6c. Add helper methods

```java
private void loadChangesSinceLastReview() {
	if (currentPullRequest == null || client == null) {
		return;
	}
	String lastSha = ReviewTracker.getLastReviewedCommit(
			client.getProviderType().name(),
			currentPullRequest.getId());
	if (lastSha == null) {
		Activator.logInfo(
				PRText.ChangesSinceLastReview_NoLastReview);
		return;
	}

	Job job = new Job(
			PRText.ChangesSinceLastReview_JobName) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				List<ChangedFile> sinceFiles =
						client.getPullRequestChangesSince(
								currentPullRequest.getId(),
								lastSha);
				Display.getDefault().asyncExec(() -> {
					// Save full list if not saved
					if (allChangedFiles == null) {
						allChangedFiles =
								new ArrayList<>(changedFiles);
					}
					changedFiles.clear();
					for (ChangedFile cf : sinceFiles) {
						changedFiles.add(
								new PullRequestChangedFile(cf));
					}
					changedFilesViewer.refresh();
				});
				return Status.OK_STATUS;
			} catch (IOException e) {
				Activator.logError(
						PRText.ChangesSinceLastReview_Error,
						e);
				return new Status(IStatus.ERROR,
						Activator.PLUGIN_ID,
						PRText.ChangesSinceLastReview_Error,
						e);
			}
		}
	};
	job.setUser(true);
	job.schedule();
}

private void markCurrentCommitAsReviewed() {
	if (currentPullRequest == null || client == null) {
		return;
	}
	String headSha = currentPullRequest.getFromRef()
			.getLatestCommit();
	if (headSha != null) {
		ReviewTracker.markReviewed(
				client.getProviderType().name(),
				currentPullRequest.getId(), headSha);
	}
}
```

---

## Step 7: Auto-record on review submission (integration with Issue #2)

**File**: `PullRequestOverviewView.java`

In the `submitReview()` method (added in Issue #2), after the successful
`client.submitReview()` call, add:

```java
// Auto-record last reviewed commit
String headSha = currentPullRequest.getFromRef()
		.getLatestCommit();
if (headSha != null) {
	ReviewTracker.markReviewed(
			client.getProviderType().name(),
			currentPullRequest.getId(), headSha);
}
```

---

## Step 8: Add tests

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`

```java
@Test
public void testParseCompareChangedFiles() {
	String json = "{\"files\":["
			+ "{\"filename\":\"src/Main.java\","
			+ "\"status\":\"modified\","
			+ "\"additions\":5,\"deletions\":2,"
			+ "\"patch\":\"@@ -1,3 +1,6 @@\"}"
			+ "]}";
	List<ChangedFile> files = GitHubJsonParser
			.parseCompareChangedFiles(json);
	assertThat(files, hasSize(1));
	assertThat(files.get(0).getPath(),
			equalTo("src/Main.java"));
}
```

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClientTest.java`

Add test for parsing changes with sinceId (uses same parser):

```java
@Test
public void testParseChangedFilesSince() {
	// Same structure as normal changes response
	String json = "{\"values\":[{\"path\":{\"toString\":"
			+ "\"src/Main.java\"},\"type\":\"MODIFY\","
			+ "\"srcPath\":{\"toString\":\"src/Main.java\"}}]}";
	List<ChangedFile> files = BitbucketJsonParser
			.parseChangedFiles(json);
	assertThat(files, hasSize(1));
}
```

---

## Verification

1. `mvn clean verify -DskipTests` — build succeeds
2. `cd org.eclipse.egit.pullrequest.test && mvn test` — all tests pass
3. Manual: Open a PR, click "Mark as Reviewed", push new commits, toggle
   "Changes Since Last Review" to verify only new files appear
