package org.eclipse.egit.pullrequest.internal.ui;

import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;

/**
 * Callback interface for actions triggered from an inline comment
 * rendered in the compare viewer. Used by both the paint-based
 * {@link CommentPaintRenderer} and the {@link CommentRulerColumn}.
 */
public interface CommentActionHandler {

	/**
	 * Called when the user clicks the Reply link.
	 *
	 * @param comment
	 *            the root comment being replied to
	 */
	void onReply(PullRequestComment comment);

	/**
	 * Called when the user clicks the Resolve/Reopen link.
	 * Only shown for Bitbucket provider.
	 *
	 * @param comment
	 *            the root comment being resolved/reopened
	 */
	void onResolve(PullRequestComment comment);

	/**
	 * Called when the user clicks the Delete link.
	 *
	 * @param comment
	 *            the comment being deleted
	 */
	void onDelete(PullRequestComment comment);

	/**
	 * Called when the user clicks the Edit link.
	 *
	 * @param comment
	 *            the comment being edited
	 */
	void onEdit(PullRequestComment comment);
}
