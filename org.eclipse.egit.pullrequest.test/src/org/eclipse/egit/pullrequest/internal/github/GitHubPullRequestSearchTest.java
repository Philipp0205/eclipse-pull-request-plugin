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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.pullrequest.internal.github.GitHubPullRequestSearch.PageReader;
import org.eclipse.egit.pullrequest.internal.github.GitHubPullRequestSearch.Result;
import org.junit.Test;

/**
 * Tests for {@link GitHubPullRequestSearch}
 */
public class GitHubPullRequestSearchTest {

	private static final String VALIDATION_FAILED = "GitHub API request " //$NON-NLS-1$
			+ "failed: HTTP 422 - {\"message\":\"Validation Failed\"," //$NON-NLS-1$
			+ "\"errors\":[{\"message\":\"The listed users and " //$NON-NLS-1$
			+ "repositories cannot be searched either because the " //$NON-NLS-1$
			+ "resources do not exist or you do not have permission to " //$NON-NLS-1$
			+ "view them.\"}]}"; //$NON-NLS-1$

	private final List<String> requestedQueries = new ArrayList<>();

	/**
	 * A reader that answers each query from a canned list of paths and fails
	 * every query that was registered as rejected.
	 */
	private class StubReader implements PageReader {

		private final Map<String, List<String>> results = new HashMap<>();

		private final Map<String, IOException> failures = new HashMap<>();

		StubReader returning(String query, String... paths) {
			results.put(query, Arrays.asList(paths));
			return this;
		}

		StubReader failingWith(String query, String message) {
			failures.put(query, new IOException(message));
			return this;
		}

		@Override
		public List<String> read(String query, int page, int pageSize)
				throws IOException {
			requestedQueries.add(query);
			IOException failure = failures.get(query);
			if (failure != null) {
				throw failure;
			}
			if (page > 1) {
				return Collections.emptyList();
			}
			return results.getOrDefault(query, Collections.emptyList());
		}
	}

	@Test
	public void testRejectedScopeIsSkippedAndOthersStillReturnResults()
			throws Exception {
		StubReader reader = new StubReader()
				.returning("is:pr user:alice is:open", //$NON-NLS-1$
						"/repos/alice/notes/pulls/1") //$NON-NLS-1$
				.failingWith("is:pr org:acme is:open", VALIDATION_FAILED) //$NON-NLS-1$
				.returning("is:pr repo:bob/tool is:open", //$NON-NLS-1$
						"/repos/bob/tool/pulls/9"); //$NON-NLS-1$

		Result result = GitHubPullRequestSearch.run(
				Arrays.asList("is:pr user:alice is:open", //$NON-NLS-1$
						"is:pr org:acme is:open", //$NON-NLS-1$
						"is:pr repo:bob/tool is:open"), //$NON-NLS-1$
				1, 1, reader);

		assertThat(result.getPullRequestPaths(),
				contains("/repos/alice/notes/pulls/1", //$NON-NLS-1$
						"/repos/bob/tool/pulls/9")); //$NON-NLS-1$
		assertThat(result.getUnsearchableScopes(), contains("org:acme")); //$NON-NLS-1$
	}

	@Test
	public void testRejectedScopeDoesNotStopLaterQueries() throws Exception {
		StubReader reader = new StubReader()
				.failingWith("is:pr user:alice is:open", VALIDATION_FAILED) //$NON-NLS-1$
				.returning("is:pr org:acme is:open", //$NON-NLS-1$
						"/repos/acme/app/pulls/3"); //$NON-NLS-1$

		GitHubPullRequestSearch.run(
				Arrays.asList("is:pr user:alice is:open", //$NON-NLS-1$
						"is:pr org:acme is:open"), //$NON-NLS-1$
				1, 1, reader);

		assertThat(requestedQueries, hasSize(2));
	}

	@Test
	public void testAllScopesRejectedFailsWithActionableMessage() {
		StubReader reader = new StubReader()
				.failingWith("is:pr user:alice is:open", VALIDATION_FAILED) //$NON-NLS-1$
				.failingWith("is:pr org:acme is:open", VALIDATION_FAILED); //$NON-NLS-1$

		try {
			GitHubPullRequestSearch.run(
					Arrays.asList("is:pr user:alice is:open", //$NON-NLS-1$
							"is:pr org:acme is:open"), //$NON-NLS-1$
					1, 1, reader);
			fail("Expected the search to fail"); //$NON-NLS-1$
		} catch (IOException e) {
			assertThat(e.getMessage(), containsString("user:alice")); //$NON-NLS-1$
			assertThat(e.getMessage(), containsString("org:acme")); //$NON-NLS-1$
			assertThat(e.getMessage(), containsString("read:org")); //$NON-NLS-1$
			assertThat(e.getCause().getMessage(),
					containsString("HTTP 422")); //$NON-NLS-1$
		}
	}

	@Test
	public void testFailuresOtherThanRejectedScopesArePropagated() {
		StubReader reader = new StubReader()
				.returning("is:pr user:alice is:open", //$NON-NLS-1$
						"/repos/alice/notes/pulls/1") //$NON-NLS-1$
				.failingWith("is:pr org:acme is:open", //$NON-NLS-1$
						"GitHub API request failed: HTTP 503 - unavailable"); //$NON-NLS-1$

		try {
			GitHubPullRequestSearch.run(
					Arrays.asList("is:pr user:alice is:open", //$NON-NLS-1$
							"is:pr org:acme is:open"), //$NON-NLS-1$
					1, 1, reader);
			fail("Expected the search to fail"); //$NON-NLS-1$
		} catch (IOException e) {
			assertThat(e.getMessage(), containsString("HTTP 503")); //$NON-NLS-1$
		}
	}

	@Test
	public void testPullRequestFoundByTwoScopesIsReportedOnce()
			throws Exception {
		StubReader reader = new StubReader()
				.returning("is:pr user:alice is:open", //$NON-NLS-1$
						"/repos/alice/notes/pulls/1") //$NON-NLS-1$
				.returning("is:pr repo:alice/notes is:open", //$NON-NLS-1$
						"/repos/alice/notes/pulls/1"); //$NON-NLS-1$

		Result result = GitHubPullRequestSearch.run(
				Arrays.asList("is:pr user:alice is:open", //$NON-NLS-1$
						"is:pr repo:alice/notes is:open"), //$NON-NLS-1$
				1, 1, reader);

		assertThat(result.getPullRequestPaths(),
				contains("/repos/alice/notes/pulls/1")); //$NON-NLS-1$
	}

	@Test
	public void testSuccessfulSearchReportsNoUnsearchableScopes()
			throws Exception {
		StubReader reader = new StubReader().returning(
				"is:pr user:alice is:open", "/repos/alice/notes/pulls/1"); //$NON-NLS-1$ //$NON-NLS-2$

		Result result = GitHubPullRequestSearch.run(
				Arrays.asList("is:pr user:alice is:open"), 1, 1, reader); //$NON-NLS-1$

		assertThat(result.getUnsearchableScopes(), empty());
	}

	@Test
	public void testValidationFailureIsRecognizedAsUnsearchableScope() {
		assertThat(
				GitHubPullRequestSearch.isUnsearchableScope(
						new IOException(VALIDATION_FAILED)),
				equalTo(true));
		assertThat(
				GitHubPullRequestSearch.isUnsearchableScope(new IOException(
						"GitHub API request failed: HTTP 401 - Bad token")), //$NON-NLS-1$
				equalTo(false));
		assertThat(GitHubPullRequestSearch
				.isUnsearchableScope(new IOException()), equalTo(false));
	}

	@Test
	public void testScopeOfExtractsTheOwnerQualifier() {
		assertThat(GitHubSearchQueries.scopeOf("is:pr user:alice is:open"), //$NON-NLS-1$
				equalTo("user:alice")); //$NON-NLS-1$
		assertThat(
				GitHubSearchQueries.scopeOf(
						"is:pr org:acme author:carol is:merged"), //$NON-NLS-1$
				equalTo("org:acme")); //$NON-NLS-1$
		assertThat(GitHubSearchQueries
				.scopeOf("is:pr repo:bob/tool is:closed is:unmerged"), //$NON-NLS-1$
				equalTo("repo:bob/tool")); //$NON-NLS-1$
		assertThat(GitHubSearchQueries.scopeOf("is:pr"), equalTo("is:pr")); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
