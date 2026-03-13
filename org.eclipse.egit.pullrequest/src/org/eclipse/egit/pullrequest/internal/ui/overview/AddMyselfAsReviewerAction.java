/*******************************************************************************
 * Copyright (C) 2026, Philipp Hoenisch and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.ui.overview;

import java.io.IOException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Action to add the current user as a reviewer to a pull request.
 */
public class AddMyselfAsReviewerAction extends Action {

	private final Shell shell;

	private PullRequest pullRequest;

	private Runnable refreshCallback;

	/**
	 * Creates a new action to add the current user as a reviewer.
	 *
	 * @param shell
	 *            the parent shell for dialogs
	 */
	public AddMyselfAsReviewerAction(Shell shell) {
		super(PRText.AddMyselfAsReviewer_ActionLabel);
		setToolTipText(PRText.AddMyselfAsReviewer_ActionTooltip);
		this.shell = shell;
	}

	/**
	 * Sets the pull request to add the current user as a reviewer to.
	 *
	 * @param pr
	 *            the pull request
	 */
	public void setPullRequest(PullRequest pr) {
		this.pullRequest = pr;
		setEnabled(pr != null);
	}

	/**
	 * Sets a callback to be invoked when the reviewer is added.
	 *
	 * @param callback
	 *            the callback to invoke
	 */
	public void setRefreshCallback(Runnable callback) {
		this.refreshCallback = callback;
	}

	@Override
	public void run() {
		if (pullRequest == null) {
			return;
		}

		IPullRequestClient client = PullRequestClientFactory.createClient();
		if (client == null) {
			MessageDialog.openError(shell,
					PRText.AddMyselfAsReviewer_ActionLabel,
					"Pull request provider not configured"); //$NON-NLS-1$
			return;
		}

		Job job = new Job(PRText.AddMyselfAsReviewer_JobName) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					// Get current username from client
					String username = client.getCurrentUser();
					if (username == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Unable to determine current username"); //$NON-NLS-1$
					}

					// Check if already a reviewer
					if (pullRequest.getReviewers() != null) {
						for (PullRequest.PullRequestParticipant p :
								pullRequest.getReviewers()) {
							if (username.equals(p.getUser().getName())) {
								// Already a reviewer
								return Status.OK_STATUS;
							}
						}
					}

					client.addReviewer(pullRequest.getId(), username);

					// Refresh the PR to get updated reviewer list
					PullRequest updatedPr = client
							.getPullRequest(pullRequest.getId());

					Display.getDefault().asyncExec(() -> {
						pullRequest.setReviewers(
								updatedPr.getReviewers());
						if (refreshCallback != null) {
							refreshCallback.run();
						}
					});

					Activator.logInfo(
							PRText.AddMyselfAsReviewer_Success);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							PRText.AddMyselfAsReviewer_Error, e);
					Display.getDefault().asyncExec(() -> {
						if (!shell.isDisposed()) {
							MessageDialog.openError(shell,
									PRText.AddMyselfAsReviewer_ActionLabel,
									PRText.AddMyselfAsReviewer_Error
											+ ": " + e.getMessage()); //$NON-NLS-1$
						}
					});
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							PRText.AddMyselfAsReviewer_Error, e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}
}
