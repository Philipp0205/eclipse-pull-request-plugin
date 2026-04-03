package org.eclipse.egit.pullrequest.internal.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/**
 * Lightweight singleton service for tracking the currently active pull request
 * in the workbench.
 * <p>
 * This allows different views and editors to coordinate around a shared PR
 * context without tight coupling (e.g., comment overlay installers can
 * retrieve the active PR without depending on a specific view).
 */
public class PullRequestContext {

	private static final PullRequestContext INSTANCE = new PullRequestContext();

	private static final String COMMENT_DECORATOR_ID = 
			"org.eclipse.egit.pullrequest.commentDecorator"; //$NON-NLS-1$

	private PullRequest activePullRequest;

	private IPullRequestClient client;

	private List<PullRequestComment> comments = Collections.emptyList();

	private PullRequestContext() {
		// Singleton
	}

	/**
	 * Gets the singleton instance.
	 *
	 * @return the instance
	 */
	public static PullRequestContext getInstance() {
		return INSTANCE;
	}

	/**
	 * Sets the currently active pull request and client.
	 *
	 * @param pullRequest
	 *            the pull request, or null to clear
	 * @param client
	 *            the pull request client, or null to clear
	 */
	public void setActivePullRequest(PullRequest pullRequest,
			IPullRequestClient client) {
		this.activePullRequest = pullRequest;
		this.client = client;
		if (pullRequest == null) {
			setComments(Collections.emptyList());
		}
	}

	/**
	 * Sets the currently active pull request.
	 *
	 * @param pullRequest
	 *            the pull request, or null to clear
	 */
	public void setActivePullRequest(PullRequest pullRequest) {
		setActivePullRequest(pullRequest, this.client);
	}

	/**
	 * Gets the currently active pull request.
	 *
	 * @return the pull request, or null if none is active
	 */
	public PullRequest getActivePullRequest() {
		return activePullRequest;
	}

	/**
	 * Gets the client for the currently active pull request.
	 *
	 * @return the client, or null if none is active
	 */
	public IPullRequestClient getClient() {
		return client;
	}

	/**
	 * Sets the comments for the currently active pull request.
	 *
	 * @param comments
	 *            the list of comments, or null/empty to clear
	 */
	public void setComments(List<PullRequestComment> comments) {
		if (comments != null) {
			this.comments = new ArrayList<>(comments);
		} else {
			this.comments = Collections.emptyList();
		}
		refreshDecorators();
	}

	/**
	 * Gets the comments for the currently active pull request.
	 *
	 * @return an unmodifiable list of comments
	 */
	public List<PullRequestComment> getComments() {
		return Collections.unmodifiableList(comments);
	}

	/**
	 * Gets the comment count for a specific file path, including replies.
	 *
	 * @param filePath
	 *            the repository-relative file path
	 * @return the number of comments (including replies) for this file
	 */
	public int getCommentCountForFile(String filePath) {
		if (filePath == null || comments.isEmpty()) {
			return 0;
		}
		return (int) comments.stream()
				.filter(comment -> filePath.equals(comment.getPath()))
				.mapToLong(comment -> {
					long count = 1; // Root comment
					if (comment.getReplies() != null) {
						count += comment.getReplies().size();
					}
					return count;
				})
				.sum();
	}

	/**
	 * Refreshes label decorators to reflect updated comment counts.
	 */
	void refreshDecorators() {
		Display display = Display.getDefault();
		if (display != null && !display.isDisposed()) {
			display.asyncExec(() -> {
				try {
					if (PlatformUI.isWorkbenchRunning()) {
						PlatformUI.getWorkbench().getDecoratorManager()
								.update(COMMENT_DECORATOR_ID);
					}
				} catch (Exception e) {
					// Ignore - workbench may not be fully initialized
				}
			});
		}
	}
}
