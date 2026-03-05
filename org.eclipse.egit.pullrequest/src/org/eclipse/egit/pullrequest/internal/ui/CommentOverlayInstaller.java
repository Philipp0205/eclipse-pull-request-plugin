package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * Installs pull request comment overlays (ruler columns and inline
 * painted comments) on any {@link TextMergeViewer} instance. This
 * allows the Eclipse compare framework to select the appropriate
 * language-specific viewer (e.g. Java Source Compare, XML Compare)
 * while still providing inline comment support.e Comm
 *
 * <p>
 * The installer uses reflection to access the left and right
 * {@link SourceViewer} instances from the {@link TextMergeViewer},
 * then installs {@link CommentRulerColumn} instances on each side.
 * Inline comments are painted directly into the
 * {@code StyledText} vertical-indent space by a
 * {@link CommentPaintRenderer}, avoiding the need for manual
 * composite positioning and overlap detection.
 * </p>
 */
public class CommentOverlayInstaller {

	private SourceViewer leftSourceViewer;
	private SourceViewer rightSourceViewer;
	private CommentRulerColumn leftRulerColumn;
	private CommentRulerColumn rightRulerColumn;

	private CommentPaintRenderer leftRenderer;
	private CommentPaintRenderer rightRenderer;
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
	public CommentOverlayInstaller(Viewer viewer) {
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

		applyComments(comments);
	}

	/**
	 * Extracts the left and right {@link SourceViewer} from a
	 * {@link TextMergeViewer} using reflection. The field names are {@code fLeft}
	 * and {@code fRight} in {@link TextMergeViewer}, each of which is a
	 * {@code MergeSourceViewer} wrapping a {@link SourceViewer}.
	 *
	 * TODO: Consider consider filing an enhancement request with the Eclipse
	 * Platform project to expose a public API in future releases, but for now,
	 * reflection is justified and necessary.
	 *
	 * @param tmv the text merge viewer
	 */
	private void extractSourceViewers(TextMergeViewer tmv) {
		if (leftSourceViewer != null && rightSourceViewer != null) {
			return;
		}

		try {
			// TextMergeViewer has fLeft and fRight fields of type
			// MergeSourceViewer which wraps a SourceViewer
			leftSourceViewer = extractSourceViewer(tmv, "fLeft"); //$NON-NLS-1$
			rightSourceViewer = extractSourceViewer(tmv, "fRight"); //$NON-NLS-1$
		} catch (Exception e) {
			Activator.logError("Failed to extract source viewers " //$NON-NLS-1$
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
	private SourceViewer extractSourceViewer(TextMergeViewer tmv, String fieldName) throws Exception {
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
			Method getSourceViewer = mergeSourceViewer.getClass().getMethod("getSourceViewer"); //$NON-NLS-1$
			Object sv = getSourceViewer.invoke(mergeSourceViewer);
			if (sv instanceof SourceViewer) {
				return (SourceViewer) sv;
			}
		} catch (NoSuchMethodException e) {
			// Try field access instead
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
	 * Applies comments to the left and right source viewers by
	 * installing {@link CommentRulerColumn} instances.
	 *
	 * @param comments
	 *            the list of comments for the current file
	 */
	private void applyComments(List<PullRequestComment> comments) {
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
			if (comment.getLine() == null || comment.getLine().intValue() < 1) {
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

		// Automatically show all comments by default
		// The StyledText widget may not have its content loaded yet at this point
		// (compare framework loads content asynchronously). Defer showing until
		// the widget actually has enough lines for the comments.
		scheduleShowAllCommentsWhenReady(leftSourceViewer, leftComments, true, 0);
		scheduleShowAllCommentsWhenReady(rightSourceViewer, rightComments, false, 0);
	}

	/**
	 * Schedules showing of all comments once the {@link StyledText}
	 * widget has its content loaded. The compare framework populates
	 * the text asynchronously, so at the time {@code applyComments}
	 * runs the widget may still be empty (line count of 1). This
	 * method retries with a short delay until either the content
	 * appears or the maximum number of retries is reached.
	 *
	 * @param sv
	 *            the source viewer
	 * @param comments
	 *            the comments to show
	 * @param isLeft
	 *            {@code true} for left side, {@code false} for right
	 * @param retryCount
	 *            the current retry attempt (starts at 0)
	 */
	private void scheduleShowAllCommentsWhenReady(SourceViewer sv,
			List<PullRequestComment> comments, boolean isLeft,
			int retryCount) {
		if (sv == null || comments == null || comments.isEmpty()) {
			return;
		}

		StyledText styledText = sv.getTextWidget();
		if (styledText == null || styledText.isDisposed()) {
			return;
		}

		// Determine the maximum line number referenced by the
		// comments so we know how many lines we need.
		int maxLine = 0;
		for (PullRequestComment comment : comments) {
			if (comment.getLine() != null
					&& comment.getLine().intValue() > maxLine) {
				maxLine = comment.getLine().intValue();
			}
		}

		// StyledText.getLineCount() returns the number of lines;
		// we need at least maxLine lines to be present.
		if (styledText.getLineCount() > maxLine) {
			showAllComments(sv, comments, isLeft);
			return;
		}

		if (retryCount >= 20) {
			Activator.logWarning(
					"[CommentOverlayInstaller] Gave up waiting" //$NON-NLS-1$
							+ " for StyledText content to" //$NON-NLS-1$
							+ " show all comments (side=" //$NON-NLS-1$
							+ (isLeft
									? "LEFT" //$NON-NLS-1$
									: "RIGHT") //$NON-NLS-1$
							+ ", needed " + maxLine //$NON-NLS-1$
							+ " lines, have " //$NON-NLS-1$
							+ styledText.getLineCount()
							+ ")"); //$NON-NLS-1$
			return;
		}

		Activator.logInfo(String.format(
				"[CommentOverlayInstaller] StyledText not" //$NON-NLS-1$
						+ " ready (lineCount=%d, need>%d)," //$NON-NLS-1$
						+ " retrying show-all" //$NON-NLS-1$
						+ " (attempt %d)", //$NON-NLS-1$
				styledText.getLineCount(), maxLine,
				retryCount + 1));

		styledText.getDisplay().timerExec(100, () -> {
			if (!styledText.isDisposed()) {
				scheduleShowAllCommentsWhenReady(sv, comments,
						isLeft, retryCount + 1);
			}
		});
	}

	/**
	 * Shows all comment threads on the given side. This is called
	 * automatically when comments are loaded.
	 *
	 * @param sv
	 *            the source viewer
	 * @param comments
	 *            the comments to show
	 * @param isLeft
	 *            {@code true} for left side, {@code false} for right side
	 */
	private void showAllComments(SourceViewer sv,
			List<PullRequestComment> comments, boolean isLeft) {
		if (sv == null || comments == null || comments.isEmpty()) {
			return;
		}

		StyledText styledText = sv.getTextWidget();
		if (styledText == null || styledText.isDisposed()) {
			return;
		}

		Activator.logInfo(String.format(
				"[CommentOverlayInstaller] showAllComments: side=%s, commentCount=%d", //$NON-NLS-1$
				isLeft ? "LEFT" : "RIGHT", comments.size())); //$NON-NLS-1$ //$NON-NLS-2$

		// Group comments by line number — only root comments
		// (replies are included via getReplies())
		Map<Integer, List<PullRequestComment>> commentsByLine = new HashMap<>();
		for (PullRequestComment comment : comments) {
			if (comment.getLine() != null && comment.getLine().intValue() >= 1) {
				int line = comment.getLine().intValue();
				if (comment.getInReplyToId() == -1) {
					commentsByLine.computeIfAbsent(line,
							k -> new ArrayList<>())
							.add(comment);
				}
			}
		}

		Activator.logInfo(String.format(
				"[CommentOverlayInstaller] Showing %d comment threads", //$NON-NLS-1$
				commentsByLine.size()));

		// Get or create the renderer for this side
		ensureCurrentUsername();
		CommentPaintRenderer renderer = ensureRenderer(sv, isLeft);

		// Register each thread with the renderer
		String fileType = isLeft
				? "FROM" : "TO"; //$NON-NLS-1$ //$NON-NLS-2$
		renderer.setActionHandler(createActionHandler(fileType));
		renderer.setCurrentUsername(currentUsername);
		renderer.setProviderType(providerType);
		renderer.clearThreads();

		List<Integer> sortedLines = new ArrayList<>(commentsByLine.keySet());
		Collections.sort(sortedLines);

		for (Integer lineNum : sortedLines) {
			List<PullRequestComment> lineComments =commentsByLine.get(lineNum);
			int lineIndex = lineNum.intValue();
			if (lineIndex < 0
					|| lineIndex >= styledText.getLineCount()) {
				continue;
			}

			renderer.addThread(lineIndex, lineComments);

			CommentRulerColumn column = isLeft ? leftRulerColumn : rightRulerColumn;
			if (column != null) {
				column.addLineWithComments(lineNum.intValue());
			}

			Activator.logInfo(String.format(
					"[CommentOverlayInstaller] Registered" //$NON-NLS-1$
							+ " thread at line %d with" //$NON-NLS-1$
							+ " %d root comments", //$NON-NLS-1$
					lineNum, lineComments.size()));
		}

		// Compute heights and set vertical indents
		for (Integer lineNum : sortedLines) {
			int lineIndex = lineNum.intValue();
			if (lineIndex >= 0
					&& lineIndex < styledText.getLineCount()) {
				int height = renderer.computeThreadHeight(
						styledText, lineIndex);
				styledText.setLineVerticalIndent(lineIndex,
						height);
			}
		}

		// Trigger repaint — the PaintListener handles rendering
		styledText.redraw();
	}

	/**
	 * Scrolls to a comment by line number and file type. This
	 * method is called when a comment is selected in the Comments View to
	 * navigate to the inline comment in the compare editor.
	 *
	 * @param line
	 *            the line number (1-based) to scroll to
	 * @param fileType
	 *            the file type: "FROM" for left side, "TO" for right side
	 */
	public void scrollToComment(int line, String fileType) {
		boolean isLeft = "FROM".equals(fileType); //$NON-NLS-1$
		SourceViewer sv = isLeft ? leftSourceViewer : rightSourceViewer;

		if (sv == null || sv.getTextWidget() == null
				|| sv.getTextWidget().isDisposed()) {
			return;
		}

		// Find comments for this line and file type
		List<PullRequestComment> lineComments = new ArrayList<>();
		if (currentComments != null) {
			for (PullRequestComment comment : currentComments) {
				if (comment.getLine() != null
						&& comment.getLine().intValue() == line
						&& fileType.equals(comment.getFileType())) {
					lineComments.add(comment);
				}
			}
		}

		if (lineComments.isEmpty()) {
			return;
		}

		// Comments are always visible, so just scroll to the line
		StyledText styledText = sv.getTextWidget();
		int lineIndex = line - 1; // Convert to 0-based

		Display.getDefault().timerExec(50, () -> {
			if (styledText != null && !styledText.isDisposed()) {
				try {
					int offset = styledText.getOffsetAtLine(lineIndex);
					styledText.setSelection(offset);
					styledText.showSelection();
					styledText.setTopIndex(Math.max(0, lineIndex - 5)); // Show context
				} catch (IllegalArgumentException e) {
					// Line doesn't exist, ignore
				}
			}
		});
	}

	/**
	 * Highlights a specific comment in the inline view. This is called
	 * when navigating from the Comments View to provide visual feedback.
	 *
	 * @param comment
	 *            the comment to highlight
	 * @param fileType
	 *            the file type: "FROM" for left side, "TO" for right side
	 */
	public void highlightComment(PullRequestComment comment, String fileType) {
		if (comment == null) {
			return;
		}

		boolean isLeft = "FROM".equals(fileType); //$NON-NLS-1$
		CommentPaintRenderer renderer = isLeft ? leftRenderer : rightRenderer;
		SourceViewer sv = isLeft ? leftSourceViewer : rightSourceViewer;

		if (renderer == null || sv == null || sv.getTextWidget() == null
				|| sv.getTextWidget().isDisposed()) {
			return;
		}

		// Set the highlighted comment and trigger redraw
		renderer.setHighlightedComment(comment);
		sv.getTextWidget().redraw();

		// Auto-clear highlight after 3 seconds
		Display.getDefault().timerExec(3000, () -> {
			if (renderer != null && sv != null
					&& sv.getTextWidget() != null
					&& !sv.getTextWidget().isDisposed()) {
				renderer.clearHighlight();
				sv.getTextWidget().redraw();
			}
		});
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

	// ---- Comment Display -----------------------------------------------

	private void handleCommentClick(SourceViewer sv,
			boolean isLeft, int line,
			List<PullRequestComment> comments) {
		// Comments are always visible, so just scroll to the line
		if (sv == null || sv.getTextWidget() == null
				|| sv.getTextWidget().isDisposed()) {
			return;
		}

		StyledText styledText = sv.getTextWidget();
		int lineIndex = line - 1; // Convert to 0-based

		try {
			int offset = styledText.getOffsetAtLine(lineIndex);
			styledText.setSelection(offset);
			styledText.showSelection();
			styledText.setTopIndex(Math.max(0, lineIndex - 5)); // Show context
		} catch (IllegalArgumentException e) {
			// Line doesn't exist, ignore
		}
	}

	// ---- Renderer management ---------------------------------------------

	/**
	 * Creates or returns the {@link CommentPaintRenderer} for the
	 * given side, installing it as a {@code PaintListener} and
	 * {@code MouseListener} on the {@link StyledText}.
	 */
	private CommentPaintRenderer ensureRenderer(SourceViewer sv,
			boolean isLeft) {
		CommentPaintRenderer renderer = isLeft
				? leftRenderer : rightRenderer;
		if (renderer != null) {
			return renderer;
		}

		renderer = new CommentPaintRenderer();
		StyledText st = sv.getTextWidget();
		st.addPaintListener(renderer);
		st.addMouseListener(renderer);
		st.addMouseMoveListener(renderer);

		if (isLeft) {
			leftRenderer = renderer;
		} else {
			rightRenderer = renderer;
		}
		return renderer;
	}

	/**
	 * Creates a {@link CommentActionHandler} that delegates to the
	 * corresponding {@code handle*} methods.
	 */
	private CommentActionHandler createActionHandler(
			String fileType) {
		return new CommentActionHandler() {
			@Override
			public void onReply(PullRequestComment comment) {
				handleReply(comment, fileType);
			}

			@Override
			public void onResolve(PullRequestComment comment) {
				handleResolve(comment);
			}

			@Override
			public void onDelete(PullRequestComment comment) {
				handleDelete(comment);
			}

			@Override
			public void onEdit(PullRequestComment comment) {
				handleEdit(comment);
			}

			@Override
			public void onSelect(PullRequestComment comment) {
				selectCommentInView(comment);
			}
		};
	}

	/**
	 * Clears all painted comments on the specified side by
	 * resetting vertical indents and disposing the renderer data.
	 *
	 * @param sv
	 *            the source viewer
	 * @param isLeft
	 *            {@code true} for left side, {@code false} for right side
	 */
	private void clearRenderedComments(SourceViewer sv, boolean isLeft) {
		CommentPaintRenderer renderer = isLeft
				? leftRenderer : rightRenderer;
		if (renderer == null) {
			return;
		}

		StyledText st = sv != null ? sv.getTextWidget() : null;
		if (st != null && !st.isDisposed()) {
			// Reset all vertical indents for lines that had threads
			for (Integer lineIndex : renderer.getThreads()
					.keySet()) {
				int idx = lineIndex.intValue();
				if (idx >= 0 && idx < st.getLineCount()) {
					st.setLineVerticalIndent(idx, 0);
				}
			}
		}

		renderer.clearThreads();

		if (st != null && !st.isDisposed()) {
			st.redraw();
		}
	}

	// ---- Comment actions -------------------------------------------------
	// TODO it looks like the comment actions contain some duplicated code. Please refactor.

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

	private void handleEdit(PullRequestComment comment) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(
				viewer.getControl().getShell(),
				"Edit Comment", //$NON-NLS-1$
				"Edit your comment:", //$NON-NLS-1$
				comment.getText());
		if (dialog.open() != Window.OK) {
			return;
		}

		String newText = dialog.getValue();
		if (newText == null || newText.trim().isEmpty()) {
			return;
		}

		PullRequest pr = getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		Job job = new Job("Editing comment") { //$NON-NLS-1$
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

					client.editComment(pr.getId(),
							comment.getId(),
							comment.getVersion(), newText,
							comment.isReviewComment());

					refreshAfterReply(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to edit comment", //$NON-NLS-1$
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to edit comment:" //$NON-NLS-1$
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
				refreshChangedFilesView(freshComments);
			});
		} catch (IOException e) {
			Activator.logError(
					"Failed to refresh comments", e); //$NON-NLS-1$
		}
	}

	private List<PullRequestComment> filterCommentsForCurrentFile(List<PullRequestComment> allComments) {
		if (currentComments == null
				|| currentComments.isEmpty()) {
			return allComments;
		}

		Set<String> paths = new HashSet<>();
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

	private void refreshCommentsView(List<PullRequestComment> freshComments) {
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

	private void refreshChangedFilesView(List<PullRequestComment> freshComments) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return;
			}
			IViewPart part = page.findView(
					PullRequestChangedFilesView.VIEW_ID);
			if (part instanceof PullRequestChangedFilesView) {
				((PullRequestChangedFilesView) part)
						.updateComments(freshComments);
			}
		} catch (Exception e) {
			Activator.logError(
					"Failed to refresh changed files view", //$NON-NLS-1$
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

	// ---- Disposal -------------------------------------------------------

	/**
	 * Disposes all resources managed by this installer. Should be
	 * called when the compare editor is closed.
	 */
	void dispose() {
		clearRenderedComments(leftSourceViewer, true);
		clearRenderedComments(rightSourceViewer, false);
		if (leftRenderer != null) {
			leftRenderer.dispose();
			leftRenderer = null;
		}
		if (rightRenderer != null) {
			rightRenderer.dispose();
			rightRenderer = null;
		}
		leftRulerColumn = null;
		rightRulerColumn = null;
		leftSourceViewer = null;
		rightSourceViewer = null;
		currentComments = null;
	}
}
