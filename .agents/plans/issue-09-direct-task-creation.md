# Issue #9: Direct Task Creation from Code Lines

## Overview

Allow users to create tasks directly from code lines in the diff viewer.
The behavior differs by provider:

- **Bitbucket**: Tasks are comments with severity `BLOCKER` and state tracking.
  An existing inline comment can be promoted to a task by updating its severity,
  or a new task can be created as a `BLOCKER`-severity inline comment.
- **GitHub**: GitHub has no native task concept on review comments. The closest
  equivalent is adding a checkbox markdown list item (`- [ ] description`) as a
  comment. Task completion toggles between `- [ ] ` and `- [x] `.

This issue also adds a checkbox/task indicator in the inline comment UI and a
"Create Task" action in the ruler column.

## Dependencies

- None. Can be implemented independently.
- Leverages existing `updateCommentSeverity()` in `IPullRequestClient` which
  is already implemented for Bitbucket and throws
  `UnsupportedOperationException` for GitHub.

## Implementation Order

No ordering constraints. Can be done in any position.

---

## Step 1: Add task-related fields to `PullRequestComment`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/PullRequestComment.java`

Add fields after `threadId` (after line 74):

```java
/**
 * Whether this comment is a task (Bitbucket: severity BLOCKER;
 * GitHub: body starts with "- [ ] " or "- [x] ").
 */
private boolean task;

/**
 * Whether this task is completed (Bitbucket: state RESOLVED;
 * GitHub: body starts with "- [x] ").
 */
private boolean taskCompleted;
```

Add getters/setters after `setThreadId()` (after line 383):

```java
/**
 * @return true if this comment is a task
 */
public boolean isTask() {
	return task;
}

/**
 * @param task
 *            true if this comment is a task
 */
public void setTask(boolean task) {
	this.task = task;
}

/**
 * @return true if this task is completed
 */
public boolean isTaskCompleted() {
	return taskCompleted;
}

/**
 * @param taskCompleted
 *            true if the task is completed
 */
public void setTaskCompleted(boolean taskCompleted) {
	this.taskCompleted = taskCompleted;
}
```

---

## Step 2: Add task API methods to `IPullRequestClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/client/IPullRequestClient.java`

Add after `deleteComment()` (after line 237):

```java
/**
 * Creates a task on a specific line in a pull request file.
 * For Bitbucket, this creates an inline comment with severity
 * BLOCKER. For GitHub, this creates an inline comment with a
 * checkbox markdown prefix.
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @param description
 *            the task description text
 * @param path
 *            the file path
 * @param line
 *            the line number (1-based)
 * @param lineType
 *            the line type: "ADDED", "REMOVED", or "CONTEXT"
 * @param fileType
 *            the file side: "FROM" or "TO"
 * @param commitId
 *            the commit SHA
 * @return the created comment (with task=true)
 * @throws IOException
 *             if the request fails
 */
@NonNull
PullRequestComment createTask(long pullRequestId,
		@NonNull String description, @NonNull String path,
		int line, @NonNull String lineType,
		@NonNull String fileType, @NonNull String commitId)
		throws IOException;

/**
 * Toggles a task's completion state. For Bitbucket, this
 * changes the comment state between OPEN and RESOLVED.
 * For GitHub, this edits the comment text to toggle between
 * "- [ ] " and "- [x] ".
 *
 * @param pullRequestId
 *            the pull request ID or number
 * @param commentId
 *            the comment ID
 * @param version
 *            the comment version
 * @param currentlyCompleted
 *            whether the task is currently completed
 * @param isReviewComment
 *            whether this is a review comment
 * @return the updated comment
 * @throws IOException
 *             if the request fails
 */
@NonNull
PullRequestComment toggleTaskState(long pullRequestId,
		long commentId, int version,
		boolean currentlyCompleted,
		boolean isReviewComment) throws IOException;
```

---

## Step 3: Implement in `BitbucketClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClient.java`

Add before `getReviewers()`:

```java
@Override
@NonNull
public PullRequestComment createTask(long pullRequestId,
		@NonNull String description, @NonNull String path,
		int line, @NonNull String lineType,
		@NonNull String fileType, @NonNull String commitId)
		throws IOException {
	// Create inline comment with BLOCKER severity
	PullRequestComment comment = addInlineComment(
			pullRequestId, description, path, line,
			lineType, fileType, commitId);
	// Update severity to BLOCKER to make it a task
	PullRequestComment updated = updateCommentSeverity(
			pullRequestId, comment.getId(),
			comment.getVersion(), "BLOCKER"); //$NON-NLS-1$
	updated.setTask(true);
	return updated;
}

@Override
@NonNull
public PullRequestComment toggleTaskState(
		long pullRequestId, long commentId, int version,
		boolean currentlyCompleted,
		boolean isReviewComment) throws IOException {
	String newState = currentlyCompleted
			? "OPEN" : "RESOLVED"; //$NON-NLS-1$ //$NON-NLS-2$
	PullRequestComment updated = updateCommentState(
			pullRequestId, commentId, version, newState);
	updated.setTaskCompleted(!currentlyCompleted);
	return updated;
}
```

---

## Step 4: Implement in `GitHubClient`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/github/GitHubClient.java`

Add before `getReviewers()`:

```java
@Override
@NonNull
public PullRequestComment createTask(long pullRequestId,
		@NonNull String description, @NonNull String path,
		int line, @NonNull String lineType,
		@NonNull String fileType, @NonNull String commitId)
		throws IOException {
	// Wrap description in markdown checkbox
	String taskBody = "- [ ] " + description; //$NON-NLS-1$
	PullRequestComment comment = addInlineComment(
			pullRequestId, taskBody, path, line,
			lineType, fileType, commitId);
	comment.setTask(true);
	comment.setTaskCompleted(false);
	return comment;
}

@Override
@NonNull
public PullRequestComment toggleTaskState(
		long pullRequestId, long commentId, int version,
		boolean currentlyCompleted,
		boolean isReviewComment) throws IOException {
	// Edit the comment text to toggle checkbox
	// First, get current comment text by fetching it
	String getPath;
	if (isReviewComment) {
		getPath = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/pulls/comments/" + commentId; //$NON-NLS-1$
	} else {
		getPath = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/issues/comments/" + commentId; //$NON-NLS-1$
	}
	String response = doGet(getPath);
	String currentText = GitHubJsonParser
			.extractStringValue(response, "body"); //$NON-NLS-1$

	String newText;
	if (currentlyCompleted) {
		// Uncheck: "- [x] " -> "- [ ] "
		newText = currentText.replace(
				"- [x] ", "- [ ] "); //$NON-NLS-1$ //$NON-NLS-2$
	} else {
		// Check: "- [ ] " -> "- [x] "
		newText = currentText.replace(
				"- [ ] ", "- [x] "); //$NON-NLS-1$ //$NON-NLS-2$
	}

	PullRequestComment updated = editComment(
			pullRequestId, commentId, version,
			newText, isReviewComment);
	updated.setTask(true);
	updated.setTaskCompleted(!currentlyCompleted);
	return updated;
}
```

---

## Step 5: Update JSON parsers to detect tasks

### 5a. `BitbucketJsonParser`

When parsing comments, detect task status from severity:

```java
// After setting severity on comment:
if ("BLOCKER".equals(severity)) { //$NON-NLS-1$
	comment.setTask(true);
	comment.setTaskCompleted(
			"RESOLVED".equals(comment.getState())); //$NON-NLS-1$
}
```

### 5b. `GitHubJsonParser`

When parsing comments, detect task from checkbox prefix:

```java
// After setting text on comment:
String text = comment.getText();
if (text != null) {
	if (text.startsWith("- [ ] ")) { //$NON-NLS-1$
		comment.setTask(true);
		comment.setTaskCompleted(false);
	} else if (text.startsWith("- [x] ")) { //$NON-NLS-1$
		comment.setTask(true);
		comment.setTaskCompleted(true);
	}
}
```

---

## Step 6: Add NLS strings

**File**: `PRText.java` — add before `static {}`:

```java
/** */
public static String Task_CreateAction;

/** */
public static String Task_CreateTooltip;

/** */
public static String Task_CreateDialogTitle;

/** */
public static String Task_CreateDialogMessage;

/** */
public static String Task_CreateJobName;

/** */
public static String Task_CreateError;

/** */
public static String Task_ToggleCompleteTooltip;

/** */
public static String Task_ToggleJobName;

/** */
public static String Task_ToggleError;

/** */
public static String Task_Indicator;

/** */
public static String Task_CompletedIndicator;
```

**File**: `prtext.properties` — append:

```properties
Task_CreateAction=Create Task
Task_CreateTooltip=Create a new task on this line
Task_CreateDialogTitle=Create Task
Task_CreateDialogMessage=Enter task description:
Task_CreateJobName=Creating task
Task_CreateError=Failed to create task
Task_ToggleCompleteTooltip=Toggle task completion
Task_ToggleJobName=Toggling task state
Task_ToggleError=Failed to toggle task state
Task_Indicator=\u2610
Task_CompletedIndicator=\u2611
```

---

## Step 7: Add task indicator to `ExpandedCommentComposite`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/ExpandedCommentComposite.java`

### 7a. Add callback to `CommentActionHandler`

Add after existing methods in the interface:

```java
/**
 * Called when the user toggles a task's completion state.
 *
 * @param comment
 *            the task comment
 */
void onToggleTask(PullRequestComment comment);
```

### 7b. Add task indicator in `renderComment()`

In `renderComment()`, before the body text block (before line 432),
add task indicator if the comment is a task:

```java
// Task indicator (checkbox)
if (comment.isTask()) {
	Composite taskRow = new Composite(
			commentArea, SWT.NONE);
	taskRow.setBackground(parent.getBackground());
	GridLayoutFactory.fillDefaults().numColumns(2)
			.margins(8, 2).spacing(4, 0)
			.applyTo(taskRow);
	GridDataFactory.fillDefaults().grab(true, false)
			.applyTo(taskRow);

	String indicator = comment.isTaskCompleted()
			? PRText.Task_CompletedIndicator
			: PRText.Task_Indicator;
	Link taskLink = new Link(taskRow, SWT.NONE);
	taskLink.setText("<a>" + indicator + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$
	taskLink.setFont(boldFont);
	taskLink.setForeground(linkColor);
	taskLink.setBackground(parent.getBackground());
	taskLink.setToolTipText(
			PRText.Task_ToggleCompleteTooltip);
	if (handler != null) {
		taskLink.addListener(SWT.Selection,
				e -> handler.onToggleTask(comment));
	}

	Label taskLabel = new Label(taskRow, SWT.NONE);
	taskLabel.setText("Task"); //$NON-NLS-1$
	taskLabel.setFont(boldFont);
	taskLabel.setForeground(authorColor);
	taskLabel.setBackground(parent.getBackground());
}
```

---

## Step 8: Add "Create Task" to `CommentRulerColumn` context menu

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentRulerColumn.java`

### 8a. Add new callback interface

Add after `NewCommentClickHandler` (after line 84):

```java
/**
 * Callback for creating a task on a specific line.
 */
@FunctionalInterface
public interface CreateTaskHandler {

	/**
	 * Called when the user requests to create a task.
	 *
	 * @param line
	 *            the 1-based line number
	 */
	void onCreateTask(int line);
}
```

### 8b. Add field and setter

```java
private CreateTaskHandler createTaskHandler;

/**
 * Sets the handler for creating tasks.
 *
 * @param handler
 *            the handler
 */
public void setCreateTaskHandler(
		CreateTaskHandler handler) {
	this.createTaskHandler = handler;
}
```

### 8c. Add right-click context menu

In the mouse listener where clicks are handled, add a right-click
handler that shows a popup menu with "Create Task":

```java
// In mouseUp or mouseDown handler for right-click:
if (e.button == 3) { // right-click
	int clickedLine = getLineOfLastMouseButtonActivity();
	if (clickedLine >= 0 && createTaskHandler != null) {
		Menu menu = new Menu(getControl());
		MenuItem taskItem = new MenuItem(menu, SWT.PUSH);
		taskItem.setText(PRText.Task_CreateAction);
		taskItem.addListener(SWT.Selection,
				evt -> createTaskHandler.onCreateTask(
						clickedLine + 1));
		menu.setVisible(true);
	}
}
```

---

## Step 9: Implement task handlers in `CommentOverlayInstaller`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentOverlayInstaller.java`

### 9a. Implement `onToggleTask()`

```java
@Override
public void onToggleTask(PullRequestComment comment) {
	if (currentPullRequest == null || client == null) {
		return;
	}
	Job job = new Job(PRText.Task_ToggleJobName) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				client.toggleTaskState(
						currentPullRequest.getId(),
						comment.getId(),
						comment.getVersion(),
						comment.isTaskCompleted(),
						comment.isReviewComment());
				Display.getDefault().asyncExec(
						() -> refreshComments());
				return Status.OK_STATUS;
			} catch (IOException e) {
				Activator.logError(
						PRText.Task_ToggleError, e);
				return new Status(IStatus.ERROR,
						Activator.PLUGIN_ID,
						PRText.Task_ToggleError, e);
			}
		}
	};
	job.setUser(true);
	job.schedule();
}
```

### 9b. Wire up `CreateTaskHandler`

When setting up the `CommentRulerColumn`, wire the create task
handler:

```java
rulerColumn.setCreateTaskHandler(line -> {
	// Prompt for description
	Display.getDefault().asyncExec(() -> {
		MultiLineInputDialog dialog =
				new MultiLineInputDialog(
						Display.getDefault()
								.getActiveShell(),
						PRText.Task_CreateDialogTitle,
						PRText.Task_CreateDialogMessage,
						""); //$NON-NLS-1$
		if (dialog.open() != Window.OK) {
			return;
		}
		String desc = dialog.getValue();
		if (desc == null || desc.trim().isEmpty()) {
			return;
		}
		createTaskOnLine(line, desc);
	});
});
```

```java
private void createTaskOnLine(int line, String desc) {
	if (currentPullRequest == null || client == null) {
		return;
	}
	Job job = new Job(PRText.Task_CreateJobName) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				// Determine lineType and fileType from
				// the diff context
				String lineType = "CONTEXT"; //$NON-NLS-1$
				String fileType = "TO"; //$NON-NLS-1$
				String commitId = currentPullRequest
						.getFromRef().getLatestCommit();
				client.createTask(
						currentPullRequest.getId(),
						desc, currentFilePath, line,
						lineType, fileType, commitId);
				Display.getDefault().asyncExec(
						() -> refreshComments());
				return Status.OK_STATUS;
			} catch (IOException e) {
				Activator.logError(
						PRText.Task_CreateError, e);
				return new Status(IStatus.ERROR,
						Activator.PLUGIN_ID,
						PRText.Task_CreateError, e);
			}
		}
	};
	job.setUser(true);
	job.schedule();
}
```

---

## Step 10: Add tests

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/github/GitHubJsonParserTest.java`

```java
@Test
public void testParseTaskComment() {
	String body = "- [ ] Fix the null check";
	PullRequestComment comment = new PullRequestComment();
	comment.setText(body);
	// Simulate parser detection
	if (body.startsWith("- [ ] ")) {
		comment.setTask(true);
		comment.setTaskCompleted(false);
	}
	assertThat(comment.isTask(), equalTo(true));
	assertThat(comment.isTaskCompleted(), equalTo(false));
}

@Test
public void testParseCompletedTaskComment() {
	String body = "- [x] Fix the null check";
	PullRequestComment comment = new PullRequestComment();
	comment.setText(body);
	if (body.startsWith("- [x] ")) {
		comment.setTask(true);
		comment.setTaskCompleted(true);
	}
	assertThat(comment.isTask(), equalTo(true));
	assertThat(comment.isTaskCompleted(), equalTo(true));
}
```

**File**: `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/bitbucket/BitbucketClientTest.java`

```java
@Test
public void testParseBlockerCommentAsTask() {
	PullRequestComment comment = new PullRequestComment();
	comment.setSeverity("BLOCKER");
	comment.setState("OPEN");
	// Simulate parser detection
	if ("BLOCKER".equals(comment.getSeverity())) {
		comment.setTask(true);
		comment.setTaskCompleted(
				"RESOLVED".equals(comment.getState()));
	}
	assertThat(comment.isTask(), equalTo(true));
	assertThat(comment.isTaskCompleted(), equalTo(false));
}
```

---

## Verification

1. `mvn clean verify -DskipTests` — build succeeds
2. `cd org.eclipse.egit.pullrequest.test && mvn test` — all tests pass
3. Manual: Right-click in ruler, select "Create Task", enter description,
   verify task appears with checkbox indicator. Toggle completion.
