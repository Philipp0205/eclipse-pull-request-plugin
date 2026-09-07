/*******************************************************************************
 * Copyright (C) 2026, Philipp0205 and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.github;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.github.GitHubSearchQueries.Query;
import org.junit.Test;

/**
 * Tests for {@link GitHubSearchQueries}
 */
public class GitHubSearchQueriesTest {

	@Test
	public void testScopesAreReadFromCommasAndWhitespace() {
		assertThat(
				GitHubSearchQueries
						.parseScopes(" alice,  acme\nbob/tool , "), //$NON-NLS-1$
				contains("alice", "acme", "bob/tool")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	public void testScopesAreDeduplicatedAndEmptyValuesAreIgnored() {
		assertThat(GitHubSearchQueries.parseScopes("alice, alice"), //$NON-NLS-1$
				contains("alice")); //$NON-NLS-1$
		assertThat(GitHubSearchQueries.parseScopes("   "), empty()); //$NON-NLS-1$
		assertThat(GitHubSearchQueries.parseScopes(null), empty());
	}

	@Test
	public void testBareNameCoversEverythingAnOwnerHas() {
		assertThat(GitHubSearchQueries.qualifierFor("alice"), //$NON-NLS-1$
				equalTo("user:alice")); //$NON-NLS-1$
		assertThat(GitHubSearchQueries.qualifierFor("bob/tool"), //$NON-NLS-1$
				equalTo("repo:bob/tool")); //$NON-NLS-1$
	}

	@Test
	public void testAlreadyQualifiedScopesAreKeptAsWritten() {
		assertThat(GitHubSearchQueries.qualifierFor("org:acme"), //$NON-NLS-1$
				equalTo("org:acme")); //$NON-NLS-1$
		assertThat(GitHubSearchQueries.qualifierFor("user:alice"), //$NON-NLS-1$
				equalTo("user:alice")); //$NON-NLS-1$
		assertThat(GitHubSearchQueries.qualifierFor("repo:bob/tool"), //$NON-NLS-1$
				equalTo("repo:bob/tool")); //$NON-NLS-1$
	}

	@Test
	public void testConfiguredScopesShareOneQuery() {
		List<Query> queries = GitHubSearchQueries
				.configuredPullRequestQueries(
						Arrays.asList("alice", "acme", "bob/tool"), null, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						null);

		assertThat(queries, hasSize(1));
		assertThat(queries.get(0).getText(),
				equalTo("is:pr user:alice user:acme repo:bob/tool " //$NON-NLS-1$
						+ "is:open")); //$NON-NLS-1$
	}

	@Test
	public void testManyScopesAreSplitIntoSeveralQueries() {
		List<String> scopes = new ArrayList<>();
		for (int i = 0; i < 45; i++) {
			scopes.add("owner" + i + "/repo"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		List<Query> queries = GitHubSearchQueries
				.configuredPullRequestQueries(scopes, null, null);

		assertThat(queries, hasSize(3));
		assertThat(queries.get(0).getScopes(), hasSize(20));
		assertThat(queries.get(1).getScopes(), hasSize(20));
		assertThat(queries.get(2).getScopes(), hasSize(5));
	}

	@Test
	public void testSplitTurnsABatchIntoOneQueryPerScope() {
		Query batch = new Query(
				Arrays.asList("user:alice", "repo:bob/tool"), "MERGED", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"carol"); //$NON-NLS-1$

		List<Query> parts = batch.split();

		assertThat(parts, hasSize(2));
		assertThat(parts.get(0).getText(),
				equalTo("is:pr user:alice author:carol is:merged")); //$NON-NLS-1$
		assertThat(parts.get(1).getText(),
				equalTo("is:pr repo:bob/tool author:carol is:merged")); //$NON-NLS-1$
	}

	@Test
	public void testSplittingASingleScopeQueryKeepsIt() {
		Query single = new Query(Arrays.asList("user:alice"), null, null); //$NON-NLS-1$

		assertThat(single.split(), contains(single));
	}

	@Test
	public void testRepositoriesCoveredByAnOwnerScopeAreNotSearchedTwice() {
		List<Query> queries = GitHubSearchQueries
				.accessiblePullRequestQueries("alice", //$NON-NLS-1$
						Arrays.asList("acme"), //$NON-NLS-1$
						Arrays.asList("alice/notes", "acme/app", //$NON-NLS-1$ //$NON-NLS-2$
								"bob/tool"), //$NON-NLS-1$
						null, null);

		assertThat(queries.get(0).getScopes(),
				contains("user:alice", "org:acme", "repo:bob/tool")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
