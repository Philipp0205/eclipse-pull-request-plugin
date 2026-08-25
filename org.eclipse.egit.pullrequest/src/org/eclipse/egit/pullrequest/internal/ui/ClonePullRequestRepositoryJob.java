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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Clones a pull request's target repository and imports Eclipse projects found
 * in it.
 */
public class ClonePullRequestRepositoryJob extends Job {

	private final PullRequest pullRequest;

	private final Shell shell;

	private final Runnable completion;

	/**
	 * Creates a repository clone job.
	 *
	 * @param pullRequest
	 *            pull request whose target repository will be cloned
	 * @param shell
	 *            parent shell for error dialogs
	 * @param completion
	 *            action to run on the UI thread after cloning
	 */
	public ClonePullRequestRepositoryJob(PullRequest pullRequest, Shell shell,
			Runnable completion) {
		super(PRText.CloneProject_JobName);
		this.pullRequest = pullRequest;
		this.shell = shell;
		this.completion = completion;
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		PullRequest.Repository repository = pullRequest.getToRef()
				.getRepository();
		String cloneUrl = repository.getCloneUrl();
		if (cloneUrl == null || cloneUrl.isBlank()) {
			return fail(PRText.CloneProject_MissingUrl, null);
		}

		IPath workspacePath = ResourcesPlugin.getWorkspace().getRoot()
				.getLocation();
		File destination = workspacePath.append(repository.getSlug()).toFile();
		if (destination.exists()) {
			return fail(PRText.CloneProject_DestinationExists, null);
		}

		monitor.beginTask(PRText.CloneProject_JobName,
				IProgressMonitor.UNKNOWN);
		try (Git git = Git.cloneRepository().setURI(cloneUrl)
				.setDirectory(destination)
				.setCredentialsProvider(credentialsProvider()).call()) {
			RepositoryUtil.INSTANCE
					.addConfiguredRepository(git.getRepository().getDirectory());
			importProjects(destination, monitor);
			Display.getDefault().asyncExec(completion);
			return Status.OK_STATUS;
		} catch (Exception e) {
			return fail(e.getMessage(), e);
		} finally {
			monitor.done();
		}
	}

	private CredentialsProvider credentialsProvider() {
		String provider = preference(
				PRPreferences.PULLREQUEST_PROVIDER_TYPE);
		String username;
		String token;
		if ("GITHUB".equals(provider)) { //$NON-NLS-1$
			username = "x-access-token"; //$NON-NLS-1$
			token = preference(PRPreferences.GITHUB_ACCESS_TOKEN);
		} else {
			username = preference(PRPreferences.BITBUCKET_USERNAME);
			token = preference(PRPreferences.BITBUCKET_ACCESS_TOKEN);
		}
		if (username.isBlank() || token.isBlank()) {
			return null;
		}
		return new UsernamePasswordCredentialsProvider(username, token);
	}

	private static String preference(String key) {
		return Activator.getDefault().getPreferenceStore().getString(key);
	}

	private static void importProjects(File destination,
			IProgressMonitor monitor) throws IOException, CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		List<java.nio.file.Path> projectFiles;
		try (Stream<java.nio.file.Path> paths = Files
				.walk(destination.toPath())) {
			projectFiles = paths.filter(path -> ".project".equals( //$NON-NLS-1$
					path.getFileName().toString())).toList();
		}

		for (java.nio.file.Path projectFile : projectFiles) {
			IProjectDescription description = workspace
					.loadProjectDescription(
							new Path(projectFile.toAbsolutePath().toString()));
			IProject project = workspace.getRoot()
					.getProject(description.getName());
			if (project.exists()) {
				continue;
			}
			description.setLocation(new Path(
					projectFile.getParent().toAbsolutePath().toString()));
			project.create(description, monitor);
			project.open(monitor);
		}
	}

	private IStatus fail(String detail, Throwable error) {
		String message = PRText.CloneProject_Error
				+ (detail == null || detail.isBlank()
						? "" : ": " + detail); //$NON-NLS-1$ //$NON-NLS-2$
		Activator.logError(message, error);
		Display.getDefault().asyncExec(() -> MessageDialog.openError(shell,
				PRText.CloneProject_ErrorTitle, message));
		return new Status(IStatus.ERROR, Activator.PLUGIN_ID, message, error);
	}
}
