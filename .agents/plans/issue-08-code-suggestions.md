# Issue #8: Code Suggestions (Parse + Apply)

## Overview

Support GitHub-style code suggestion blocks in review comments. When a comment
contains a fenced code block tagged as ` ```suggestion `, the plugin should:

1. **Parse** the suggestion block out of the comment body.
2. **Render** it as a diff-style before/after view inside the inline comment
   composite.
3. Provide an **"Apply Suggestion"** button that modifies the workspace file
   to replace the original lines with the suggested code.

Bitbucket Data Center does not have a native code suggestion format, but this
feature should still parse any comment body that uses the ` ```suggestion `
convention (manually authored or from integrations).

## Dependencies

- None. Can be implemented independently.

## Implementation Order

No ordering constraints. Can be done in any position.

---

## Step 1: Create `SuggestionParser` utility

**File** (new): `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/model/SuggestionParser.java`

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses GitHub-style code suggestion blocks from comment text.
 * A suggestion block has the format:
 * <pre>
 * ```suggestion
 * replacement line 1
 * replacement line 2
 * ```
 * </pre>
 */
public class SuggestionParser {

	/**
	 * Represents a single code suggestion extracted from a
	 * comment.
	 */
	public static class Suggestion {

		private final String replacementText;

		private final int startInBody;

		private final int endInBody;

		/**
		 * Creates a new suggestion.
		 *
		 * @param replacementText
		 *            the suggested replacement code
		 * @param startInBody
		 *            start index in the comment body
		 * @param endInBody
		 *            end index in the comment body
		 */
		public Suggestion(String replacementText,
				int startInBody, int endInBody) {
			this.replacementText = replacementText;
			this.startInBody = startInBody;
			this.endInBody = endInBody;
		}

		/**
		 * @return the suggested replacement code
		 */
		public String getReplacementText() {
			return replacementText;
		}

		/**
		 * @return start index in the comment body
		 */
		public int getStartInBody() {
			return startInBody;
		}

		/**
		 * @return end index in the comment body
		 */
		public int getEndInBody() {
			return endInBody;
		}
	}

	private SuggestionParser() {
		// Static utility class
	}

	/**
	 * Parses all suggestion blocks from the given comment text.
	 *
	 * @param commentBody
	 *            the full comment body text
	 * @return list of suggestions found, never null
	 */
	public static List<Suggestion> parse(String commentBody) {
		if (commentBody == null || commentBody.isEmpty()) {
			return Collections.emptyList();
		}

		List<Suggestion> suggestions = new ArrayList<>();
		String marker = "```suggestion"; //$NON-NLS-1$
		String endMarker = "```"; //$NON-NLS-1$

		int searchFrom = 0;
		while (true) {
			int start = commentBody.indexOf(
					marker, searchFrom);
			if (start == -1) {
				break;
			}

			// Find the end of the opening marker line
			int lineEnd = commentBody.indexOf(
					'\n', start + marker.length());
			if (lineEnd == -1) {
				break;
			}

			// Find the closing ```
			int codeStart = lineEnd + 1;
			int end = commentBody.indexOf(
					endMarker, codeStart);
			if (end == -1) {
				break;
			}

			// Ensure the closing ``` is at the start of a
			// line (or preceded by only whitespace)
			String replacement = commentBody.substring(
					codeStart, end);
			// Remove trailing newline if present
			if (replacement.endsWith("\n")) { //$NON-NLS-1$
				replacement = replacement.substring(
						0, replacement.length() - 1);
			}

			suggestions.add(new Suggestion(
					replacement, start,
					end + endMarker.length()));

			searchFrom = end + endMarker.length();
		}

		return suggestions;
	}

	/**
	 * Checks whether the given comment body contains any
	 * suggestion blocks.
	 *
	 * @param commentBody
	 *            the comment body text
	 * @return true if at least one suggestion is present
	 */
	public static boolean hasSuggestion(String commentBody) {
		return commentBody != null
				&& commentBody.contains(
						"```suggestion"); //$NON-NLS-1$
	}
}
```

---

## Step 2: Add NLS strings

**File**: `PRText.java` — add before `static {}` block:

```java
/** */
public static String Suggestion_ApplyAction;

/** */
public static String Suggestion_ApplyTooltip;

/** */
public static String Suggestion_ApplyJobName;

/** */
public static String Suggestion_ApplyError;

/** */
public static String Suggestion_ApplySuccess;

/** */
public static String Suggestion_OriginalLabel;

/** */
public static String Suggestion_SuggestedLabel;

/** */
public static String Suggestion_FileNotFound;

/** */
public static String Suggestion_SuggestChangeAction;
```

**File**: `prtext.properties` — append:

```properties
Suggestion_ApplyAction=Apply Suggestion
Suggestion_ApplyTooltip=Replace the original code with this suggestion
Suggestion_ApplyJobName=Applying code suggestion
Suggestion_ApplyError=Failed to apply suggestion
Suggestion_ApplySuccess=Suggestion applied successfully
Suggestion_OriginalLabel=Original:
Suggestion_SuggestedLabel=Suggested:
Suggestion_FileNotFound=Cannot find workspace file to apply suggestion
Suggestion_SuggestChangeAction=Suggest Change
```

---

## Step 3: Render suggestion blocks in `ExpandedCommentComposite`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/ExpandedCommentComposite.java`

### 3a. Add callback to `CommentActionHandler`

Add after `onSelect()` at line 109:

```java
/**
 * Called when the user clicks "Apply Suggestion".
 *
 * @param comment
 *            the comment containing the suggestion
 * @param suggestion
 *            the suggestion to apply
 */
void onApplySuggestion(PullRequestComment comment,
		SuggestionParser.Suggestion suggestion);
```

Add import:
```java
import org.eclipse.egit.pullrequest.internal.model.SuggestionParser;
```

### 3b. Modify body rendering in `renderComment()`

In `renderComment()` at line 432, after the body text label, add
suggestion rendering. Replace the body-rendering block
(lines 432-459) with:

```java
// Body text with improved styling
String bodyText = comment.getText();
if (bodyText != null && !bodyText.isEmpty()) {
	// Check for suggestion blocks
	List<SuggestionParser.Suggestion> suggestions =
			SuggestionParser.parse(bodyText);

	if (suggestions.isEmpty()) {
		// Normal body rendering (existing code)
		renderPlainBody(commentArea, parent,
				bodyText, comment, handler);
	} else {
		// Render text before first suggestion
		int lastEnd = 0;
		for (SuggestionParser.Suggestion s : suggestions) {
			// Text before this suggestion
			if (s.getStartInBody() > lastEnd) {
				String before = bodyText.substring(
						lastEnd, s.getStartInBody()).trim();
				if (!before.isEmpty()) {
					renderPlainBody(commentArea, parent,
							before, comment, handler);
				}
			}
			// Render the suggestion diff-style
			renderSuggestionBlock(commentArea, parent,
					comment, s, handler);
			lastEnd = s.getEndInBody();
		}
		// Text after last suggestion
		if (lastEnd < bodyText.length()) {
			String after = bodyText.substring(
					lastEnd).trim();
			if (!after.isEmpty()) {
				renderPlainBody(commentArea, parent,
						after, comment, handler);
			}
		}
	}
}
```

### 3c. Extract existing body rendering into helper

```java
private void renderPlainBody(Composite commentArea,
		Composite parent, String bodyText,
		PullRequestComment comment,
		CommentActionHandler handler) {
	Composite bodyContainer = new Composite(
			commentArea, SWT.NONE);
	bodyContainer.setBackground(parent.getBackground());
	GridLayoutFactory.fillDefaults().margins(8, 6)
			.applyTo(bodyContainer);
	GridDataFactory.fillDefaults().grab(true, false)
			.applyTo(bodyContainer);

	Label bodyLabel = new Label(bodyContainer, SWT.WRAP);
	bodyLabel.setText(bodyText);
	bodyLabel.setForeground(bodyTextColor);
	bodyLabel.setBackground(parent.getBackground());
	GridDataFactory.fillDefaults().grab(true, false)
			.hint(400, SWT.DEFAULT).applyTo(bodyLabel);

	if (handler != null) {
		bodyContainer.setCursor(getDisplay()
				.getSystemCursor(SWT.CURSOR_HAND));
		bodyContainer.addListener(SWT.MouseDown,
				e -> handler.onSelect(comment));
		bodyLabel.setCursor(getDisplay()
				.getSystemCursor(SWT.CURSOR_HAND));
		bodyLabel.addListener(SWT.MouseDown,
				e -> handler.onSelect(comment));
	}
}
```

### 3d. Add suggestion block renderer

```java
private static final RGB SUGGESTION_ADD_BG_RGB =
		new RGB(230, 255, 237);
private static final RGB SUGGESTION_DEL_BG_RGB =
		new RGB(255, 235, 233);

private void renderSuggestionBlock(Composite commentArea,
		Composite parent, PullRequestComment comment,
		SuggestionParser.Suggestion suggestion,
		CommentActionHandler handler) {
	Composite suggBlock = new Composite(
			commentArea, SWT.BORDER);
	suggBlock.setBackground(parent.getBackground());
	GridLayoutFactory.fillDefaults().margins(8, 4)
			.spacing(0, 2).applyTo(suggBlock);
	GridDataFactory.fillDefaults().grab(true, false)
			.indent(8, 4).applyTo(suggBlock);

	// "Suggested:" label
	Label suggLabel = new Label(suggBlock, SWT.NONE);
	suggLabel.setText(
			PRText.Suggestion_SuggestedLabel);
	suggLabel.setFont(boldFont);
	suggLabel.setForeground(authorColor);
	suggLabel.setBackground(parent.getBackground());

	// Suggestion text with green background
	Color addBg = new Color(getDisplay(),
			SUGGESTION_ADD_BG_RGB);
	Label codeLabel = new Label(suggBlock, SWT.WRAP);
	codeLabel.setText(suggestion.getReplacementText());
	codeLabel.setBackground(addBg);
	codeLabel.setForeground(bodyTextColor);
	GridDataFactory.fillDefaults().grab(true, false)
			.hint(400, SWT.DEFAULT).applyTo(codeLabel);

	// "Apply Suggestion" button
	if (handler != null) {
		Link applyLink = createStyledLink(suggBlock,
				PRText.Suggestion_ApplyAction);
		applyLink.setToolTipText(
				PRText.Suggestion_ApplyTooltip);
		applyLink.addListener(SWT.Selection,
				e -> handler.onApplySuggestion(
						comment, suggestion));
	}
}
```

---

## Step 4: Implement `onApplySuggestion()` in `CommentOverlayInstaller`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentOverlayInstaller.java`

The `CommentOverlayInstaller` implements `CommentActionHandler`. Add
the new method implementation:

```java
@Override
public void onApplySuggestion(PullRequestComment comment,
		SuggestionParser.Suggestion suggestion) {
	if (comment.getPath() == null
			|| comment.getLine() == null) {
		return;
	}

	Job job = new Job(PRText.Suggestion_ApplyJobName) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				applySuggestionToFile(comment, suggestion);
				return Status.OK_STATUS;
			} catch (Exception e) {
				Activator.logError(
						PRText.Suggestion_ApplyError, e);
				return new Status(IStatus.ERROR,
						Activator.PLUGIN_ID,
						PRText.Suggestion_ApplyError, e);
			}
		}
	};
	job.setUser(true);
	job.schedule();
}

/**
 * Applies a suggestion by replacing the commented line(s) in the
 * workspace file.
 */
private void applySuggestionToFile(
		PullRequestComment comment,
		SuggestionParser.Suggestion suggestion)
		throws Exception {
	// Find the workspace IFile for this path
	String filePath = comment.getPath();
	IFile[] files = ResourcesPlugin.getWorkspace().getRoot()
			.findFilesForLocationURI(
					new java.io.File(filePath).toURI());

	// Alternative: search by relative path in project
	if (files.length == 0) {
		// Try to resolve via project-relative path
		IProject[] projects = ResourcesPlugin.getWorkspace()
				.getRoot().getProjects();
		for (IProject project : projects) {
			IFile f = project.getFile(filePath);
			if (f.exists()) {
				files = new IFile[] { f };
				break;
			}
		}
	}

	if (files.length == 0) {
		throw new IOException(
				PRText.Suggestion_FileNotFound);
	}

	IFile file = files[0];
	int lineNum = comment.getLine();

	// Read file content
	String content;
	try (InputStream is = file.getContents()) {
		content = new String(is.readAllBytes(),
				file.getCharset());
	}

	// Split into lines and replace the target line
	String[] lines = content.split("\n", -1); //$NON-NLS-1$
	if (lineNum < 1 || lineNum > lines.length) {
		throw new IOException(
				"Line " + lineNum //$NON-NLS-1$
						+ " out of range"); //$NON-NLS-1$
	}

	// Replace the single line with the suggestion text
	StringBuilder result = new StringBuilder();
	for (int i = 0; i < lines.length; i++) {
		if (i == lineNum - 1) {
			result.append(
					suggestion.getReplacementText());
		} else {
			result.append(lines[i]);
		}
		if (i < lines.length - 1) {
			result.append('\n');
		}
	}

	// Write back
	byte[] bytes = result.toString().getBytes(
			file.getCharset());
	file.setContents(
			new java.io.ByteArrayInputStream(bytes),
			IFile.FORCE, null);
}
```

---

## Step 5: Add "Suggest Change" menu item to `CommentRulerColumn`

**File**: `org.eclipse.egit.pullrequest/src/org/eclipse/egit/pullrequest/internal/ui/CommentRulerColumn.java`

This is **optional** for the first iteration. If desired, add a
right-click context menu on the "+" icon that includes "Suggest Change"
which pre-fills the comment text with the ` ```suggestion ` template:

```java
// In the mouse handler for new comment clicks, add context menu:
Menu menu = new Menu(getControl());
MenuItem suggestItem = new MenuItem(menu, SWT.PUSH);
suggestItem.setText(PRText.Suggestion_SuggestChangeAction);
suggestItem.addListener(SWT.Selection, e -> {
	// Pre-fill with suggestion template
	String template = "```suggestion\n"  //$NON-NLS-1$
			+ "// replacement code here\n"  //$NON-NLS-1$
			+ "```";  //$NON-NLS-1$
	if (newCommentHandler != null) {
		newCommentHandler.onNewCommentClickWithTemplate(
				line, template);
	}
});
```

This requires extending `NewCommentClickHandler` with an optional
template-aware method, or passing the template through a different
mechanism. **Defer to a follow-up if complexity is too high.**

---

## Step 6: Add tests

**File** (new): `org.eclipse.egit.pullrequest.test/src/org/eclipse/egit/pullrequest/internal/model/SuggestionParserTest.java`

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.Test;

public class SuggestionParserTest {

	@Test
	public void testParseSingleSuggestion() {
		String body = "Try this instead:\n"
				+ "```suggestion\n"
				+ "int x = 42;\n"
				+ "```\n"
				+ "This should fix the issue.";
		List<SuggestionParser.Suggestion> suggestions =
				SuggestionParser.parse(body);
		assertThat(suggestions, hasSize(1));
		assertThat(suggestions.get(0).getReplacementText(),
				equalTo("int x = 42;"));
	}

	@Test
	public void testParseMultipleSuggestions() {
		String body = "```suggestion\nline1\n```\n"
				+ "and also\n"
				+ "```suggestion\nline2\n```";
		List<SuggestionParser.Suggestion> suggestions =
				SuggestionParser.parse(body);
		assertThat(suggestions, hasSize(2));
		assertThat(suggestions.get(0).getReplacementText(),
				equalTo("line1"));
		assertThat(suggestions.get(1).getReplacementText(),
				equalTo("line2"));
	}

	@Test
	public void testParseNoSuggestion() {
		String body = "Regular comment with ```code``` blocks";
		List<SuggestionParser.Suggestion> suggestions =
				SuggestionParser.parse(body);
		assertThat(suggestions, hasSize(0));
	}

	@Test
	public void testHasSuggestion() {
		assertThat(SuggestionParser.hasSuggestion(
				"```suggestion\nfoo\n```"),
				is(true));
		assertThat(SuggestionParser.hasSuggestion(
				"no suggestion here"),
				is(false));
	}

	@Test
	public void testParseNullBody() {
		assertThat(SuggestionParser.parse(null),
				hasSize(0));
	}

	@Test
	public void testMultiLineSuggestion() {
		String body = "```suggestion\n"
				+ "line 1\n"
				+ "line 2\n"
				+ "line 3\n"
				+ "```";
		List<SuggestionParser.Suggestion> suggestions =
				SuggestionParser.parse(body);
		assertThat(suggestions, hasSize(1));
		assertThat(suggestions.get(0).getReplacementText(),
				equalTo("line 1\nline 2\nline 3"));
	}
}
```

---

## Verification

1. `mvn clean verify -DskipTests` — build succeeds
2. `cd org.eclipse.egit.pullrequest.test && mvn test` — all tests pass
   (including new `SuggestionParserTest`)
3. Manual: Create a PR comment with ` ```suggestion ` block, verify it
   renders as a diff-style suggestion with "Apply Suggestion" button.
   Click apply, verify the workspace file is modified.
