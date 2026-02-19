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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.contentmergeviewer.TextMergeViewer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * Custom {@link TextMergeViewer} subclass that supports displaying inline pull
 * request comments using expandable markers in the vertical ruler.
 *
 * <p>
 * This viewer uses {@link CommentRulerColumn} to show lightweight speech-bubble
 * icons in the gutter for lines with comments, and a "+" icon on hover for
 * adding new comments. Clicking a comment icon expands the full thread inline
 * using an {@link ExpandedCommentComposite} positioned over the
 * {@link StyledText} widget via
 * {@link StyledText#setLineVerticalIndent(int, int)}.
 * </p>
 *
 * <p>
 * Only one comment thread can be expanded at a time per viewer side. Expanding
 * a different comment automatically collapses the previous one.
 * </p>
 *
 * <p>
 * Each expanded comment shows the full thread with Reply, Resolve, and
 * Collapse actions. Clicking on the comment body opens the
 * {@link PullRequestCommentsView} and selects that comment.
 * </p>
 */
public class InlineCommentTextMergeViewer extends TextMergeViewer {

	private SourceViewer leftSourceViewer;

	private SourceViewer rightSourceViewer;

	private CommentRulerColumn leftRulerColumn;

	private CommentRulerColumn rightRulerColumn;

	private ExpandedCommentComposite leftExpandedComposite;

	private ExpandedCommentComposite rightExpandedComposite;

	/**
	 * Counter for configureTextViewer calls. The call order is always ancestor
	 * (0), left (1), right (2) -- even in 2-way mode where the ancestor pane
	 * is created but hidden.
	 */
	private int configureCount;

	/**
	 * Pending comments to be applied once documents are available.
	 */
	private List<PullRequestComment> pendingComments;

	/**
	 * The most recently applied comments list, kept for refreshing after a
	 * reply is posted.
	 */
	private List<PullRequestComment> currentComments;

	/**
	 * The file path of the currently displayed file. This is needed for
	 * creating new comments when no existing comments are present.
	 */
	private String currentFilePath;

	/**
	 * Constructor.
	 *
	 * @param parent
	 *            the parent composite
	 * @param configuration
	 *            the compare configuration
	 */
	public InlineCommentTextMergeViewer(Composite parent,
			CompareConfiguration configuration) {
		super(parent, configuration);
	}

	@Override
	protected void configureTextViewer(
			org.eclipse.jface.text.TextViewer textViewer) {
		super.configureTextViewer(textViewer);

		// Track left/right SourceViewer references.
		// configureTextViewer is called in order: ancestor (0), left (1),
		// right (2) -- even in 2-way mode (ancestor is created but hidden).
		if (textViewer instanceof SourceViewer) {
			SourceViewer sv = (SourceViewer) textViewer;
			int index = configureCount++;
			if (index == 1) {
				leftSourceViewer = sv;
			} else if (index == 2) {
				rightSourceViewer = sv;
			}
		}
	}

	@Override
	protected void updateContent(Object ancestor, Object left, Object right) {
		super.updateContent(ancestor, left, right);

		if (pendingComments != null) {
			final List<PullRequestComment> commentsToApply = pendingComments;
			pendingComments = null;

			getControl().getDisplay().asyncExec(() -> {
				if (!getControl().isDisposed()) {
					applyCommentsDeferred(commentsToApply);
				}
			});
		}
	}

	/**
	 * Sets the pull request comments to display as inline annotations.
	 *
	 * <p>
	 * Only inline comments (those with a non-null {@code line} and
	 * {@code path}) are rendered. Comments are placed on the left side
	 * ({@code fileType == "FROM"}) or right side ({@code fileType == "TO"}).
	 * </p>
	 *
	 * @param comments
	 *            the list of comments for the current file
	 */
	public void setComments(List<PullRequestComment> comments) {
		// Extract file path from comments for use in creating new comments
		if (comments != null && !comments.isEmpty()) {
			for (PullRequestComment c : comments) {
				if (c.getPath() != null) {
					currentFilePath = c.getPath();
					break;
				}
			}
		}

		// If documents are not yet available, store comments as pending
		boolean documentsReady = (leftSourceViewer != null
				&& leftSourceViewer.getDocument() != null)
				|| (rightSourceViewer != null
						&& rightSourceViewer.getDocument() != null);

		if (!documentsReady) {
			pendingComments = comments;
			return;
		}

		applyComments(comments);
	}

	/**
	 * Sets the file path for the currently displayed file. This is used when
	 * creating new comments, especially when there are no existing comments to
	 * extract the path from.
	 *
	 * @param filePath
	 *            the file path
	 */
	public void setFilePath(String filePath) {
		this.currentFilePath = filePath;
	}

	/**
	 * Applies comments with a deferred retry mechanism. If documents are not
	 * ready yet, this method will retry after a short delay.
	 *
	 * @param comments
	 *            the list of comments to apply
	 */
	private void applyCommentsDeferred(List<PullRequestComment> comments) {
		applyCommentsDeferred(comments, 0);
	}

	/**
	 * Applies comments with a deferred retry mechanism.
	 *
	 * @param comments
	 *            the list of comments to apply
	 * @param retryCount
	 *            the number of retries attempted so far
	 */
	private void applyCommentsDeferred(List<PullRequestComment> comments,
			int retryCount) {
		boolean documentsReady = (leftSourceViewer != null
				&& leftSourceViewer.getDocument() != null
				&& leftSourceViewer.getDocument().getNumberOfLines() > 1)
				|| (rightSourceViewer != null
						&& rightSourceViewer.getDocument() != null
						&& rightSourceViewer.getDocument()
								.getNumberOfLines() > 1);

		if (!documentsReady && retryCount < 5) {
			final int nextRetry = retryCount + 1;
			getControl().getDisplay().timerExec(50, () -> {
				if (!getControl().isDisposed()) {
					applyCommentsDeferred(comments, nextRetry);
				}
			});
			return;
		}

		applyComments(comments);
	}

	/**
	 * Actually applies comments to the viewers. Installs
	 * {@link CommentRulerColumn} instances on both sides and sets the
	 * comment data on each.
	 *
	 * @param comments
	 *            the list of comments to apply
	 */
	private void applyComments(List<PullRequestComment> comments) {
		// Collapse any expanded comment
		collapseExpanded(leftSourceViewer, true);
		collapseExpanded(rightSourceViewer, false);

		if (comments == null || comments.isEmpty()) {
			// Clear ruler columns
			if (leftRulerColumn != null) {
				leftRulerColumn.setComments(null);
			}
			if (rightRulerColumn != null) {
				rightRulerColumn.setComments(null);
			}
			return;
		}

		// Keep a reference for refresh after reply
		currentComments = new ArrayList<>(comments);

		// Separate comments by side (LEFT = "FROM", RIGHT = "TO")
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

			String fileType = comment.getFileType();
			if ("FROM".equals(fileType)) { //$NON-NLS-1$
				leftComments.add(comment);
			} else {
				rightComments.add(comment);
			}
		}

		// Install ruler columns if not already present
		installRulerColumn(leftSourceViewer, true);
		installRulerColumn(rightSourceViewer, false);

		// Set comments on ruler columns
		if (leftRulerColumn != null) {
			leftRulerColumn.setComments(leftComments);
		}
		if (rightRulerColumn != null) {
			rightRulerColumn.setComments(rightComments);
		}
	}

	/**
	 * Installs a {@link CommentRulerColumn} on the given source viewer if
	 * one has not already been installed.
	 *
	 * @param viewer
	 *            the source viewer
	 * @param isLeft
	 *            {@code true} for the left side, {@code false} for right
	 */
	private void installRulerColumn(SourceViewer viewer, boolean isLeft) {
		if (viewer == null) {
			return;
		}

		CommentRulerColumn existing = isLeft ? leftRulerColumn
				: rightRulerColumn;
		if (existing != null) {
			return;
		}

		CommentRulerColumn column = new CommentRulerColumn();
		String fileType = isLeft ? "FROM" : "TO"; //$NON-NLS-1$ //$NON-NLS-2$

		column.setCommentClickHandler((line, lineComments) -> {
			handleCommentClick(viewer, isLeft, line, lineComments);
		});

		column.setNewCommentClickHandler(line -> {
			handleNewComment(line, fileType);
		});

		viewer.addVerticalRulerColumn(column);

		if (isLeft) {
			leftRulerColumn = column;
		} else {
			rightRulerColumn = column;
		}
	}

	// ---- Expand / Collapse ------------------------------------------------

	/**
	 * Handles a click on a comment indicator in the ruler column. Expands
	 * the comment thread inline, or collapses it if already expanded.
	 *
	 * @param viewer
	 *            the source viewer
	 * @param isLeft
	 *            {@code true} for the left side
	 * @param line
	 *            the 1-based line number
	 * @param comments
	 *            the comments on that line
	 */
	private void handleCommentClick(SourceViewer viewer, boolean isLeft,
			int line, List<PullRequestComment> comments) {
		CommentRulerColumn column = isLeft ? leftRulerColumn
				: rightRulerColumn;

		// Toggle: if already expanded on this line, collapse
		if (column != null && column.getExpandedLine() == line) {
			collapseExpanded(viewer, isLeft);
			return;
		}

		// Collapse any previously expanded comment on this side
		collapseExpanded(viewer, isLeft);

		// Expand the new comment
		expandComment(viewer, isLeft, line, comments);
	}

	/**
	 * Expands a comment thread inline by creating an
	 * {@link ExpandedCommentComposite} and reserving space via
	 * {@link StyledText#setLineVerticalIndent(int, int)}.
	 *
	 * @param viewer
	 *            the source viewer
	 * @param isLeft
	 *            {@code true} for the left side
	 * @param line
	 *            the 1-based line number
	 * @param comments
	 *            the comments on that line
	 */
	private void expandComment(SourceViewer viewer, boolean isLeft,
			int line, List<PullRequestComment> comments) {
		StyledText styledText = viewer.getTextWidget();
		if (styledText == null || styledText.isDisposed()) {
			return;
		}

		// The comment line (1-based). The indent is set on the line
		// itself so the composite appears above that line's text.
		int lineIndex = line;
		if (lineIndex < 0 || lineIndex >= styledText.getLineCount()) {
			return;
		}

		String fileType = isLeft ? "FROM" : "TO"; //$NON-NLS-1$ //$NON-NLS-2$

		ExpandedCommentComposite.CommentActionHandler actionHandler =
				new ExpandedCommentComposite.CommentActionHandler() {

			@Override
			public void onReply(PullRequestComment comment) {
				handleReply(comment, fileType);
			}

			@Override
			public void onResolve(PullRequestComment comment) {
				handleResolve(comment);
			}

			@Override
			public void onCollapse(int collapseLine) {
				collapseExpanded(viewer, isLeft);
			}

			@Override
			public void onSelect(PullRequestComment comment) {
				selectCommentInView(comment);
			}
		};

		// Create the expanded composite as a direct child of the
		// StyledText. Since StyledText extends Canvas (which extends
		// Composite), it supports child controls, and its internal
		// scrollVertical() method automatically relocates all children
		// by the scroll delta. This means the composite scrolls with
		// the text content without any manual scroll tracking.
		ExpandedCommentComposite composite =
				new ExpandedCommentComposite(styledText, SWT.NONE,
						line, comments, actionHandler);

		// Compute preferred size to determine how much vertical indent
		// we need
		Point preferredSize = composite.computeSize(
				styledText.getClientArea().width - 20, SWT.DEFAULT);
		int indentHeight = preferredSize.y + 8;

		// Reserve space in the StyledText
		styledText.setLineVerticalIndent(lineIndex, indentHeight);

		// Position the composite in the reserved indent space
		positionExpandedComposite(styledText, composite, lineIndex,
				indentHeight);

		// Reposition on resize so width adapts to editor size changes
		styledText.addListener(SWT.Resize, e -> {
			if (!composite.isDisposed()
					&& !styledText.isDisposed()) {
				positionExpandedComposite(styledText, composite,
						lineIndex, indentHeight);
			}
		});

		// Update the ruler column to show expanded state
		CommentRulerColumn column = isLeft ? leftRulerColumn
				: rightRulerColumn;
		if (column != null) {
			column.setExpandedLine(line);
		}

		// Store reference
		if (isLeft) {
			leftExpandedComposite = composite;
		} else {
			rightExpandedComposite = composite;
		}
	}

	/**
	 * Positions the {@link ExpandedCommentComposite} in the reserved
	 * vertical indent area of the {@link StyledText}. Because the
	 * composite is a direct child of the {@code StyledText}, coordinates
	 * are in the {@code StyledText}'s local coordinate space and the
	 * composite scrolls automatically with the text content.
	 *
	 * @param styledText
	 *            the styled text widget
	 * @param composite
	 *            the expanded comment composite
	 * @param lineIndex
	 *            the 0-based line index (same as 1-based line for this
	 *            scheme)
	 * @param indentHeight
	 *            the reserved indent height
	 */
	private void positionExpandedComposite(StyledText styledText,
			ExpandedCommentComposite composite, int lineIndex,
			int indentHeight) {
		if (styledText.isDisposed() || composite.isDisposed()) {
			return;
		}

		try {
			int lineOffset = styledText.getOffsetAtLine(lineIndex);
			Point location = styledText.getLocationAtOffset(lineOffset);
			int verticalIndent = styledText
					.getLineVerticalIndent(lineIndex);

			// Position in StyledText-local coordinates — the indent
			// area is directly above the line's text baseline.
			int x = 10;
			int y = location.y - verticalIndent + 4;
			int width = styledText.getClientArea().width - 20;

			composite.setBounds(x, y, Math.max(width, 100),
					indentHeight - 8);
		} catch (IllegalArgumentException e) {
			// Line no longer valid
			composite.setVisible(false);
		}
	}

	/**
	 * Collapses the currently expanded comment on the given side.
	 *
	 * @param viewer
	 *            the source viewer
	 * @param isLeft
	 *            {@code true} for the left side
	 */
	private void collapseExpanded(SourceViewer viewer, boolean isLeft) {
		ExpandedCommentComposite composite = isLeft
				? leftExpandedComposite : rightExpandedComposite;
		CommentRulerColumn column = isLeft ? leftRulerColumn
				: rightRulerColumn;

		if (composite != null && !composite.isDisposed()) {
			int line = composite.getLine();
			composite.dispose();

			// Reset vertical indent
			if (viewer != null) {
				StyledText styledText = viewer.getTextWidget();
				if (styledText != null && !styledText.isDisposed()) {
					int lineIndex = line;
					if (lineIndex >= 0
							&& lineIndex < styledText.getLineCount()) {
						styledText.setLineVerticalIndent(lineIndex, 0);
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

	// ---- Comment actions --------------------------------------------------

	/**
	 * Handles a reply action from the expanded comment composite.
	 *
	 * @param comment
	 *            the root comment being replied to
	 * @param fileType
	 *            "FROM" for left side, "TO" for right side
	 */
	private void handleReply(PullRequestComment comment, String fileType) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(
				getControl().getShell(),
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
							PullRequestClientFactory.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider not configured"); //$NON-NLS-1$
					}

					client.addComment(pr.getId(), replyText,
							comment.getId());

					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to post reply", e); //$NON-NLS-1$
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

	/**
	 * Handles a resolve action from the expanded comment composite.
	 *
	 * @param comment
	 *            the root comment to resolve/reopen
	 */
	private void handleResolve(PullRequestComment comment) {
		PullRequest pr = getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		boolean isResolved = "RESOLVED".equals(comment.getState()); //$NON-NLS-1$
		String action = isResolved ? "Reopening" : "Resolving"; //$NON-NLS-1$ //$NON-NLS-2$

		Job job = new Job(action + " comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client =
							PullRequestClientFactory.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider not configured"); //$NON-NLS-1$
					}

					String newState = isResolved
								? "OPEN" : "RESOLVED"; //$NON-NLS-1$ //$NON-NLS-2$
					client.updateCommentState(pr.getId(),
							comment.getId(),
							comment.getVersion(),
							newState);

					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to update comment state", //$NON-NLS-1$
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to update comment: " //$NON-NLS-1$
									+ e.getMessage(),
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/**
	 * Selects a comment in the {@link PullRequestCommentsView}.
	 *
	 * @param comment
	 *            the comment to select
	 */
	private void selectCommentInView(PullRequestComment comment) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return;
			}

			IViewPart part = page
					.showView(PullRequestCommentsView.VIEW_ID);
			if (part instanceof PullRequestCommentsView) {
				((PullRequestCommentsView) part)
						.selectAndRevealComment(comment);
			}
		} catch (Exception e) {
			Activator.logError(
					"Failed to open comments view", e); //$NON-NLS-1$
		}
	}

	/**
	 * Handles creating a new inline comment on a specific line.
	 * Opens a {@link MultiLineInputDialog}, posts the comment via the
	 * configured pull request client, then refreshes both the inline
	 * rulers and the {@link PullRequestCommentsView}.
	 *
	 * @param line
	 *            the 1-based line number
	 * @param fileType
	 *            "FROM" for left side, "TO" for right side
	 */
	private void handleNewComment(int line, String fileType) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(
				getControl().getShell(),
				"Add Comment", //$NON-NLS-1$
				"Enter your comment:", //$NON-NLS-1$
				""); //$NON-NLS-1$
		if (dialog.open() != Window.OK) {
			return;
		}

		String commentText = dialog.getValue();
		if (commentText == null || commentText.trim().isEmpty()) {
			return;
		}

		PullRequest pr = getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		// Use the stored file path
		if (currentFilePath == null) {
			Activator.logError("Cannot create comment: file path unknown", //$NON-NLS-1$
					null);
			return;
		}

		final String finalFilePath = currentFilePath;

		Job job = new Job("Posting comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client =
							PullRequestClientFactory.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider not configured"); //$NON-NLS-1$
					}

					// Get commit SHA from pull request
					String commitId = pr.getFromRef().getId();

					// Determine line type (simplified - always use
					// ADDED for now)
					// TODO: Use RangeDifferencer to determine actual
					// line type
					String lineType = "ADDED"; //$NON-NLS-1$

					client.addInlineComment(pr.getId(), commentText,
							finalFilePath, line, lineType, fileType,
							commitId);

					// Refresh both the inline comments and the
					// comments view tree
					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to post comment", e); //$NON-NLS-1$
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to post comment: " //$NON-NLS-1$
									+ e.getMessage(),
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	// ---- Refresh ----------------------------------------------------------

	/**
	 * Refreshes both the inline comment rulers and the
	 * {@link PullRequestCommentsView} after a reply has been posted.
	 *
	 * @param pr
	 *            the current pull request
	 * @param client
	 *            the pull request client
	 */
	private void refreshAfterReply(PullRequest pr,
			IPullRequestClient client) {
		try {
			List<PullRequestComment> freshComments =
					client.getPullRequestComments(pr.getId());

			Display.getDefault().asyncExec(() -> {
				if (getControl() != null && !getControl().isDisposed()) {
					// Refresh rulers with fresh comments filtered to
					// the same file
					List<PullRequestComment> fileComments =
							filterCommentsForCurrentFile(freshComments);
					applyComments(fileComments);
				}

				// Refresh the PullRequestCommentsView tree
				refreshCommentsView(freshComments);
			});
		} catch (IOException e) {
			Activator.logError("Failed to refresh comments", e); //$NON-NLS-1$
		}
	}

	/**
	 * Filters the given comments to only those that match the file paths of
	 * the currently displayed comments.
	 *
	 * @param allComments
	 *            all comments from the server
	 * @return comments matching the current file
	 */
	private List<PullRequestComment> filterCommentsForCurrentFile(
			List<PullRequestComment> allComments) {
		if (currentComments == null || currentComments.isEmpty()) {
			return allComments;
		}

		// Collect file paths from the current comments
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
			if (c.getPath() != null && paths.contains(c.getPath())) {
				filtered.add(c);
			}
		}
		return filtered;
	}

	/**
	 * Refreshes the {@link PullRequestCommentsView} with fresh comments
	 * from the server.
	 *
	 * @param freshComments
	 *            the fresh comments from the server
	 */
	private void refreshCommentsView(
			List<PullRequestComment> freshComments) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return;
			}
			IViewPart part = page
					.findView(PullRequestCommentsView.VIEW_ID);
			if (part instanceof PullRequestCommentsView) {
				((PullRequestCommentsView) part)
						.updateComments(freshComments);
			}
		} catch (Exception e) {
			Activator.logError(
					"Failed to refresh comments view", e); //$NON-NLS-1$
		}
	}

	/**
	 * Retrieves the currently selected pull request from the
	 * {@link PullRequestChangedFilesView}.
	 *
	 * @return the selected pull request, or {@code null}
	 */
	private PullRequest getSelectedPullRequest() {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return null;
			}
			IViewPart part = page
					.findView(PullRequestChangedFilesView.VIEW_ID);
			if (part instanceof PullRequestChangedFilesView) {
				return ((PullRequestChangedFilesView) part)
						.getSelectedPullRequest();
			}
		} catch (Exception e) {
			Activator.logError(
					"Failed to get selected pull request", e); //$NON-NLS-1$
		}
		return null;
	}

	// ---- Lifecycle ---------------------------------------------------------

	@Override
	protected void handleDispose(org.eclipse.swt.events.DisposeEvent event) {
		collapseExpanded(leftSourceViewer, true);
		collapseExpanded(rightSourceViewer, false);

		leftRulerColumn = null;
		rightRulerColumn = null;
		leftSourceViewer = null;
		rightSourceViewer = null;
		currentComments = null;
		super.handleDispose(event);
	}
}
