package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;

/**
 * Executes comment actions (Reply, Resolve, Delete, Edit, New Comment)
 * by showing dialogs, calling the pull request client API, and
 * triggering view refreshes.
 *
 * <p>
 * All actions follow a consistent pattern:
 * <ol>
 * <li>Show dialog to gather user input (if needed)</li>
 * <li>Get the selected pull request</li>
 * <li>Create a background Job that calls the API</li>
 * <li>Refresh views after successful completion</li>
 * </ol>
 * </p>
 */
final class CommentActionExecutor {

	/**
	 * Callback interface for refreshing views after an action
	 * completes.
	 */
	interface RefreshCallback {
		/**
		 * Called after a comment action completes successfully.
		 *
		 * @param pr
		 *            the pull request
		 * @param client
		 *            the client (for fetching fresh comments)
		 */
		void onRefreshNeeded(PullRequest pr, IPullRequestClient client);
	}

	private final Shell shell;
	private final PullRequestProvider prProvider;
	private final RefreshCallback refreshCallback;

	/**
	 * Creates a new comment action executor.
	 *
	 * @param shell
	 *            the parent shell for dialogs
	 * @param prProvider
	 *            provider for the selected pull request
	 * @param refreshCallback
	 *            callback for refreshing views
	 */
	CommentActionExecutor(Shell shell,
			PullRequestProvider prProvider,
			RefreshCallback refreshCallback) {
		this.shell = shell;
		this.prProvider = prProvider;
		this.refreshCallback = refreshCallback;
	}

	/**
	 * Handles the Reply action by showing an input dialog and posting
	 * a reply to the comment.
	 *
	 * @param comment
	 *            the comment to reply to
	 * @param fileType
	 *            the file type (FROM/TO)
	 */
	void handleReply(PullRequestComment comment, String fileType) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(shell,
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

		PullRequest pr = prProvider.getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		Job job = new Job("Posting reply") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client = PullRequestClientFactory
							.createClient(pr);
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					client.addComment(pr.getId(), replyText,
							comment.getId());

					refreshCallback.onRefreshNeeded(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError("Failed to post reply", //$NON-NLS-1$
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

	/**
	 * Handles the Resolve/Reopen action by toggling the comment state.
	 *
	 * @param comment
	 *            the comment to resolve or reopen
	 */
	void handleResolve(PullRequestComment comment) {
		PullRequest pr = prProvider.getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		boolean isResolved = "RESOLVED" //$NON-NLS-1$
				.equals(comment.getState());
		String action = isResolved ? "Reopening" : "Resolving"; //$NON-NLS-1$ //$NON-NLS-2$

		Job job = new Job(action + " comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client = PullRequestClientFactory
							.createClient(pr);
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					String newState = isResolved ? "OPEN" //$NON-NLS-1$
							: "RESOLVED"; //$NON-NLS-1$
					client.updateCommentState(pr.getId(),
							comment.getId(), comment.getVersion(),
							newState);

					refreshCallback.onRefreshNeeded(pr, client);
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

	/**
	 * Handles the Delete action by showing a confirmation dialog and
	 * deleting the comment.
	 *
	 * @param comment
	 *            the comment to delete
	 */
	void handleDelete(PullRequestComment comment) {
		boolean confirmed = MessageDialog.openConfirm(shell,
				"Delete Comment", //$NON-NLS-1$
				"Are you sure you want to delete this comment?"); //$NON-NLS-1$
		if (!confirmed) {
			return;
		}

		PullRequest pr = prProvider.getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		Job job = new Job("Deleting comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client = PullRequestClientFactory
							.createClient(pr);
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					client.deleteComment(pr.getId(), comment.getId(),
							comment.getVersion(),
							comment.isReviewComment());

					refreshCallback.onRefreshNeeded(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError("Failed to delete comment", //$NON-NLS-1$
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

	/**
	 * Handles the Edit action by showing an input dialog with the
	 * current comment text and updating the comment.
	 *
	 * @param comment
	 *            the comment to edit
	 */
	void handleEdit(PullRequestComment comment) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(shell,
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

		PullRequest pr = prProvider.getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		Job job = new Job("Editing comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client = PullRequestClientFactory
							.createClient(pr);
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					client.editComment(pr.getId(), comment.getId(),
							comment.getVersion(), newText,
							comment.isReviewComment());

					refreshCallback.onRefreshNeeded(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError("Failed to edit comment", //$NON-NLS-1$
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

	/**
	 * Handles creating a new inline comment at the specified line.
	 *
	 * @param line
	 *            the line number (1-based)
	 * @param fileType
	 *            the file type (FROM/TO)
	 * @param filePath
	 *            the file path
	 */
	void handleNewComment(int line, String fileType, String filePath) {
		MultiLineInputDialog dialog = new MultiLineInputDialog(shell,
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

		PullRequest pr = prProvider.getSelectedPullRequest();
		if (pr == null) {
			return;
		}

		if (filePath == null) {
			Activator.logError("Cannot create comment:" //$NON-NLS-1$
					+ " file path unknown", //$NON-NLS-1$
					null);
			return;
		}

		Job job = new Job("Posting comment") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client = PullRequestClientFactory
							.createClient(pr);
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider" //$NON-NLS-1$
										+ " not configured"); //$NON-NLS-1$
					}

					String commitId = pr.getFromRef().getLatestCommit();
					String lineType = "ADDED"; //$NON-NLS-1$

					client.addInlineComment(pr.getId(), commentText,
							filePath, line, lineType, fileType,
							commitId);

					refreshCallback.onRefreshNeeded(pr, client);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError("Failed to post comment", //$NON-NLS-1$
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

	/**
	 * Provider interface for obtaining the selected pull request.
	 */
	interface PullRequestProvider {
		/**
		 * Returns the currently selected pull request, or
		 * {@code null}.
		 *
		 * @return the pull request or null
		 */
		PullRequest getSelectedPullRequest();
	}
}
