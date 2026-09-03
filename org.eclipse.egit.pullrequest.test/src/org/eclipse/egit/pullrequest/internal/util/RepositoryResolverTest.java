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
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Test;

/**
 * Tests selection of the local clone that belongs to a pull request, using
 * real repositories in a temporary directory.
 */
public class RepositoryResolverTest {

	private final List<Git> repositories = new ArrayList<>();

	private File tempRoot;

	@After
	public void tearDown() throws IOException {
		for (Git git : repositories) {
			git.getRepository().close();
			git.close();
		}
		repositories.clear();
		if (tempRoot != null) {
			delete(tempRoot);
			tempRoot = null;
		}
	}

	@Test
	public void testPicksClonePointingAtPullRequestRepository()
			throws Exception {
		Repository other = createRepository("other", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/SOC/other.git"); //$NON-NLS-1$
		Repository smartest = createRepository("smartest", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/SOC/smartest.git"); //$NON-NLS-1$

		Repository resolved = RepositoryResolver.findBestMatch(
				Arrays.asList(other, smartest), matcher());

		assertThat(resolved, sameInstance(smartest));
	}

	@Test
	public void testPrefersExactHostOverSameNameInAnotherProject()
			throws Exception {
		Repository sameName = createRepository("zenith-smartest", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/ZENITH/smartest.git"); //$NON-NLS-1$
		Repository smartest = createRepository("soc-smartest", //$NON-NLS-1$
				"https://server.example.com/bitbucket/scm/soc/smartest.git"); //$NON-NLS-1$

		Repository resolved = RepositoryResolver
				.findBestMatch(Arrays.asList(sameName, smartest), matcher());

		assertThat(resolved, sameInstance(smartest));
	}

	@Test
	public void testMatchesSecondaryRemote() throws Exception {
		Repository repository = createRepository("smartest", //$NON-NLS-1$
				"ssh://git@other.example.com:7999/FORK/smartest-fork.git"); //$NON-NLS-1$
		addRemote(repository, "upstream", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/SOC/smartest.git"); //$NON-NLS-1$

		assertThat(
				RepositoryResolver.findBestMatch(Arrays.asList(repository),
						matcher()),
				sameInstance(repository));
	}

	@Test
	public void testUnrelatedCloneIsNotMatched() throws Exception {
		Repository repository = createRepository("unrelated", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/SOC/tools.git"); //$NON-NLS-1$

		assertThat(RepositoryResolver
				.findBestMatch(Arrays.asList(repository), matcher()),
				nullValue());
	}

	@Test
	public void testFindRemoteNameOfPullRequestRepository() throws Exception {
		Repository repository = createRepository("smartest", //$NON-NLS-1$
				"ssh://git@mirror.example.com/mirror/soc/smartest.git"); //$NON-NLS-1$
		addRemote(repository, "bitbucket", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/SOC/smartest.git"); //$NON-NLS-1$

		assertThat(
				RepositoryResolver.findRemoteName(repository,
						pullRequestRepository()),
				equalTo("bitbucket")); //$NON-NLS-1$
	}

	@Test
	public void testFindRemoteNameWithoutMatchingRemote() throws Exception {
		Repository repository = createRepository("unrelated", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/SOC/tools.git"); //$NON-NLS-1$

		assertThat(RepositoryResolver.findRemoteName(repository,
				pullRequestRepository()), nullValue());
	}

	@Test
	public void testResolveWithoutPullRequestIsSafe() {
		assertThat(RepositoryResolver.resolve(null), nullValue());
	}

	private static RepositoryUrlMatcher matcher() {
		return RepositoryResolver.matcherFor(pullRequestRepository());
	}

	private static PullRequest.Repository pullRequestRepository() {
		PullRequest.Project project = new PullRequest.Project();
		project.setKey("SOC"); //$NON-NLS-1$
		PullRequest.Repository repository = new PullRequest.Repository();
		repository.setProject(project);
		repository.setSlug("smartest"); //$NON-NLS-1$
		repository.setCloneUrl(
				"https://server.example.com/bitbucket/scm/soc/smartest.git"); //$NON-NLS-1$
		return repository;
	}

	private Repository createRepository(String name, String originUrl)
			throws Exception {
		if (tempRoot == null) {
			tempRoot = Files.createTempDirectory("pr-resolver-test") //$NON-NLS-1$
					.toFile();
		}
		Git git = Git.init().setDirectory(new File(tempRoot, name)).call();
		repositories.add(git);
		Repository repository = git.getRepository();
		addRemote(repository, "origin", originUrl); //$NON-NLS-1$
		return repository;
	}

	private static void addRemote(Repository repository, String remoteName,
			String url) throws Exception {
		RemoteConfig remote = new RemoteConfig(repository.getConfig(),
				remoteName);
		remote.addURI(new URIish(url));
		remote.update(repository.getConfig());
		repository.getConfig().save();
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
