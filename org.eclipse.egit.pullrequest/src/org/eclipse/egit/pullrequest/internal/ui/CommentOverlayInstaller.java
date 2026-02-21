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
package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.compare.contentmergeviewer.TextMergeViewer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.egit.pullrequest.internal.model.DiffHunkParser;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * Installs pull request comment overlays (ruler columns and expandable
 * comment composites) on any {@link TextMergeViewer} instance. This
 * allows the Eclipse compare framework to select the appropriate
 * language-specific viewer (e.g. Java Source Compare, XML Compare)
 * while still providing inline comment support.
 *
 * <p>
 * The installer uses reflection to access the left and right
 * {@link SourceViewer} instances from the {@link TextMergeViewer},
 * then installs {@link CommentRulerColumn} instances on each side.
 * </p>
 */
class CommentOverlayInstaller {

	private SourceViewer leftSourceViewer;

	private SourceViewer rightSourceViewer;

	private CommentRulerColumn leftRulerColumn;

	private CommentRulerColumn rightRulerColumn;

	private ExpandedCommentComposite leftExpandedComposite;

	private ExpandedCommentComposite rightExpandedComposite;

	private List<PullRequestComment> currentComments;

	private String currentFilePath;

	private DiffHunkParser.DiffLines diffLines;

	private final Viewer viewer;

	private String currentUsername;

	private PullRequestProviderType providerType;

	/**
	 * Creates a new installer for the given viewer.
	 *
	 * @param viewer
	 *            the merge viewer returned by the compare framework
	 */
	CommentOverlayInstaller(Viewer viewer) {
		this.viewer = viewer;
	}

	/**
	 * Sets the file path for the currently displayed file. This is
	 * used when creating new comments.
	 *
	 * @param filePath
	 *            the file path
	 */
	void setFilePath(String filePath) {
		this.currentFilePath = filePath;
	}

	/**
	 * Sets the parsed diff lines for the current file. These
	 * indicate which line numbers are valid targets for new
	 * inline comments.
	 *
	 * @param diffLines
	 *            the diff lines, or {@code null} if not available
	 */
	void setDiffLines(DiffHunkParser.DiffLines diffLines) {
		this.diffLines = diffLines;
	}

	/**
	 * Installs comment overlays on the viewer. Extracts left and right
	 * {@link SourceViewer} instances via reflection and installs
	 * {@link CommentRulerColumn} instances on each.
	 *
	 * @param comments
	 *            the comments for the current file
	 */
	void installComments(List<PullRequestComment> comments) {
		if (!(viewer instanceof TextMergeViewer)) {
			return;
		}

		TextMergeViewer tmv = (TextMergeViewer) viewer;
		extractSourceViewers(tmv);

		if (leftSourceViewer == null && rightSourceViewer == null) {
			// Source viewers not yet available; retry after a delay
			scheduleRetry(comments, 0);
			return;
		}

		applyComments(comments);
	}

	/**
	 * Extracts the left and right {@link SourceViewer} from a
	 * {@link TextMergeViewer} using reflection. The field names are
	 * {@code fLeft} and {@code fRight} in
	 * {@link TextMergeViewer}, each of which is a
	 * {@code MergeSourceViewer} wrapping a {@link SourceViewer}.
	 *
	 * @param tmv
	 *            the text merge viewer
	 */
	private void extractSourceViewers(TextMergeViewer tmv) {
		if (leftSourceViewer != null && rightSourceViewer != null) {
			return;
		}

		try {
			// TextMergeViewer has fLeft and fRight fields of type
			// MergeSourceViewer which wraps a SourceViewer
			leftSourceViewer = extractSourceViewer(tmv,
					"fLeft"); //$NON-NLS-1$
			rightSourceViewer = extractSourceViewer(tmv,
					"fRight"); //$NON-NLS-1$
		} catch (Exception e) {
			Activator.logError(
					"Failed to extract source viewers " //$NON-NLS-1$
							+ "from TextMergeViewer", //$NON-NLS-1$
					e);
		}
	}

	/**
	 * Extracts a {@link SourceViewer} from a field of
	 * {@link TextMergeViewer}.
	 *
	 * @param tmv
	 *            the text merge viewer
	 * @param fieldName
	 *            the field name ({@code "fLeft"} or
	 *            {@code "fRight"})
	 * @return the source viewer, or {@code null} if not available
	 * @throws Exception
	 *             if reflection fails
	 */
	@SuppressWarnings("restriction")
	private SourceViewer extractSourceViewer(TextMergeViewer tmv,
			String fieldName) throws Exception {
		// Access the MergeSourceViewer field from TextMergeViewer
		Field field = findField(tmv.getClass(), fieldName);
		if (field == null) {
			return null;
		}
		field.setAccessible(true);
		Object mergeSourceViewer = field.get(tmv);
		if (mergeSourceViewer == null) {
			return null;
		}

		// MergeSourceViewer wraps a SourceViewer. Try to get it
		// via the getSourceViewer() method first, then fall back
		// to the fSourceViewer field.
		try {
			java.lang.reflect.Method getSourceViewer =
					mergeSourceViewer.getClass()
							.getMethod("getSourceViewer"); //$NON-NLS-1$
			Object sv = getSourceViewer.invoke(mergeSourceViewer);
			if (sv instanceof SourceViewer) {
				return (SourceViewer) sv;
			}
		} catch (NoSuchMethodException e) {
			// Try field access instead
		}

		// Fallback: try fSourceViewer field
		Field svField = findField(mergeSourceViewer.getClass(),
				"fSourceViewer"); //$NON-NLS-1$
		if (svField != null) {
			svField.setAccessible(true);
			Object sv = svField.get(mergeSourceViewer);
			if (sv instanceof SourceViewer) {
				return (SourceViewer) sv;
			}
		}

		return null;
	}

	/**
	 * Finds a field by name in the class hierarchy.
	 *
	 * @param clazz
	 *            the class to search
	 * @param fieldName
	 *            the field name
	 * @return the field, or {@code null} if not found
	 */
	private Field findField(Class<?> clazz, String fieldName) {
		Class<?> current = clazz;
		while (current != null) {
			try {
				return current.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {
				current = current.getSuperclass();
			}
		}
		return null;
	}

	/**
	 * Schedules a retry to install comments after a short delay.
	 *
	 * @param comments
	 *            the comments to install
	 * @param retryCount
	 *            the current retry count
	 */
	private void scheduleRetry(List<PullRequestComment> comments,
			int retryCount) {
		if (retryCount >= 10) {
			Activator.logWarning(
					"Could not install comment overlays: " //$NON-NLS-1$
							+ "source viewers not available" //$NON-NLS-1$
							+ " after retries"); //$NON-NLS-1$
			return;
		}

		if (viewer.getControl() == null
				|| viewer.getControl().isDisposed()) {
			return;
		}

		viewer.getControl().getDisplay().timerExec(100, () -> {
			if (viewer.getControl() != null
					&& !viewer.getControl().isDisposed()) {
				if (viewer instanceof TextMergeViewer) {
					extractSourceViewers(
							(TextMergeViewer) viewer);
				}

				if (leftSourceViewer != null
						|| rightSourceViewer != null) {
					applyComments(comments);
				} else {
					scheduleRetry(comments, retryCount + 1);
				}
			}
		});
	}

	/**
	 * Applies comments to the left and right source viewers by
	 * installing {@link CommentRulerColumn} instances.
	 *
	 * @param comments
	 *            the list of comments for the current file
	 */
	private void applyComments(List<PullRequestComment> comments) {
		// Collapse any expanded comment
		collapseExpanded(leftSourceViewer, true);
		collapseExpanded(rightSourceViewer, false);

		// Always install ruler columns so the "+" add-comment icon
		// is available even when there are no existing comments
		installRulerColumn(leftSourceViewer, true);
		installRulerColumn(rightSourceViewer, false);

		if (comments == null || comments.isEmpty()) {
			if (leftRulerColumn != null) {
				leftRulerColumn.setComments(null);
			}
			if (rightRulerColumn != null) {
				rightRulerColumn.setComments(null);
			}
			return;
		}

		currentComments = new ArrayList<>(comments);

		// Extract file path from comments if not already set
		if (currentFilePath == null) {
			for (PullRequestComment c : comments) {
				if (c.getPath() != null) {
					currentFilePath = c.getPath();
					break;
				}
			}
		}

		// Separate comments by side
		List<PullRequestComment> leftComments = new ArrayList<>();
		List<PullRequestComment> rightComments = new ArrayList<>();

		for (PullRequestComment comment : comments) {
			if (!comment.isInlineComment()) {
				continue;
			}
			if (comment.getLine() == null
					|| comment.getLine().intValue() < 1) {
				continue;
			}

			// Note: We now show resolved comments too, so users can see them
			// and potentially unresolve them. The visual distinction is handled
			// by CommentRulerColumn (different icon color for resolved comments)

			String fileType = comment.getFileType();
			if ("FROM".equals(fileType)) { //$NON-NLS-1$
				leftComments.add(comment);
			} else {
				rightComments.add(comment);
			}
		}

		if (leftRulerColumn != null) {
			leftRulerColumn.setComments(leftComments);
		}
		if (rightRulerColumn != null) {
			rightRulerColumn.setComments(rightComments);
		}
	}

	/**
	 * Installs a {@link CommentRulerColumn} on the given source
	 * viewer.
	 *
	 * @param sv
	 *            the source viewer
	 * @param isLeft
	 *            {@code true} for the left side
	 */
	private void installRulerColumn(SourceViewer sv,
			boolean isLeft) {
		if (sv == null) {
			return;
		}

		CommentRulerColumn existing = isLeft ? leftRulerColumn
				: rightRulerColumn;
		if (existing != null) {
			return;
		}

		CommentRulerColumn column = new CommentRulerColumn();
		String fileType = isLeft
				? "FROM" : "TO"; //$NON-NLS-1$ //$NON-NLS-2$

		// Set valid diff lines so the ruler only shows the
		// "+" icon on lines that are part of the diff
		if (diffLines != null) {
			Set<Integer> validLines = isLeft
					? diffLines.getLeftLines()
					: diffLines.getRightLines();
			column.setValidDiffLines(validLines);
		}

		column.setCommentClickHandler((line, lineComments) -> {
			handleCommentClick(sv, isLeft, line,
					lineComments);
		});

		column.setNewCommentClickHandler(line -> {
			handleNewComment(line, fileType);
		});

		sv.addVerticalRulerColumn(column);

		if (isLeft) {
			leftRulerColumn = column;
		} else {
			rightRulerColumn = column;
		}
	}

	// ---- Expand / Collapse -----------------------------------------------

	private void handleCommentClick(SourceViewer sv,
			boolean isLeft, int line,
			List<PullRequestComment> comments) {
		CommentRulerColumn column = isLeft ? leftRulerColumn
				: rightRulerColumn;

		if (column != null && column.getExpandedLine() == line) {
			collapseExpanded(sv, isLeft);
			return;
		}

		collapseExpanded(sv, isLeft);
		expandComment(sv, isLeft, line, comments);
	}

	private void expandComment(SourceViewer sv, boolean isLeft,
			int line,
			List<PullRequestComment> comments) {
		StyledText styledText = sv.getTextWidget();
		if (styledText == null || styledText.isDisposed()) {
			return;
		}

		int lineIndex = line;
		if (lineIndex < 0
				|| lineIndex >= styledText.getLineCount()) {
			return;
		}

		String fileType = isLeft
				? "FROM" : "TO"; //$NON-NLS-1$ //$NON-NLS-2$

		ExpandedCommentComposite.CommentActionHandler handler =
				new ExpandedCommentComposite
						.CommentActionHandler() {

					@Override
					public void onReply(
							PullRequestComment comment) {
						handleReply(comment, fileType);
					}

				@Override
				public void onResolve(
						PullRequestComment comment) {
					handleResolve(comment);
				}

				@Override
				public void onDelete(
						PullRequestComment comment) {
					handleDelete(comment);
				}

				@Override
				public void onCollapse(int collapseLine) {
						collapseExpanded(sv, isLeft);
					}

					@Override
					public void onSelect(
							PullRequestComment comment) {
						selectCommentInView(comment);
			}
		};

		ensureCurrentUsername();

		Activator.logInfo(String.format(
				"[CommentOverlayInstaller] Creating overlay for line %d with currentUsername='%s', provider=%s, %d comments", //$NON-NLS-1$
				line, currentUsername, providerType, comments.size()));
		if (!comments.isEmpty()) {
			Activator.logInfo(String.format(
					"[CommentOverlayInstaller] First comment author='%s'", //$NON-NLS-1$
					comments.get(0).getAuthorName()));
		}

		ExpandedCommentComposite composite =
				new ExpandedCommentComposite(styledText,
						SWT.NONE, line, comments, handler,
						currentUsername, providerType);

		Point preferredSize = composite.computeSize(
				styledText.getClientArea().width - 20,
				SWT.DEFAULT);
		int indentHeight = preferredSize.y + 8;

		styledText.setLineVerticalIndent(lineIndex,
				indentHeight);

		positionExpandedComposite(styledText, composite,
				lineIndex, indentHeight);

		styledText.addListener(SWT.Resize, e -> {
			if (!composite.isDisposed()
					&& !styledText.isDisposed()) {
				positionExpandedComposite(styledText,
						composite, lineIndex, indentHeight);
			}
		});

		CommentRulerColumn column = isLeft ? leftRulerColumn
				: rightRulerColumn;
		if (column != null) {
			column.setExpandedLine(line);
		}

		if (isLeft) {
			leftExpandedComposite = composite;
		} else {
			rightExpandedComposite = composite;
		}
	}

	private void positionExpandedComposite(StyledText styledText,
			ExpandedCommentComposite composite, int lineIndex,
			int indentHeight) {
		if (styledText.isDisposed() || composite.isDisposed()) {
			return;
		}

		try {
			int lineOffset = styledText
					.getOffsetAtLine(lineIndex);
			Point location = styledText
					.getLocationAtOffset(lineOffset);
			int verticalIndent = styledText
					.getLineVerticalIndent(lineIndex);

			int x = 10;
			int y = location.y - verticalIndent + 4;
			int width = styledText.getClientArea().width - 20;

			composite.setBounds(x, y, Math.max(width, 100),
					indentHeight - 8);
		} catch (IllegalArgumentException e) {
			composite.setVisible(false);
		}
	}

	private void collapseExpanded(SourceViewer sv,
			boolean isLeft) {
		ExpandedCommentComposite composite = isLeft
				? leftExpandedComposite
				: rightExpandedComposite;
		CommentRulerColumn column = isLeft ? leftRulerColumn
				: rightRulerColumn;

		if (composite != null && !composite.isDisposed()) {
			int line = composite.getLine();
			composite.dispose();

			if (sv != null) {
				StyledText styledText = sv.getTextWidget();
				if (styledText != null
						&& !styledText.isDisposed()) {
					int lineIndex = line;
					if (lineIndex >= 0 && lineIndex
							< styledText.getLineCount()) {
						styledText.setLineVerticalIndent(
								lineIndex, 0);
					}
				}
			}
		}

		if (column != null) {
			column.setExpandedLine(-1);
		}

		if (isLeft) {
			leftExpandedComposite = null;
		} else {
			rightExpandedComposite = null;
		}
	}

	// ---- Comment actions -------------------------------------------------

	private void handleReply(PullRequestComment comment,
			String fileType) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(
				viewer.getControl().getShell(),
				"Reply", //$NON-NLS-1$
				"Enter your reply:", //$NON-NLS-1$
				""); //$NON-NLS-1$
		if (dialog.open() != Window.OK) {
			return;
		}

		String replyText = dialog.getValue();
		if (replyText == null || replyText.trim().isEmpty()) {
			return;
		}

		PullRequest pr = getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		Job job = new Job("Posting reply") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client =
							PullRequestClientFactory
									.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					client.addComment(pr.getId(), replyText,
							comment.getId());

					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to post reply", //$NON-NLS-1$
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to post reply: " //$NON-NLS-1$
									+ e.getMessage(),
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private void handleResolve(PullRequestComment comment) {
		PullRequest pr = getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		boolean isResolved = "RESOLVED" //$NON-NLS-1$
				.equals(comment.getState());
		String action = isResolved
				? "Reopening" : "Resolving"; //$NON-NLS-1$ //$NON-NLS-2$

		Job job = new Job(action + " comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client =
							PullRequestClientFactory
									.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					String newState = isResolved
							? "OPEN" //$NON-NLS-1$
							: "RESOLVED"; //$NON-NLS-1$
					client.updateCommentState(pr.getId(),
							comment.getId(),
							comment.getVersion(),
							newState);

					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to update comment" //$NON-NLS-1$
									+ " state", //$NON-NLS-1$
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to update comment:" //$NON-NLS-1$
									+ " " //$NON-NLS-1$
									+ e.getMessage(),
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private void handleDelete(PullRequestComment comment) {
		boolean confirmed = MessageDialog.openConfirm(
				viewer.getControl().getShell(),
				"Delete Comment", //$NON-NLS-1$
				"Are you sure you want to delete this comment?"); //$NON-NLS-1$
		if (!confirmed) {
			return;
		}

		PullRequest pr = getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		Job job = new Job("Deleting comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client =
							PullRequestClientFactory
									.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					client.deleteComment(pr.getId(),
							comment.getId(),
							comment.getVersion(),
							comment.isReviewComment());

					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to delete comment", //$NON-NLS-1$
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to delete comment:" //$NON-NLS-1$
									+ " " //$NON-NLS-1$
									+ e.getMessage(),
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private void handleNewComment(int line, String fileType) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(
				viewer.getControl().getShell(),
				"Add Comment", //$NON-NLS-1$
				"Enter your comment:", //$NON-NLS-1$
				""); //$NON-NLS-1$
		if (dialog.open() != Window.OK) {
			return;
		}

		String commentText = dialog.getValue();
		if (commentText == null
				|| commentText.trim().isEmpty()) {
			return;
		}

		PullRequest pr = getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		if (currentFilePath == null) {
			Activator.logError(
					"Cannot create comment:" //$NON-NLS-1$
							+ " file path unknown", //$NON-NLS-1$
					null);
			return;
		}

		final String finalFilePath = currentFilePath;

		Job job = new Job("Posting comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client =
							PullRequestClientFactory
									.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					String commitId = pr.getFromRef()
							.getLatestCommit();
					String lineType = "ADDED"; //$NON-NLS-1$

					client.addInlineComment(pr.getId(),
							commentText, finalFilePath,
							line, lineType, fileType,
							commitId);

					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to post comment", //$NON-NLS-1$
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to post comment:" //$NON-NLS-1$
									+ " " //$NON-NLS-1$
									+ e.getMessage(),
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	// ---- Helper methods --------------------------------------------------

	private void selectCommentInView(PullRequestComment comment) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return;
			}

			IViewPart part = page.showView(
					PullRequestCommentsView.VIEW_ID);
			if (part instanceof PullRequestCommentsView) {
				((PullRequestCommentsView) part)
						.selectAndRevealComment(comment);
			}
		} catch (Exception e) {
			Activator.logError(
					"Failed to open comments view", e); //$NON-NLS-1$
		}
	}

	private PullRequest getSelectedPullRequest() {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return null;
			}
			IViewPart part = page.findView(
					PullRequestChangedFilesView.VIEW_ID);
			if (part instanceof PullRequestChangedFilesView) {
				return ((PullRequestChangedFilesView) part)
						.getSelectedPullRequest();
			}
		} catch (Exception e) {
			Activator.logError(
					"Failed to get selected pull" //$NON-NLS-1$
							+ " request", //$NON-NLS-1$
					e);
		}
		return null;
	}

	// ---- Refresh ---------------------------------------------------------

	private void refreshAfterReply(PullRequest pr,
			IPullRequestClient client) {
		try {
			List<PullRequestComment> freshComments =
					client.getPullRequestComments(pr.getId());

			Display.getDefault().asyncExec(() -> {
				if (viewer.getControl() != null
						&& !viewer.getControl().isDisposed()) {
					List<PullRequestComment> fileComments =
							filterCommentsForCurrentFile(
									freshComments);
					applyComments(fileComments);
				}

				refreshCommentsView(freshComments);
			});
		} catch (IOException e) {
			Activator.logError(
					"Failed to refresh comments", e); //$NON-NLS-1$
		}
	}

	private List<PullRequestComment>
			filterCommentsForCurrentFile(
					List<PullRequestComment> allComments) {
		if (currentComments == null
				|| currentComments.isEmpty()) {
			return allComments;
		}

		java.util.Set<String> paths = new java.util.HashSet<>();
		for (PullRequestComment c : currentComments) {
			if (c.getPath() != null) {
				paths.add(c.getPath());
			}
		}

		if (paths.isEmpty()) {
			return allComments;
		}

		List<PullRequestComment> filtered = new ArrayList<>();
		for (PullRequestComment c : allComments) {
			if (c.getPath() != null
					&& paths.contains(c.getPath())) {
				filtered.add(c);
			}
		}
		return filtered;
	}

	private void refreshCommentsView(
			List<PullRequestComment> freshComments) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return;
			}
			IViewPart part = page.findView(
					PullRequestCommentsView.VIEW_ID);
			if (part instanceof PullRequestCommentsView) {
				((PullRequestCommentsView) part)
						.updateComments(freshComments);
			}
		} catch (Exception e) {
			Activator.logError(
					"Failed to refresh comments view", //$NON-NLS-1$
					e);
		}
	}

	private void ensureCurrentUsername() {
		if (currentUsername == null || providerType == null) {
			try {
				IPullRequestClient client =
						PullRequestClientFactory.createClient();
				if (client != null) {
					currentUsername = client.getCurrentUser();
					providerType = client.getProviderType();
					Activator.logInfo(
							"Fetched current username: " //$NON-NLS-1$
									+ currentUsername
									+ ", provider: " //$NON-NLS-1$
									+ providerType);
				}
			} catch (IOException e) {
				Activator.logError(
						"Failed to fetch current user", e); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Disposes all resources managed by this installer. Should be
	 * called when the compare editor is closed.
	 */
	void dispose() {
		collapseExpanded(leftSourceViewer, true);
		collapseExpanded(rightSourceViewer, false);
		leftRulerColumn = null;
		rightRulerColumn = null;
		leftSourceViewer = null;
		rightSourceViewer = null;
		currentComments = null;
	}
}
