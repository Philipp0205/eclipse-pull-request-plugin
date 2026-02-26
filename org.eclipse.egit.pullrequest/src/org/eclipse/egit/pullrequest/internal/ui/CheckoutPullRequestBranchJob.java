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

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.op.FetchOperation;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.util.RepositoryResolver;
import org.eclipse.egit.ui.internal.branch.BranchOperationUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Job that checks out the source branch of a pull request
 */
public class CheckoutPullRequestBranchJob extends Job {

	private final PullRequest pullRequest;
	private final Shell shell;

	/**
	 * Creates a new checkout job
	 *
	 * @param pullRequest
	 *            the pull request
	 * @param shell
	 *            the parent shell for dialogs
	 */
	public CheckoutPullRequestBranchJob(PullRequest pullRequest,
			Shell shell) {
		super(PRText.CheckoutBranch_JobName);
		this.pullRequest = pullRequest;
		this.shell = shell;
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			monitor.beginTask(PRText.CheckoutBranch_JobName, 4);

			// 1. Resolve local repository
			monitor.subTask("Resolving local repository..."); //$NON-NLS-1$
			Repository repo = RepositoryResolver.resolve(pullRequest);
			if (repo == null) {
				showError(PRText.CheckoutBranch_ErrorNoRepository);
			return Status.CANCEL_STATUS;
		}
		monitor.worked(1);

		// 2. Determine remote and fetch
		monitor.subTask("Fetching branch..."); //$NON-NLS-1$
		String remoteName = determineRemote(repo);
		if (remoteName == null) {
			showError("Could not determine remote for pull request"); //$NON-NLS-1$
			return Status.CANCEL_STATUS;
		}

		String branchName = pullRequest.getFromRef().getDisplayId();
		if (!fetchBranch(repo, remoteName, branchName, monitor)) {
			showError(MessageFormat.format(
					PRText.CheckoutBranch_ErrorFetchFailed, branchName));
			return Status.CANCEL_STATUS;
		}
		monitor.worked(1);

		// 3. Show confirmation dialog
		monitor.subTask("Confirming checkout..."); //$NON-NLS-1$
		if (!confirmCheckout(branchName, remoteName)) {
			return Status.CANCEL_STATUS;
		}
			monitor.worked(1);

			// 4. Checkout branch
			monitor.subTask("Checking out branch..."); //$NON-NLS-1$
			String target = "refs/remotes/" + remoteName + "/" + branchName; //$NON-NLS-1$ //$NON-NLS-2$
			Display.getDefault().syncExec(() -> {
				BranchOperationUI.checkout(repo, target).start();
			});
			monitor.worked(1);

			return Status.OK_STATUS;
		} catch (Exception e) {
			Activator.logError("Error checking out pull request branch", e); //$NON-NLS-1$
			showError("Error checking out branch: " + e.getMessage()); //$NON-NLS-1$
			return Status.CANCEL_STATUS;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Determines which remote to use for the pull request. For same-repo PRs,
	 * uses "origin". For fork PRs, would need to add/find a fork remote.
	 *
	 * @param repo
	 *            the repository
	 * @return the remote name, or null if not found
	 */
	private String determineRemote(Repository repo) {
		// Check if fromRef and toRef repositories match (same-repo PR)
		PullRequest.Repository fromRepo = pullRequest.getFromRef()
				.getRepository();
		PullRequest.Repository toRepo = pullRequest.getToRef()
				.getRepository();

		boolean isSameRepo = fromRepo.getSlug().equals(toRepo.getSlug())
				&& fromRepo.getProject().getKey()
						.equals(toRepo.getProject().getKey());

		if (isSameRepo) {
			// Same-repo PR: use origin
			return "origin"; //$NON-NLS-1$
		} else {
			// Fork PR: need to add/find fork remote
			return findOrAddForkRemote(repo, fromRepo);
		}
	}

	/**
	 * Finds or adds a remote for a fork repository
	 *
	 * @param repo
	 *            the local repository
	 * @param forkRepo
	 *            the fork repository
	 * @return the remote name, or null if failed
	 */
	private String findOrAddForkRemote(Repository repo,
			PullRequest.Repository forkRepo) {
		String cloneUrl = forkRepo.getCloneUrl();
		if (cloneUrl == null) {
			return null;
		}

		try {
			URIish forkUri = new URIish(cloneUrl);

			// Check if a remote with this URL already exists
			for (RemoteConfig remote : RemoteConfig
					.getAllRemoteConfigs(repo.getConfig())) {
				for (URIish uri : remote.getURIs()) {
					if (uri.toString().equals(forkUri.toString())) {
						return remote.getName();
					}
				}
			}

			// Add new remote for fork
			String remoteName = forkRepo.getProject().getKey().toLowerCase()
					+ "-" + forkRepo.getSlug(); //$NON-NLS-1$
			RemoteConfig remoteConfig = new RemoteConfig(repo.getConfig(),
					remoteName);
			remoteConfig.addURI(forkUri);
			remoteConfig.update(repo.getConfig());
			repo.getConfig().save();

			return remoteName;
		} catch (Exception e) {
			Activator.logError("Error setting up fork remote", e); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Fetches a branch from a remote
	 *
	 * @param repo
	 *            the repository
	 * @param remoteName
	 *            the remote name
	 * @param branchName
	 *            the branch name
	 * @param monitor
	 *            progress monitor
	 * @return true if successful
	 */
	private boolean fetchBranch(Repository repo, String remoteName,
			String branchName, IProgressMonitor monitor) {
		try {
			RemoteConfig remoteConfig = new RemoteConfig(repo.getConfig(),
					remoteName);

			RefSpec refSpec = new RefSpec(
					"+refs/heads/" + branchName + ":refs/remotes/" //$NON-NLS-1$ //$NON-NLS-2$
							+ remoteName + "/" + branchName); //$NON-NLS-1$
			List<RefSpec> refSpecs = new ArrayList<>();
			refSpecs.add(refSpec);

			FetchOperation fetchOp = new FetchOperation(repo, remoteConfig,
					60, false);
			fetchOp.run(monitor);

			return true;
		} catch (Exception e) {
			Activator.logError("Error fetching branch", e); //$NON-NLS-1$
			return false;
		}
	}

	/**
	 * Shows a confirmation dialog for checking out the branch
	 *
	 * @param branchName
	 *            the branch name
	 * @param remoteName
	 *            the remote name
	 * @return true if user confirmed
	 */
	private boolean confirmCheckout(String branchName, String remoteName) {
		final boolean[] result = new boolean[1];
		Display.getDefault().syncExec(() -> {
			String repoName = pullRequest.getFromRef().getRepository()
					.getName();
			String commitSha = pullRequest.getFromRef().getLatestCommit();
			String shortSha = commitSha != null && commitSha.length() > 7
					? commitSha.substring(0, 7)
					: commitSha;

			String message = MessageFormat.format(
					PRText.CheckoutBranch_ConfirmMessage, branchName, repoName,
					shortSha != null ? shortSha : "unknown"); //$NON-NLS-1$

			result[0] = MessageDialog.openConfirm(shell,
					PRText.CheckoutBranch_ConfirmTitle, message);
		});
		return result[0];
	}

	/**
	 * Shows an error dialog
	 *
	 * @param message
	 *            the error message
	 */
	private void showError(String message) {
		Display.getDefault().asyncExec(() -> {
			MessageDialog.openError(shell, "Checkout Error", message); //$NON-NLS-1$
		});
	}
}
