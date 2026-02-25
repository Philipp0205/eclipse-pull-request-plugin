# Issue #10: Emoji Reactions on Comments

## Overview

Display, add, and remove emoji reactions on pull request comments. GitHub has
a rich reactions API; Bitbucket Data Center does not support reactions natively,
so the Bitbucket implementation returns empty lists and the UI hides reaction
controls when the provider doesn't support them.

Reactions supported by GitHub:
`+1`, `-1`, `laugh`, `confused`, `heart`, `hooray`, `rocket`, `eyes`

## Dependencies

- None. Can be implemented independently.

## Implementation Order

No ordering constraints. Can be done in any position.

---

## Step 1: Create `Reaction` model class

**File** (new): `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/Reaction.java`

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
package org.eclipse.egit.pullrequest.internal.model;

/**
 * Represents a single emoji reaction on a pull request comment.
 */
public class Reaction {

	private long id;

	private String content;

	private String userName;

	/**
	 * @return the reaction ID
	 */
	public long getId() {
		return id;
	}

	/**
	 * @param id
	 *            the reaction ID
	 */
	public void setId(long id) {
		this.id = id;
	}

	/**
	 * @return the reaction content/type (e.g., "+1", "heart",
	 *         "rocket")
	 */
	public String getContent() {
		return content;
	}

	/**
	 * @param content
	 *            the reaction content/type
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * @return the username who created this reaction
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * @param userName
	 *            the username who created this reaction
	 */
	public void setUserName(String userName) {
		this.userName = userName;
	}
}
```

---

## Step 2: Create `ReactionSummary` model class

**File** (new): `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/ReactionSummary.java`

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
package org.eclipse.egit.pullrequest.internal.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summarizes reactions on a comment, grouping by reaction type
 * with counts and whether the current user has reacted.
 */
public class ReactionSummary {

	private final Map<String, Integer> counts;

	private final Map<String, Boolean> currentUserReacted;

	private final Map<String, Long> currentUserReactionIds;

	/**
	 * Creates a summary from a list of reactions.
	 *
	 * @param reactions
	 *            the reactions list
	 * @param currentUser
	 *            the current user's username
	 */
	public ReactionSummary(List<Reaction> reactions,
			String currentUser) {
		Map<String, Integer> c = new LinkedHashMap<>();
		Map<String, Boolean> u = new LinkedHashMap<>();
		Map<String, Long> ids = new LinkedHashMap<>();

		if (reactions != null) {
			for (Reaction r : reactions) {
				String key = r.getContent();
				c.merge(key, 1, Integer::sum);
				if (currentUser != null && currentUser
						.equals(r.getUserName())) {
					u.put(key, Boolean.TRUE);
					ids.put(key, r.getId());
				}
			}
		}

		this.counts = Collections.unmodifiableMap(c);
		this.currentUserReacted =
				Collections.unmodifiableMap(u);
		this.currentUserReactionIds =
				Collections.unmodifiableMap(ids);
	}

	/**
	 * @return map of reaction type to count
	 */
	public Map<String, Integer> getCounts() {
		return counts;
	}

	/**
	 * @param content
	 *            the reaction type
	 * @return true if the current user has this reaction
	 */
	public boolean hasCurrentUserReacted(String content) {
		return currentUserReacted.getOrDefault(
				content, Boolean.FALSE);
	}

	/**
	 * @param content
	 *            the reaction type
	 * @return the reaction ID for the current user, or -1
	 */
	public long getCurrentUserReactionId(String content) {
		return currentUserReactionIds.getOrDefault(
				content, -1L);
	}
}
```

---

## Step 3: Add reactions list to `PullRequestComment`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequestComment.java`

Add field after `threadId` (after line 74):

```java
private List<Reaction> reactions;
```

Add import:
```java
import java.util.Collections;
```

Add getter/setter:

```java
/**
 * @return the reactions on this comment, never null
 */
public List<Reaction> getReactions() {
	if (reactions == null) {
		return Collections.emptyList();
	}
	return reactions;
}

/**
 * @param reactions
 *            the reactions to set
 */
public void setReactions(List<Reaction> reactions) {
	this.reactions = reactions;
}
```

---

## Step 4: Add capability flag

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/PullRequestProviderCapabilities.java`

Add field:

```java
private final boolean supportsReactions;
```

Update constructor to accept `supportsReactions` parameter.

Add getter:

```java
/**
 * @return true if the provider supports emoji reactions
 */
public boolean supportsReactions() {
	return supportsReactions;
}
```

Update `forProvider()`:
- GitHub: `supportsReactions = true`
- Bitbucket: `supportsReactions = false`

---

## Step 5: Add reaction API methods to `IPullRequestClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/IPullRequestClient.java`

Add after `deleteComment()`:

```java
/**
 * Gets reactions for a specific comment.
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @param commentId
 *            the comment ID
 * @param isReviewComment
 *            true if this is a review (inline) comment
 * @return list of reactions, never null
 * @throws IOException
 *             if the request fails
 */
@NonNull
List<Reaction> getReactions(long pullRequestId,
		long commentId, boolean isReviewComment)
		throws IOException;

/**
 * Adds a reaction to a comment.
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @param commentId
 *            the comment ID
 * @param isReviewComment
 *            true if this is a review (inline) comment
 * @param content
 *            the reaction type (e.g., "+1", "heart")
 * @return the created reaction
 * @throws IOException
 *             if the request fails
 */
@NonNull
Reaction addReaction(long pullRequestId, long commentId,
		boolean isReviewComment, @NonNull String content)
		throws IOException;

/**
 * Removes a reaction from a comment.
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @param commentId
 *            the comment ID
 * @param isReviewComment
 *            true if this is a review (inline) comment
 * @param reactionId
 *            the reaction ID to remove
 * @throws IOException
 *             if the request fails
 */
void removeReaction(long pullRequestId, long commentId,
		boolean isReviewComment, long reactionId)
		throws IOException;
```

Add import:
```java
import org.eclipse.egit.pullrequest.internal.model.Reaction;
```

---

## Step 6: Implement in `GitHubClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubClient.java`

```java
@Override
@NonNull
public List<Reaction> getReactions(long pullRequestId,
		long commentId, boolean isReviewComment)
		throws IOException {
	String path;
	if (isReviewComment) {
		path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/pulls/comments/" + commentId //$NON-NLS-1$
				+ "/reactions"; //$NON-NLS-1$
	} else {
		path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/issues/comments/" + commentId //$NON-NLS-1$
				+ "/reactions"; //$NON-NLS-1$
	}
	String response = doGetAllPages(path);
	return GitHubJsonParser.parseReactions(response);
}

@Override
@NonNull
public Reaction addReaction(long pullRequestId,
		long commentId, boolean isReviewComment,
		@NonNull String content) throws IOException {
	String path;
	if (isReviewComment) {
		path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/pulls/comments/" + commentId //$NON-NLS-1$
				+ "/reactions"; //$NON-NLS-1$
	} else {
		path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/issues/comments/" + commentId //$NON-NLS-1$
				+ "/reactions"; //$NON-NLS-1$
	}
	String json = "{\"content\":\"" //$NON-NLS-1$
			+ escapeJson(content) + "\"}"; //$NON-NLS-1$
	String response = doPost(path, json);
	return GitHubJsonParser.parseReaction(response);
}

@Override
public void removeReaction(long pullRequestId,
		long commentId, boolean isReviewComment,
		long reactionId) throws IOException {
	String path;
	if (isReviewComment) {
		path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/pulls/comments/" + commentId //$NON-NLS-1$
				+ "/reactions/" + reactionId; //$NON-NLS-1$
	} else {
		path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/issues/comments/" + commentId //$NON-NLS-1$
				+ "/reactions/" + reactionId; //$NON-NLS-1$
	}
	doDelete(path);
}
```

---

## Step 7: Implement in `BitbucketClient` (no-op)

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClient.java`

```java
@Override
@NonNull
public List<Reaction> getReactions(long pullRequestId,
		long commentId, boolean isReviewComment)
		throws IOException {
	// Bitbucket Data Center does not support reactions
	return java.util.Collections.emptyList();
}

@Override
@NonNull
public Reaction addReaction(long pullRequestId,
		long commentId, boolean isReviewComment,
		@NonNull String content) throws IOException {
	throw new UnsupportedOperationException(
			"Bitbucket does not support reactions"); //$NON-NLS-1$
}

@Override
public void removeReaction(long pullRequestId,
		long commentId, boolean isReviewComment,
		long reactionId) throws IOException {
	throw new UnsupportedOperationException(
			"Bitbucket does not support reactions"); //$NON-NLS-1$
}
```

---

## Step 8: Add parsing to `GitHubJsonParser`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParser.java`

```java
/**
 * Parses a JSON array of reactions.
 *
 * @param json
 *            the JSON array string
 * @return list of reactions
 */
public static List<Reaction> parseReactions(String json) {
	List<Reaction> reactions = new ArrayList<>();
	if (json == null || json.isEmpty()) {
		return reactions;
	}
	int idx = 0;
	while (true) {
		int objStart = json.indexOf('{', idx);
		if (objStart == -1) {
			break;
		}
		int objEnd = findMatchingBrace(json, objStart);
		if (objEnd == -1) {
			break;
		}
		String obj = json.substring(
				objStart, objEnd + 1);
		reactions.add(parseReaction(obj));
		idx = objEnd + 1;
	}
	return reactions;
}

/**
 * Parses a single reaction JSON object.
 *
 * @param json
 *            the JSON object string
 * @return the parsed reaction
 */
public static Reaction parseReaction(String json) {
	Reaction r = new Reaction();
	r.setId(extractLongValue(json, "id")); //$NON-NLS-1$
	r.setContent(
			extractStringValue(json, "content")); //$NON-NLS-1$

	// Extract user login
	int userIdx = json.indexOf("\"user\""); //$NON-NLS-1$
	if (userIdx != -1) {
		int braceStart = json.indexOf('{', userIdx);
		if (braceStart != -1) {
			int braceEnd = findMatchingBrace(
					json, braceStart);
			if (braceEnd != -1) {
				String userObj = json.substring(
						braceStart, braceEnd + 1);
				r.setUserName(extractStringValue(
						userObj, "login")); //$NON-NLS-1$
			}
		}
	}
	return r;
}
```

Also update the comment parser to inline-fetch reactions when parsing
comments (GitHub includes a `reactions` object with counts in comment
responses, but individual reactions require a separate API call). For
efficiency, parse the inline reaction counts and fetch full reactions
only when the user expands a comment.

---

## Step 9: Add NLS strings

**File**: `PRText.java` — add before `static {}`:

```java
/** */
public static String Reaction_AddTooltip;

/** */
public static String Reaction_RemoveTooltip;

/** */
public static String Reaction_PickerTitle;

/** */
public static String Reaction_JobName;

/** */
public static String Reaction_Error;
```

**File**: `prtext.properties` — append:

```properties
Reaction_AddTooltip=Add reaction
Reaction_RemoveTooltip=Remove your reaction
Reaction_PickerTitle=Add Reaction
Reaction_JobName=Updating reaction
Reaction_Error=Failed to update reaction
```

---

## Step 10: Add reaction bar to `ExpandedCommentComposite`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/ExpandedCommentComposite.java`

### 10a. Add callback to `CommentActionHandler`

```java
/**
 * Called when the user adds or removes a reaction.
 *
 * @param comment
 *            the comment
 * @param content
 *            the reaction type (e.g., "+1")
 * @param add
 *            true to add, false to remove
 * @param reactionId
 *            the reaction ID (for removal), or -1
 */
void onReaction(PullRequestComment comment,
		String content, boolean add, long reactionId);
```

### 10b. Render reaction bar after body text

In `renderComment()`, after the body text block, add:

```java
// Reaction bar (only if provider supports reactions)
if (supportsReactions) {
	renderReactionBar(commentArea, parent,
			comment, handler);
}
```

Add a `supportsReactions` boolean field, set from the constructor
based on provider capabilities.

### 10c. Implement `renderReactionBar()`

```java
private static final String[] REACTION_EMOJIS = {
	"\uD83D\uDC4D", "\uD83D\uDC4E",  // thumbs up/down
	"\uD83D\uDE04", "\uD83D\uDE15",   // smile/confused
	"\u2764\uFE0F", "\uD83C\uDF89",   // heart/party
	"\uD83D\uDE80", "\uD83D\uDC40"    // rocket/eyes
}; //$NON-NLS-1$ etc.

private static final String[] REACTION_NAMES = {
	"+1", "-1", "laugh", "confused", //$NON-NLS-1$ ...
	"heart", "hooray", "rocket", "eyes" //$NON-NLS-1$ ...
};

private void renderReactionBar(Composite commentArea,
		Composite parent, PullRequestComment comment,
		CommentActionHandler handler) {
	List<Reaction> reactions = comment.getReactions();
	ReactionSummary summary = new ReactionSummary(
			reactions, currentUsername);

	if (summary.getCounts().isEmpty()
			&& handler == null) {
		return; // Nothing to show
	}

	Composite bar = new Composite(commentArea, SWT.NONE);
	bar.setBackground(parent.getBackground());
	int cols = summary.getCounts().size() + 1; // +1 for add button
	GridLayoutFactory.fillDefaults()
			.numColumns(cols).spacing(4, 0)
			.margins(8, 2).applyTo(bar);
	GridDataFactory.fillDefaults().grab(true, false)
			.applyTo(bar);

	// Existing reactions
	for (Map.Entry<String, Integer> entry
			: summary.getCounts().entrySet()) {
		String content = entry.getKey();
		int count = entry.getValue();
		boolean myReaction = summary
				.hasCurrentUserReacted(content);

		String emoji = getEmojiForContent(content);
		Label reactionLabel = new Label(bar, SWT.NONE);
		reactionLabel.setText(emoji + " " + count); //$NON-NLS-1$
		reactionLabel.setBackground(
				parent.getBackground());
		reactionLabel.setCursor(getDisplay()
				.getSystemCursor(SWT.CURSOR_HAND));

		if (handler != null) {
			reactionLabel.addListener(SWT.MouseDown,
					e -> {
				if (myReaction) {
					long rid = summary
							.getCurrentUserReactionId(
									content);
					handler.onReaction(comment,
							content, false, rid);
				} else {
					handler.onReaction(comment,
							content, true, -1);
				}
			});
		}
	}

	// "+" add reaction button
	if (handler != null) {
		Label addBtn = new Label(bar, SWT.NONE);
		addBtn.setText("+"); //$NON-NLS-1$
		addBtn.setBackground(parent.getBackground());
		addBtn.setForeground(linkColor);
		addBtn.setToolTipText(PRText.Reaction_AddTooltip);
		addBtn.setCursor(getDisplay()
				.getSystemCursor(SWT.CURSOR_HAND));
		addBtn.addListener(SWT.MouseDown, e -> {
			showReactionPicker(bar, comment, handler);
		});
	}
}

private void showReactionPicker(Composite anchor,
		PullRequestComment comment,
		CommentActionHandler handler) {
	// Simple popup with emoji labels
	Shell popup = new Shell(getShell(),
			SWT.ON_TOP | SWT.TOOL);
	popup.setLayout(new org.eclipse.swt.layout
			.RowLayout(SWT.HORIZONTAL));
	for (int i = 0; i < REACTION_NAMES.length; i++) {
		String name = REACTION_NAMES[i];
		String emoji = REACTION_EMOJIS[i];
		Label emojiLabel = new Label(popup, SWT.NONE);
		emojiLabel.setText(emoji);
		emojiLabel.setCursor(getDisplay()
				.getSystemCursor(SWT.CURSOR_HAND));
		emojiLabel.addListener(SWT.MouseDown, e -> {
			handler.onReaction(comment,
					name, true, -1);
			popup.dispose();
		});
	}
	popup.pack();
	org.eclipse.swt.graphics.Point loc = anchor
			.toDisplay(0, anchor.getSize().y);
	popup.setLocation(loc);
	popup.setVisible(true);
}

private String getEmojiForContent(String content) {
	for (int i = 0; i < REACTION_NAMES.length; i++) {
		if (REACTION_NAMES[i].equals(content)) {
			return REACTION_EMOJIS[i];
		}
	}
	return content;
}
```

---

## Step 11: Implement `onReaction()` in `CommentOverlayInstaller`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentOverlayInstaller.java`

```java
@Override
public void onReaction(PullRequestComment comment,
		String content, boolean add, long reactionId) {
	if (currentPullRequest == null || client == null) {
		return;
	}
	Job job = new Job(PRText.Reaction_JobName) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				if (add) {
					client.addReaction(
							currentPullRequest.getId(),
							comment.getId(),
							comment.isReviewComment(),
							content);
				} else {
					client.removeReaction(
							currentPullRequest.getId(),
							comment.getId(),
							comment.isReviewComment(),
							reactionId);
				}
				Display.getDefault().asyncExec(
						() -> refreshComments());
				return Status.OK_STATUS;
			} catch (IOException e) {
				Activator.logError(
						PRText.Reaction_Error, e);
				return new Status(IStatus.ERROR,
						Activator.PLUGIN_ID,
						PRText.Reaction_Error, e);
			}
		}
	};
	job.setUser(true);
	job.schedule();
}
```

---

## Step 12: Add tests

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`

```java
@Test
public void testParseReactions() {
	String json = "[{\"id\":1,\"content\":\"+1\","
			+ "\"user\":{\"login\":\"user1\"}},"
			+ "{\"id\":2,\"content\":\"heart\","
			+ "\"user\":{\"login\":\"user2\"}}]";
	List<Reaction> reactions = GitHubJsonParser
			.parseReactions(json);
	assertThat(reactions, hasSize(2));
	assertThat(reactions.get(0).getContent(),
			equalTo("+1"));
	assertThat(reactions.get(0).getUserName(),
			equalTo("user1"));
	assertThat(reactions.get(1).getContent(),
			equalTo("heart"));
}

@Test
public void testParseSingleReaction() {
	String json = "{\"id\":42,\"content\":\"rocket\","
			+ "\"user\":{\"login\":\"dev1\"}}";
	Reaction r = GitHubJsonParser.parseReaction(json);
	assertThat(r.getId(), equalTo(42L));
	assertThat(r.getContent(), equalTo("rocket"));
	assertThat(r.getUserName(), equalTo("dev1"));
}
```

**File** (new): `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/model/ReactionSummaryTest.java`

```java
@Test
public void testReactionSummary() {
	List<Reaction> reactions = new ArrayList<>();
	Reaction r1 = new Reaction();
	r1.setId(1); r1.setContent("+1"); r1.setUserName("me");
	Reaction r2 = new Reaction();
	r2.setId(2); r2.setContent("+1"); r2.setUserName("other");
	Reaction r3 = new Reaction();
	r3.setId(3); r3.setContent("heart"); r3.setUserName("me");
	reactions.add(r1); reactions.add(r2); reactions.add(r3);

	ReactionSummary summary = new ReactionSummary(
			reactions, "me");
	assertThat(summary.getCounts().get("+1"),
			equalTo(2));
	assertThat(summary.getCounts().get("heart"),
			equalTo(1));
	assertThat(summary.hasCurrentUserReacted("+1"),
			equalTo(true));
	assertThat(summary.getCurrentUserReactionId("+1"),
			equalTo(1L));
}
```

---

## Verification

1. `mvn clean verify -DskipTests` — build succeeds
2. `cd org.eclipse.egit.pullrequest.test && mvn test` — all tests pass
3. Manual: Open a GitHub PR, verify reactions appear on comments, click
   to add/remove reactions, verify the "+" picker works
4. Manual: Open a Bitbucket PR, verify no reaction controls appear
