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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
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
 * Tests how {@link GitHubClient} lists pull requests across every accessible
 * owner scope when GitHub refuses to search some of them.
 */
public class GitHubConnectionTest {

	private static final String VALIDATION_FAILED = "{\"message\":" //$NON-NLS-1$
			+ "\"Validation Failed\",\"errors\":[{\"message\":\"The listed " //$NON-NLS-1$
			+ "users and repositories cannot be searched either because " //$NON-NLS-1$
			+ "the resources do not exist or you do not have permission to " //$NON-NLS-1$
			+ "view them.\",\"resource\":\"Search\",\"field\":\"q\"," //$NON-NLS-1$
			+ "\"code\":\"invalid\"}],\"status\":\"422\"}"; //$NON-NLS-1$

	private StubGitHubServer server;

	@Before
	public void startServer() throws IOException {
		server = new StubGitHubServer();
		server.on("/user", ok("{\"login\":\"Philipp0205\"}")); //$NON-NLS-1$ //$NON-NLS-2$
		server.on("/user/orgs", ok("[{\"login\":\"acme\"}]")); //$NON-NLS-1$ //$NON-NLS-2$
		server.on("/user/repos", ok("[]")); //$NON-NLS-1$ //$NON-NLS-2$
		server.on("/repos/Philipp0205/notes/pulls/1", //$NON-NLS-1$
				ok(pullRequest(1, "Fix the parser"))); //$NON-NLS-1$
		server.on("/repos/acme/app/pulls/4", //$NON-NLS-1$
				ok(pullRequest(4, "Bump the target platform"))); //$NON-NLS-1$
	}

	@After
	public void stopServer() throws IOException {
		server.close();
	}

	@Test
	public void testScopeGitHubRefusesToSearchIsSkipped() throws Exception {
		searchReturns("user%3APhilipp0205", //$NON-NLS-1$
				"/repos/Philipp0205/notes/pulls/1"); //$NON-NLS-1$
		searchFailsWithValidationError("org%3Aacme"); //$NON-NLS-1$

		List<PullRequest> pullRequests = client()
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(1));
		assertThat(pullRequests.get(0).getTitle(),
				equalTo("Fix the parser")); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsReportSkippedScopeAsWarningNotFailure()
			throws Exception {
		searchReturns("user%3APhilipp0205", //$NON-NLS-1$
				"/repos/Philipp0205/notes/pulls/1"); //$NON-NLS-1$
		searchFailsWithValidationError("org%3Aacme"); //$NON-NLS-1$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.toReport(), report.isSuccessful(), is(true));
		assertThat(step(report, "Read pull requests").getDetail(), //$NON-NLS-1$
				equalTo("Returned 1 pull request(s)")); //$NON-NLS-1$
		Step scopes = step(report, "Search scopes"); //$NON-NLS-1$
		assertThat(scopes.getOutcome(), equalTo(Outcome.WARNING));
		assertThat(scopes.getDetail(), containsString("org:acme")); //$NON-NLS-1$
		assertThat(scopes.getDetail(), containsString("read:org")); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsStaySilentWhenEveryScopeIsSearchable()
			throws Exception {
		searchReturns("user%3APhilipp0205", //$NON-NLS-1$
				"/repos/Philipp0205/notes/pulls/1"); //$NON-NLS-1$
		searchReturns("org%3Aacme", "/repos/acme/app/pulls/4"); //$NON-NLS-1$ //$NON-NLS-2$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.toReport(), report.isSuccessful(), is(true));
		assertThat(report.toReport(), not(containsString("Search scopes"))); //$NON-NLS-1$
	}

	@Test
	public void testEveryScopeRefusedIsReportedWithTokenAdvice() {
		searchFailsWithValidationError("user%3APhilipp0205"); //$NON-NLS-1$
		searchFailsWithValidationError("org%3Aacme"); //$NON-NLS-1$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.isSuccessful(), is(false));
		Step read = step(report, "Read pull requests"); //$NON-NLS-1$
		assertThat(read.getOutcome(), equalTo(Outcome.FAILED));
		assertThat(read.getDetail(), containsString("user:Philipp0205")); //$NON-NLS-1$
		assertThat(read.getDetail(), containsString("org:acme")); //$NON-NLS-1$
		assertThat(read.getDetail(), containsString("read:org")); //$NON-NLS-1$
	}

	@Test
	public void testOrganizationsThatCannotBeListedDoNotHideOwnPullRequests()
			throws Exception {
		server.on("/user/orgs", new Response(403, "Forbidden", //$NON-NLS-1$ //$NON-NLS-2$
				"{\"message\":\"Resource not accessible by integration\"}")); //$NON-NLS-1$
		searchReturns("user%3APhilipp0205", //$NON-NLS-1$
				"/repos/Philipp0205/notes/pulls/1"); //$NON-NLS-1$

		List<PullRequest> pullRequests = client()
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(1));
		assertThat(pullRequests.get(0).getTitle(),
				equalTo("Fix the parser")); //$NON-NLS-1$
	}

	@Test
	public void testSearchFailuresOtherThanRefusedScopesAreReported() {
		searchReturns("user%3APhilipp0205", //$NON-NLS-1$
				"/repos/Philipp0205/notes/pulls/1"); //$NON-NLS-1$
		server.onQueryContaining("org%3Aacme", //$NON-NLS-1$
				new Response(503, "Service Unavailable", //$NON-NLS-1$
						"{\"message\":\"No server is currently available\"}")); //$NON-NLS-1$

		ConnectionDiagnostics report = client().diagnoseConnection();

		Step read = step(report, "Read pull requests"); //$NON-NLS-1$
		assertThat(read.getOutcome(), equalTo(Outcome.FAILED));
		assertThat(read.getDetail(), containsString("HTTP 503")); //$NON-NLS-1$
	}

	@Test
	public void testEveryOwnerScopeIsSearchedSeparately() throws Exception {
		server.on("/user/repos", //$NON-NLS-1$
				ok("[{\"full_name\":\"bob/tool\"}]")); //$NON-NLS-1$
		searchReturns("user%3APhilipp0205", //$NON-NLS-1$
				"/repos/Philipp0205/notes/pulls/1"); //$NON-NLS-1$
		searchReturns("org%3Aacme", "/repos/acme/app/pulls/4"); //$NON-NLS-1$ //$NON-NLS-2$
		searchFailsWithValidationError("repo%3Abob%2Ftool"); //$NON-NLS-1$

		List<PullRequest> pullRequests = client()
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(2));
		assertThat(searchTargets(), hasSize(3));
	}

	@Test
	public void testPullRequestFoundInTwoScopesIsListedOnce()
			throws Exception {
		server.on("/user/repos", //$NON-NLS-1$
				ok("[{\"full_name\":\"acme/app\"}]")); //$NON-NLS-1$
		searchReturns("user%3APhilipp0205", //$NON-NLS-1$
				"/repos/acme/app/pulls/4"); //$NON-NLS-1$
		searchReturns("org%3Aacme", "/repos/acme/app/pulls/4"); //$NON-NLS-1$ //$NON-NLS-2$

		List<PullRequest> pullRequests = client()
				.getPullRequests(null, null, null, 25, 0);

		assertThat(pullRequests, hasSize(1));
		assertThat(pullRequests.get(0).getTitle(),
				equalTo("Bump the target platform")); //$NON-NLS-1$
	}

	private GitHubClient client() {
		return new GitHubClient(null, null, "token", server.url()); //$NON-NLS-1$
	}

	private void searchReturns(String encodedScope, String... pullPaths) {
		StringBuilder items = new StringBuilder();
		for (String pullPath : pullPaths) {
			if (items.length() > 0) {
				items.append(',');
			}
			items.append("{\"pull_request\":{\"url\":\"") //$NON-NLS-1$
					.append(server.url()).append(pullPath).append("\"}}"); //$NON-NLS-1$
		}
		server.onQueryContaining(encodedScope,
				ok("{\"total_count\":" + pullPaths.length + ",\"items\":[" //$NON-NLS-1$ //$NON-NLS-2$
						+ items + "]}")); //$NON-NLS-1$
	}

	private void searchFailsWithValidationError(String encodedScope) {
		server.onQueryContaining(encodedScope, new Response(422,
				"Unprocessable Entity", VALIDATION_FAILED)); //$NON-NLS-1$
	}

	private List<String> searchTargets() {
		return server.requestedTargets().stream()
				.filter(target -> target.startsWith("/search/issues")) //$NON-NLS-1$
				.toList();
	}

	private static Response ok(String body) {
		return new Response(200, "OK", body); //$NON-NLS-1$
	}

	private static String pullRequest(int number, String title) {
		return "{\"number\":" + number + ",\"title\":\"" + title //$NON-NLS-1$ //$NON-NLS-2$
				+ "\",\"state\":\"open\",\"body\":\"\"," //$NON-NLS-1$
				+ "\"created_at\":\"2026-09-01T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-09-0" + number //$NON-NLS-1$
				+ "T10:00:00Z\",\"user\":{\"login\":\"Philipp0205\"}}"; //$NON-NLS-1$
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
