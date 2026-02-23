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
package org.eclipse.egit.pullrequest.internal.ui;

import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * Action to open the reviewer management dialog for a pull request.
 */
public class ManageReviewersAction extends Action {

	private final Shell shell;

	private PullRequest pullRequest;

	private Runnable refreshCallback;

	/**
	 * Creates a new manage reviewers action.
	 *
	 * @param shell
	 *            the parent shell for the dialog
	 */
	public ManageReviewersAction(Shell shell) {
		super(PRText.ManageReviewers_ActionLabel);
		setToolTipText(PRText.ManageReviewers_ActionTooltip);
		this.shell = shell;
	}

	/**
	 * Sets the pull request to manage reviewers for.
	 *
	 * @param pr
	 *            the pull request
	 */
	public void setPullRequest(PullRequest pr) {
		this.pullRequest = pr;
		setEnabled(pr != null);
	}

	/**
	 * Sets a callback to be invoked when reviewers are modified.
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
					PRText.ManageReviewers_ActionLabel,
					"Pull request provider not configured"); //$NON-NLS-1$
			return;
		}

		ReviewerManagementDialog dialog = new ReviewerManagementDialog(shell,
				pullRequest, client);
		dialog.setRefreshCallback(refreshCallback);
		dialog.open();
	}
}
