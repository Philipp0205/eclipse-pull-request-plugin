package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.swt.widgets.Display;

/**
 * Synchronizes pull request comments across the Synchronize view and the
 * compare editor.
 *
 * <p>
 * This class handles:
 * <ul>
 * <li>Fetching fresh comments from the API after an action</li>
 * <li>Refreshing Synchronize view decorators</li>
 * <li>Filtering comments for the current file</li>
 * </ul>
 * </p>
 */
final class CommentViewSynchronizer {

	/**
	 * Callback interface for refreshing the compare editor after
	 * comments are updated.
	 */
	interface CompareEditorRefreshCallback {
		/**
		 * Called when the compare editor should refresh with new
		 * comments.
		 *
		 * @param comments
		 *            the fresh comments for the current file
		 */
		void onCommentsRefreshed(List<PullRequestComment> comments);
	}

	private final CompareEditorRefreshCallback compareEditorCallback;
	private List<PullRequestComment> currentComments;

	/**
	 * Creates a new view synchronizer.
	 *
	 * @param compareEditorCallback
	 *            callback for refreshing the compare editor
	 */
	CommentViewSynchronizer(CompareEditorRefreshCallback compareEditorCallback) {
		this.compareEditorCallback = compareEditorCallback;
	}

	/**
	 * Sets the current comments displayed in the compare editor. This
	 * is used to determine which file paths to filter when
	 * refreshing.
	 *
	 * @param comments
	 *            the current comments
	 */
	void setCurrentComments(List<PullRequestComment> comments) {
		this.currentComments = comments;
	}

	/**
	 * Refreshes all views after a comment action completes. Fetches fresh
	 * comments from the API and updates Synchronize decorators and the compare
	 * editor.
	 *
	 * @param pr
	 *            the pull request
	 * @param client
	 *            the client for fetching fresh comments
	 */
	void refreshAfterAction(PullRequest pr, IPullRequestClient client) {
		try {
			List<PullRequestComment> freshComments = client.getPullRequestComments(pr.getId());

			Display.getDefault().asyncExec(() -> {
				List<PullRequestComment> fileComments = filterCommentsForCurrentFile(freshComments);
				compareEditorCallback.onCommentsRefreshed(fileComments);

				refreshChangedFilesView(freshComments);
			});
		} catch (IOException e) {
			Activator.logError("Failed to refresh comments", e); //$NON-NLS-1$
		}
	}

	/**
	 * Filters comments to only those that match the file paths of the
	 * current comments displayed in the compare editor.
	 *
	 * @param allComments
	 *            all comments from the API
	 * @return filtered comments for the current file
	 */
	private List<PullRequestComment> filterCommentsForCurrentFile(List<PullRequestComment> allComments) {
		if (currentComments == null || currentComments.isEmpty()) {
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
			if (c.getPath() != null && paths.contains(c.getPath())) {
				filtered.add(c);
			}
		}
		return filtered;
	}

	/**
	 * Refreshes the comment display and decorator. Updates the
	 * PullRequestContext with fresh comments and triggers a decorator
	 * refresh so comment counts update in the Synchronize View.
	 *
	 * @param freshComments
	 *            the fresh comments from the API
	 */
	private void refreshChangedFilesView(List<PullRequestComment> freshComments) {
		// Update context with fresh comments to trigger decorator refresh
		PullRequestContext context = PullRequestContext.getInstance();
		context.setComments(freshComments);
		context.refreshDecorators();
	}
}
