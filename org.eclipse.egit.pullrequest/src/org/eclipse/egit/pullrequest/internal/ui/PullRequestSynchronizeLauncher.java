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
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;
import org.eclipse.egit.pullrequest.internal.util.PullRequestRefResolver;
import org.eclipse.egit.pullrequest.internal.util.PullRequestRefResolver.ResolvedRefs;
import org.eclipse.egit.pullrequest.internal.util.RepositoryResolver;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelSynchronize;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/**
 * Launches Eclipse's Synchronize view for pull request comparisons using
 * EGit's GitModelSynchronize infrastructure.
 * <p>
 * This class requires that the pull request's repository is cloned locally and
 * that the necessary Git refs are available (or can be fetched).
 */
@SuppressWarnings("restriction")
public class PullRequestSynchronizeLauncher {

	/**
	 * Launches the Synchronize view for a pull request, comparing the base
	 * branch with the head branch.
	 *
	 * @param pr
	 *            the pull request
	 */
	public static void launchForPullRequest(PullRequest pr) {
		// Set the active PR in context for comment overlay
		PullRequestContext.getInstance().setActivePullRequest(pr);

		Repository repo = RepositoryResolver.resolve(pr);
		if (repo == null) {
			showRepositoryNotFoundDialog();
			return;
		}

		try {
			ResolvedRefs refs = resolveRefsWithProgress(repo, pr);
			if (refs == null) {
				return; // Resolution failed, error already logged
			}

			launchSynchronize(repo, refs.getBaseCommit(),
					refs.getHeadCommit());
		} catch (Exception e) {
			Activator.logError("Failed to launch synchronize view for PR", //$NON-NLS-1$
					e);
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_ErrorMessage + ": " //$NON-NLS-1$
							+ e.getMessage());
		}
	}

	/**
	 * Launches the Synchronize view for a single commit, comparing it with its
	 * parent.
	 *
	 * @param pr
	 *            the pull request
	 * @param commit
	 *            the commit to compare
	 */
	public static void launchForCommit(PullRequest pr,
			PullRequestCommit commit) {
		// Set the active PR in context for comment overlay
		PullRequestContext.getInstance().setActivePullRequest(pr);

		Repository repo = RepositoryResolver.resolve(pr);
		if (repo == null) {
			showRepositoryNotFoundDialog();
			return;
		}

		try {
			RevCommit headCommit = resolveCommit(repo, commit.getId());
			if (headCommit == null) {
				Activator.logError(
						"Commit not found: " + commit.getId(), null); //$NON-NLS-1$
				return;
			}

			// Compare with first parent (or empty tree if no parent)
			RevCommit baseCommit = headCommit.getParentCount() > 0
					? headCommit.getParent(0) : null;

			launchSynchronize(repo, baseCommit, headCommit);
		} catch (Exception e) {
			Activator.logError("Failed to launch synchronize view for commit", //$NON-NLS-1$
					e);
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_ErrorMessage + ": " //$NON-NLS-1$
							+ e.getMessage());
		}
	}

	/**
	 * Launches the Synchronize view for a commit range.
	 *
	 * @param pr
	 *            the pull request
	 * @param baseCommit
	 *            the base commit
	 * @param headCommit
	 *            the head commit
	 */
	public static void launchForCommitRange(PullRequest pr,
			PullRequestCommit baseCommit, PullRequestCommit headCommit) {
		// Set the active PR in context for comment overlay
		PullRequestContext.getInstance().setActivePullRequest(pr);

		Repository repo = RepositoryResolver.resolve(pr);
		if (repo == null) {
			showRepositoryNotFoundDialog();
			return;
		}

		try {
			RevCommit base = resolveCommit(repo, baseCommit.getId());
			RevCommit head = resolveCommit(repo, headCommit.getId());

			if (base == null || head == null) {
				Activator.logError(
						"Commits not found: base=" + baseCommit.getId() //$NON-NLS-1$
								+ ", head=" + headCommit.getId(), //$NON-NLS-1$
						null);
				return;
			}

			launchSynchronize(repo, base, head);
		} catch (Exception e) {
			Activator.logError(
					"Failed to launch synchronize view for commit range", //$NON-NLS-1$
					e);
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_ErrorMessage + ": " //$NON-NLS-1$
							+ e.getMessage());
		}
	}

	/**
	 * Resolves refs with a progress dialog and optional fetching.
	 *
	 * @param repo
	 *            the repository
	 * @param pr
	 *            the pull request
	 * @return the resolved refs, or null if resolution failed
	 */
	private static ResolvedRefs resolveRefsWithProgress(Repository repo,
			PullRequest pr) {
		final ResolvedRefs[] result = new ResolvedRefs[1];

		try {
			new ProgressMonitorDialog(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell())
									.run(true, true,
											new IRunnableWithProgress() {
												@Override
												public void run(
														IProgressMonitor monitor)
														throws InvocationTargetException,
														InterruptedException {
													try {
														PullRequestRefResolver resolver = new PullRequestRefResolver(
																repo, pr);
														result[0] = resolver
																.resolve(true,
																		monitor);
													} catch (IOException e) {
														throw new InvocationTargetException(
																e);
													}
												}
											});
		} catch (InvocationTargetException e) {
			Activator.logError("Failed to resolve PR refs", e.getCause()); //$NON-NLS-1$
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_FetchErrorMessage
							+ ": " //$NON-NLS-1$
							+ e.getCause().getMessage());
			return null;
		} catch (InterruptedException e) {
			// User cancelled
			return null;
		}

		return result[0];
	}

	/**
	 * Resolves a commit by its SHA.
	 *
	 * @param repo
	 *            the repository
	 * @param commitId
	 *            the commit SHA
	 * @return the commit, or null if not found
	 */
	private static RevCommit resolveCommit(Repository repo, String commitId) {
		try {
			return repo.parseCommit(repo.resolve(commitId));
		} catch (Exception e) {
			Activator.logError("Failed to resolve commit: " + commitId, e); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Launches EGit's Synchronize view for the given repository and commit
	 * range.
	 *
	 * @param repo
	 *            the repository
	 * @param baseCommit
	 *            the base commit (may be null for initial commits)
	 * @param headCommit
	 *            the head commit
	 */
	private static void launchSynchronize(Repository repo,
			RevCommit baseCommit, RevCommit headCommit) {
		// Get all projects mapped to this repository
		Set<IProject> projects = new LinkedHashSet<>();
		IProject[] workspaceProjects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();

		for (IProject project : workspaceProjects) {
			Repository projectRepo = ResourceUtil.getRepository(project);
			if (projectRepo != null
					&& projectRepo.getDirectory()
							.equals(repo.getDirectory())) {
				projects.add(project);
			}
		}

		if (projects.isEmpty()) {
			Activator.logWarning(
					"No workspace projects found for repository: " //$NON-NLS-1$
							+ repo.getDirectory());
			MessageDialog.openWarning(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_WarningTitle,
					PRText.PullRequestSynchronizeLauncher_NoProjectsMessage);
			return;
		}

		// Build IResource[] from projects
		IResource[] resources = projects.toArray(new IResource[0]);

		// Create GitSynchronizeData
		// Source = base (destination/target), Destination = head (source)
		String srcRev = baseCommit != null ? baseCommit.getName()
				: "^tree"; //$NON-NLS-1$
		String dstRev = headCommit.getName();

		GitSynchronizeData data = new GitSynchronizeData(repo, srcRev, dstRev,
				false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// Launch the synchronize view
		GitModelSynchronize.launch(dataSet, resources);
	}

	/**
	 * Shows an error dialog explaining that the repository is not cloned
	 * locally.
	 */
	private static void showRepositoryNotFoundDialog() {
		Display.getDefault().asyncExec(() -> {
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_RepoNotFoundTitle,
					PRText.PullRequestSynchronizeLauncher_RepoNotFoundMessage);
		});
	}
}
