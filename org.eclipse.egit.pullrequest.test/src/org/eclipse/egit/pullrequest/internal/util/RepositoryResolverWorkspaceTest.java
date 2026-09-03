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
package org.eclipse.egit.pullrequest.internal.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.nio.file.Files;

import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests resolving a pull request repository against the repositories the
 * workspace knows about, which is what the reported failure came down to: a
 * clone that exists but is not currently open in EGit's repository cache.
 */
public class RepositoryResolverWorkspaceTest {

	private static final String SERVER_URL = "https://socgit.example.com/bitbucket"; //$NON-NLS-1$

	private static final String SSH_URL = "ssh://git@socgit.example.com:7999/SOC/smartest.git"; //$NON-NLS-1$

	private File tempRoot;

	private File gitDir;

	private String previousProvider;

	private String previousServerUrl;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("no plug-in instance available", //$NON-NLS-1$
				Activator.getDefault() != null);
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		previousProvider = store
				.getString(PRPreferences.PULLREQUEST_PROVIDER_TYPE);
		previousServerUrl = store
				.getString(PRPreferences.BITBUCKET_SERVER_URL);
		store.setValue(PRPreferences.PULLREQUEST_PROVIDER_TYPE, "BITBUCKET"); //$NON-NLS-1$
		store.setValue(PRPreferences.BITBUCKET_SERVER_URL, SERVER_URL);

		tempRoot = Files.createTempDirectory("pr-workspace-test").toFile(); //$NON-NLS-1$
		try (Git git = Git.init()
				.setDirectory(new File(tempRoot, "smartest")).call()) { //$NON-NLS-1$
			Repository repository = git.getRepository();
			gitDir = repository.getDirectory();
			RemoteConfig remote = new RemoteConfig(repository.getConfig(),
					"origin"); //$NON-NLS-1$
			remote.addURI(new URIish(SSH_URL));
			remote.update(repository.getConfig());
			repository.getConfig().save();
		}
	}

	@After
	public void tearDown() {
		if (gitDir != null) {
			try {
				RepositoryUtil.INSTANCE.removeDir(gitDir);
			} catch (Exception e) {
				// Nothing to clean up then.
			}
		}
		if (Activator.getDefault() != null) {
			IPreferenceStore store = Activator.getDefault()
					.getPreferenceStore();
			store.setValue(PRPreferences.PULLREQUEST_PROVIDER_TYPE,
					previousProvider);
			store.setValue(PRPreferences.BITBUCKET_SERVER_URL,
					previousServerUrl);
		}
		if (tempRoot != null) {
			delete(tempRoot);
			tempRoot = null;
		}
	}

	@Test
	public void testResolvesConfiguredCloneWithSshRemote() {
		Assume.assumeTrue("cannot configure repositories", //$NON-NLS-1$
				configure(gitDir));
		// The clone is known to the Git Repositories view but not open, so
		// searching EGit's repository cache alone cannot find it.
		assertThat(RepositoryCache.INSTANCE.getRepository(gitDir),
				nullValue());

		Repository resolved = RepositoryResolver.resolve(pullRequest(
				"SOC", "smartest")); //$NON-NLS-1$ //$NON-NLS-2$

		assertThat(resolved, notNullValue());
		assertThat(resolved.getDirectory().getAbsoluteFile(),
				equalTo(gitDir.getAbsoluteFile()));
	}

	@Test
	public void testDoesNotResolveADifferentRepository() {
		Assume.assumeTrue("cannot configure repositories", //$NON-NLS-1$
				configure(gitDir));

		assertThat(RepositoryResolver
				.resolve(pullRequest("SOC", "tools")), nullValue()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean configure(File gitDir) {
		try {
			RepositoryUtil.INSTANCE.addConfiguredRepository(gitDir);
			return RepositoryUtil.INSTANCE.getRepositories()
					.contains(gitDir.getAbsolutePath());
		} catch (Exception e) {
			return false;
		}
	}

	private static PullRequest pullRequest(String projectKey, String slug) {
		PullRequest.Project project = new PullRequest.Project();
		project.setKey(projectKey);
		PullRequest.Repository repository = new PullRequest.Repository();
		repository.setProject(project);
		repository.setSlug(slug);
		// Bitbucket does not always report a clone URL, so resolution has to
		// work from the configured server URL alone.
		PullRequest.PullRequestRef toRef = new PullRequest.PullRequestRef();
		toRef.setDisplayId("master"); //$NON-NLS-1$
		toRef.setRepository(repository);
		PullRequest pullRequest = new PullRequest();
		pullRequest.setId(177313);
		pullRequest.setToRef(toRef);
		return pullRequest;
	}

	private static void delete(File file) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				delete(child);
			}
		}
		file.delete();
	}
}
