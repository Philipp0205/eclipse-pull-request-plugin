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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics.Outcome;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics.Step;
import org.eclipse.egit.pullrequest.internal.github.StubGitHubServer.Response;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests how {@link GitHubClient} lists pull requests across the scopes it is
 * allowed to search, and how few requests that takes.
 */
public class GitHubConnectionTest {

	private static final String VALIDATION_FAILED = "{\"message\":" //$NON-NLS-1$
			+ "\"Validation Failed\",\"errors\":[{\"message\":\"The listed " //$NON-NLS-1$
			+ "users and repositories cannot be searched either because " //$NON-NLS-1$
			+ "the resources do not exist or you do not have permission to " //$NON-NLS-1$
			+ "view them.\",\"resource\":\"Search\",\"field\":\"q\"," //$NON-NLS-1$
			+ "\"code\":\"invalid\"}],\"status\":\"422\"}"; //$NON-NLS-1$

	private static final String RATE_LIMITED = "{\"message\":\"API rate " //$NON-NLS-1$
			+ "limit exceeded for user ID 201185819.\",\"status\":\"403\"}"; //$NON-NLS-1$

	private StubGitHubServer server;

	@Before
	public void startServer() throws IOException {
		server = new StubGitHubServer();
		server.on("/user", ok("{\"login\":\"philipp-kurrle_ADVNTST\"}")); //$NON-NLS-1$ //$NON-NLS-2$
		server.on("/user/orgs", ok("[{\"login\":\"acme\"}]")); //$NON-NLS-1$ //$NON-NLS-2$
		server.on("/user/repos", ok("[]")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@After
	public void stopServer() throws IOException {
		server.close();
	}

	@Test
	public void testConfiguredScopesReplaceTheDiscoveredOnes()
			throws Exception {
		servePullRequest("/repos/Philipp0205/notes/pulls/1", //$NON-NLS-1$
				"Fix the parser"); //$NON-NLS-1$
		server.onSearch("is:pr user:Philipp0205 is:open", //$NON-NLS-1$
				searchResult("/repos/Philipp0205/notes/pulls/1")); //$NON-NLS-1$

		List<PullRequest> pullRequests = client("Philipp0205") //$NON-NLS-1$
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(1));
		assertThat(pullRequests.get(0).getTitle(),
				equalTo("Fix the parser")); //$NON-NLS-1$
		assertThat(server.searchQueries(),
				contains("is:pr user:Philipp0205 is:open")); //$NON-NLS-1$
		assertThat(server.requestedPaths(),
				not(hasItem("/user/orgs"))); //$NON-NLS-1$
		assertThat(server.requestedPaths(), not(hasItem("/user"))); //$NON-NLS-1$
	}

	@Test
	public void testConfiguredScopesAcceptOwnersAndSingleRepositories()
			throws Exception {
		client("Philipp0205, acme, someone/their-repo") //$NON-NLS-1$
				.getPullRequests(null, null, null, 25, 0);

		assertThat(server.searchQueries(),
				contains("is:pr user:Philipp0205 user:acme " //$NON-NLS-1$
						+ "repo:someone/their-repo is:open")); //$NON-NLS-1$
	}

	@Test
	public void testOnlyTheRequestedPageIsReadInFull() throws Exception {
		List<String> found = new ArrayList<>();
		for (int number = 1; number <= 30; number++) {
			String path = "/repos/acme/app/pulls/" + number; //$NON-NLS-1$
			found.add(path);
			servePullRequest(path, "Change " + number); //$NON-NLS-1$
		}
		server.onSearch("is:pr user:acme is:open", //$NON-NLS-1$
				searchResult(found.toArray(new String[0])));

		List<PullRequest> pullRequests = client("acme") //$NON-NLS-1$
				.getPullRequests(null, null, null, 5, 0);

		assertThat(pullRequests, hasSize(5));
		assertThat(detailRequests(), hasSize(5));
	}

	@Test
	public void testManyRepositoriesAreSearchedInBatchedRequests()
			throws Exception {
		StringBuilder repositories = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			repositories.append("owner").append(i).append("/repo "); //$NON-NLS-1$ //$NON-NLS-2$
		}

		client(repositories.toString()).getPullRequests(null, null, null, 25,
				0);

		assertThat(server.searchQueries(), hasSize(3));
	}

	/**
	 * A token that reaches many repositories used to cost one search per
	 * repository plus one request per pull request found anywhere, which is
	 * what exhausted the API rate limit.
	 *
	 * @throws Exception
	 *             if the listing fails
	 */
	@Test
	public void testListingManyRepositoriesCostsFewRequests()
			throws Exception {
		StringBuilder repositories = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			repositories.append("owner").append(i).append("/repo "); //$NON-NLS-1$ //$NON-NLS-2$
		}
		List<String> found = new ArrayList<>();
		for (int number = 1; number <= 600; number++) {
			String path = "/repos/owner0/repo/pulls/" + number; //$NON-NLS-1$
			found.add(path);
			servePullRequest(path, "Change " + number); //$NON-NLS-1$
		}
		server.onAnySearch(searchResult(found.toArray(new String[0])));

		List<PullRequest> pullRequests = client(repositories.toString())
				.getPullRequests(null, null, null, 100, 0);

		assertThat(pullRequests, hasSize(100));
		assertThat(server.searchQueries(), hasSize(3));
		assertThat(detailRequests(), hasSize(100));
		assertThat(server.requestedPaths(), hasSize(103));
	}

	@Test
	public void testScopeGitHubRefusesToSearchIsSkipped() throws Exception {
		servePullRequest("/repos/philipp-kurrle_ADVNTST/notes/pulls/1", //$NON-NLS-1$
				"Fix the parser"); //$NON-NLS-1$
		server.onSearch(
				"is:pr user:philipp-kurrle_ADVNTST org:acme is:open", //$NON-NLS-1$
				refused());
		server.onSearch("is:pr user:philipp-kurrle_ADVNTST is:open", //$NON-NLS-1$
				searchResult(
						"/repos/philipp-kurrle_ADVNTST/notes/pulls/1")); //$NON-NLS-1$
		server.onSearch("is:pr org:acme is:open", refused()); //$NON-NLS-1$

		List<PullRequest> pullRequests = client(null)
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(1));
		assertThat(pullRequests.get(0).getTitle(),
				equalTo("Fix the parser")); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsReportSkippedScopeAsWarningNotFailure()
			throws Exception {
		servePullRequest("/repos/philipp-kurrle_ADVNTST/notes/pulls/1", //$NON-NLS-1$
				"Fix the parser"); //$NON-NLS-1$
		server.onSearch(
				"is:pr user:philipp-kurrle_ADVNTST org:acme is:open", //$NON-NLS-1$
				refused());
		server.onSearch("is:pr user:philipp-kurrle_ADVNTST is:open", //$NON-NLS-1$
				searchResult(
						"/repos/philipp-kurrle_ADVNTST/notes/pulls/1")); //$NON-NLS-1$
		server.onSearch("is:pr org:acme is:open", refused()); //$NON-NLS-1$

		ConnectionDiagnostics report = client(null).diagnoseConnection();

		assertThat(report.toReport(), report.isSuccessful(), is(true));
		assertThat(step(report, "Authentication").getDetail(), //$NON-NLS-1$
				equalTo("Authenticated as philipp-kurrle_ADVNTST")); //$NON-NLS-1$
		assertThat(step(report, "Read pull requests").getDetail(), //$NON-NLS-1$
				equalTo("Returned 1 pull request(s)")); //$NON-NLS-1$
		Step scopes = step(report, "Search scopes"); //$NON-NLS-1$
		assertThat(scopes.getOutcome(), equalTo(Outcome.WARNING));
		assertThat(scopes.getDetail(), containsString("org:acme")); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsStaySilentWhenEveryScopeIsSearchable()
			throws Exception {
		servePullRequest("/repos/philipp-kurrle_ADVNTST/notes/pulls/1", //$NON-NLS-1$
				"Fix the parser"); //$NON-NLS-1$
		server.onSearch(
				"is:pr user:philipp-kurrle_ADVNTST org:acme is:open", //$NON-NLS-1$
				searchResult(
						"/repos/philipp-kurrle_ADVNTST/notes/pulls/1")); //$NON-NLS-1$

		ConnectionDiagnostics report = client(null).diagnoseConnection();

		assertThat(report.toReport(), report.isSuccessful(), is(true));
		assertThat(report.toReport(), not(containsString("Search scopes"))); //$NON-NLS-1$
	}

	@Test
	public void testEveryScopeRefusedIsReportedWithTokenAdvice() {
		server.onSearch("is:pr user:alice user:bob is:open", refused()); //$NON-NLS-1$
		server.onSearch("is:pr user:alice is:open", refused()); //$NON-NLS-1$
		server.onSearch("is:pr user:bob is:open", refused()); //$NON-NLS-1$

		ConnectionDiagnostics report = client("alice, bob") //$NON-NLS-1$
				.diagnoseConnection();

		assertThat(report.isSuccessful(), is(false));
		Step read = step(report, "Read pull requests"); //$NON-NLS-1$
		assertThat(read.getOutcome(), equalTo(Outcome.FAILED));
		assertThat(read.getDetail(), containsString("user:alice")); //$NON-NLS-1$
		assertThat(read.getDetail(), containsString("user:bob")); //$NON-NLS-1$
		assertThat(read.getDetail(), containsString("read:org")); //$NON-NLS-1$
	}

	@Test
	public void testRateLimitIsReportedWithAdviceInsteadOfRawJson() {
		server.onSearch("is:pr user:alice is:open", //$NON-NLS-1$
				new Response(403, "Forbidden", RATE_LIMITED)); //$NON-NLS-1$

		ConnectionDiagnostics report = client("alice").diagnoseConnection(); //$NON-NLS-1$

		Step read = step(report, "Read pull requests"); //$NON-NLS-1$
		assertThat(read.getOutcome(), equalTo(Outcome.FAILED));
		assertThat(read.getDetail(),
				containsString("rate limit for this access token")); //$NON-NLS-1$
		assertThat(read.getDetail(), containsString("preference page")); //$NON-NLS-1$
	}

	@Test
	public void testOrganizationsThatCannotBeListedDoNotHideOwnPullRequests()
			throws Exception {
		server.on("/user/orgs", new Response(403, "Forbidden", //$NON-NLS-1$ //$NON-NLS-2$
				"{\"message\":\"Resource not accessible by integration\"}")); //$NON-NLS-1$
		servePullRequest("/repos/philipp-kurrle_ADVNTST/notes/pulls/1", //$NON-NLS-1$
				"Fix the parser"); //$NON-NLS-1$
		server.onSearch("is:pr user:philipp-kurrle_ADVNTST is:open", //$NON-NLS-1$
				searchResult(
						"/repos/philipp-kurrle_ADVNTST/notes/pulls/1")); //$NON-NLS-1$

		List<PullRequest> pullRequests = client(null)
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(1));
		assertThat(pullRequests.get(0).getTitle(),
				equalTo("Fix the parser")); //$NON-NLS-1$
	}

	@Test
	public void testSearchFailuresOtherThanRefusedScopesAreReported() {
		server.onSearch("is:pr user:alice is:open", //$NON-NLS-1$
				new Response(503, "Service Unavailable", //$NON-NLS-1$
						"{\"message\":\"No server is available\"}")); //$NON-NLS-1$

		ConnectionDiagnostics report = client("alice").diagnoseConnection(); //$NON-NLS-1$

		Step read = step(report, "Read pull requests"); //$NON-NLS-1$
		assertThat(read.getOutcome(), equalTo(Outcome.FAILED));
		assertThat(read.getDetail(), containsString("HTTP 503")); //$NON-NLS-1$
	}

	@Test
	public void testPullRequestFoundInTwoScopesIsListedOnce()
			throws Exception {
		servePullRequest("/repos/acme/app/pulls/4", //$NON-NLS-1$
				"Bump the target platform"); //$NON-NLS-1$
		server.onSearch("is:pr user:acme repo:acme/app is:open", //$NON-NLS-1$
				searchResult("/repos/acme/app/pulls/4", //$NON-NLS-1$
						"/repos/acme/app/pulls/4")); //$NON-NLS-1$

		List<PullRequest> pullRequests = client("acme, acme/app") //$NON-NLS-1$
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(1));
		assertThat(detailRequests(), hasSize(1));
	}

	private GitHubClient client(String searchScopes) {
		return new GitHubClient(null, null, "token", server.url(), //$NON-NLS-1$
				GitHubSearchQueries.parseScopes(searchScopes));
	}

	private List<String> detailRequests() {
		return server.requestedPaths().stream()
				.filter(path -> path.contains("/pulls/")) //$NON-NLS-1$
				.toList();
	}

	private void servePullRequest(String path, String title) {
		int number = Integer
				.parseInt(path.substring(path.lastIndexOf('/') + 1));
		server.on(path, ok("{\"number\":" + number + ",\"title\":\"" + title //$NON-NLS-1$ //$NON-NLS-2$
				+ "\",\"state\":\"open\",\"body\":\"\"," //$NON-NLS-1$
				+ "\"created_at\":\"2026-09-01T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-09-01T10:00:00Z\"," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"Philipp0205\"}}")); //$NON-NLS-1$
	}

	private Response searchResult(String... pullPaths) {
		StringBuilder items = new StringBuilder();
		int index = 0;
		for (String pullPath : pullPaths) {
			if (items.length() > 0) {
				items.append(',');
			}
			items.append("{\"updated_at\":\"").append(timestamp(index)) //$NON-NLS-1$
					.append("\",\"pull_request\":{\"url\":\"") //$NON-NLS-1$
					.append(server.url()).append(pullPath).append("\"}}"); //$NON-NLS-1$
			index++;
		}
		return ok("{\"total_count\":" + pullPaths.length + ",\"items\":[" //$NON-NLS-1$ //$NON-NLS-2$
				+ items + "]}"); //$NON-NLS-1$
	}

	/**
	 * Builds an update timestamp that decreases with the index, so that
	 * search results come back most recently updated first.
	 *
	 * @param index
	 *            position in the result list
	 * @return an ISO 8601 timestamp
	 */
	private static String timestamp(int index) {
		return DateTimeFormatter.ISO_INSTANT.format(
				Instant.parse("2026-09-01T00:00:00Z") //$NON-NLS-1$
						.minusSeconds(index * 60L));
	}

	private static Response refused() {
		return new Response(422, "Unprocessable Entity", VALIDATION_FAILED); //$NON-NLS-1$
	}

	private static Response ok(String body) {
		return new Response(200, "OK", body); //$NON-NLS-1$
	}

	private static Step step(ConnectionDiagnostics report, String name) {
		for (Step step : report.getSteps()) {
			if (step.getName().equals(name)) {
				return step;
			}
		}
		throw new AssertionError(
				"No step " + name + " in\n" + report.toReport()); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
