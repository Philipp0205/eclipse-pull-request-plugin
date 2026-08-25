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
package org.eclipse.egit.pullrequest.internal.bitbucket;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;

import org.eclipse.egit.pullrequest.internal.bitbucket.StubBitbucketServer.Response;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics.Outcome;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the connection handling and diagnostics of {@link BitbucketClient}
 * against a stub server.
 */
public class BitbucketConnectionTest {

	private static final String PROPERTIES_PATH = "/rest/api/1.0" //$NON-NLS-1$
			+ "/application-properties"; //$NON-NLS-1$

	private static final String WHOAMI_PATH = "/plugins/servlet/applinks/whoami"; //$NON-NLS-1$

	private static final String PROJECT_PATH = "/rest/api/1.0/projects/SOC"; //$NON-NLS-1$

	private static final String REPO_PATH = PROJECT_PATH
			+ "/repos/smartest"; //$NON-NLS-1$

	private StubBitbucketServer server;

	@Before
	public void startServer() throws IOException {
		server = new StubBitbucketServer();
	}

	@After
	public void stopServer() throws IOException {
		server.close();
	}

	private BitbucketClient client() {
		return new BitbucketClient(server.url(), "SOC", "smartest", //$NON-NLS-1$ //$NON-NLS-2$
				"secret-token"); //$NON-NLS-1$
	}

	private void serveApplicationProperties(String username) {
		Response response = new Response(200, "OK", //$NON-NLS-1$
				"{\"version\":\"9.4.2\",\"displayName\":\"Bitbucket\"}"); //$NON-NLS-1$
		if (username != null) {
			response.header("X-AUSERNAME", username); //$NON-NLS-1$
		}
		server.on(PROPERTIES_PATH, response);
	}

	@Test
	public void testCurrentUserComesFromUsernameHeader() throws IOException {
		serveApplicationProperties("john.doe"); //$NON-NLS-1$

		assertThat(client().getCurrentUser(), equalTo("john.doe")); //$NON-NLS-1$
		// Bitbucket Data Center has no /users/current resource
		assertThat(server.requestedPaths(),
				not(hasItem(containsString("users/current")))); //$NON-NLS-1$
	}

	@Test
	public void testCurrentUserIsUrlDecoded() throws IOException {
		serveApplicationProperties("john.doe%40example.com"); //$NON-NLS-1$

		assertThat(client().getCurrentUser(),
				equalTo("john.doe@example.com")); //$NON-NLS-1$
	}

	@Test
	public void testCurrentUserFallsBackToWhoami() throws IOException {
		serveApplicationProperties(null);
		server.on(WHOAMI_PATH, new Response(200, "OK", "jane.doe") //$NON-NLS-1$ //$NON-NLS-2$
				.contentType("text/plain")); //$NON-NLS-1$

		assertThat(client().getCurrentUser(), equalTo("jane.doe")); //$NON-NLS-1$
	}

	@Test
	public void testTokenIsSentAsBearer() throws IOException {
		serveApplicationProperties("john.doe"); //$NON-NLS-1$

		client().getCurrentUser();

		assertThat(server.authorizationHeaders(),
				hasItem(equalTo("Bearer secret-token"))); //$NON-NLS-1$
	}

	@Test
	public void testAnonymousResponseIsReportedAsFailure() {
		serveApplicationProperties(null);
		server.on(WHOAMI_PATH, new Response(200, "OK", "")); //$NON-NLS-1$ //$NON-NLS-2$

		try {
			client().getCurrentUser();
			throw new AssertionError("expected an IOException"); //$NON-NLS-1$
		} catch (IOException e) {
			assertThat(e.getMessage(),
					containsString("no authenticated user")); //$NON-NLS-1$
		}
	}

	@Test
	public void testUnauthorizedMentionsTheToken() {
		server.on(PROPERTIES_PATH, new Response(401, "Unauthorized", //$NON-NLS-1$
				"{\"errors\":[{\"message\":\"bad token\"}]}")); //$NON-NLS-1$

		try {
			client().getCurrentUser();
			throw new AssertionError("expected an IOException"); //$NON-NLS-1$
		} catch (IOException e) {
			assertThat(e.getMessage(), containsString("HTTP 401")); //$NON-NLS-1$
			assertThat(e.getMessage(),
					containsString("personal access token")); //$NON-NLS-1$
			assertThat(e.getMessage(), containsString("bad token")); //$NON-NLS-1$
		}
	}

	@Test
	public void testNotFoundMentionsProjectAndRepository() {
		serveApplicationProperties("john.doe"); //$NON-NLS-1$

		try {
			client().getPullRequests("OPEN", null, null, 25, 0); //$NON-NLS-1$
			throw new AssertionError("expected an IOException"); //$NON-NLS-1$
		} catch (IOException e) {
			assertThat(e.getMessage(), containsString("HTTP 404")); //$NON-NLS-1$
			assertThat(e.getMessage(), containsString("SOC")); //$NON-NLS-1$
			assertThat(e.getMessage(), containsString("smartest")); //$NON-NLS-1$
		}
	}

	@Test
	public void testHtmlResponseHintsAtWrongAddress() {
		server.on(PROPERTIES_PATH,
				new Response(404, "Not Found", "<html>Login</html>") //$NON-NLS-1$ //$NON-NLS-2$
						.contentType("text/html;charset=UTF-8")); //$NON-NLS-1$

		try {
			client().getCurrentUser();
			throw new AssertionError("expected an IOException"); //$NON-NLS-1$
		} catch (IOException e) {
			assertThat(e.getMessage(), containsString("HTML")); //$NON-NLS-1$
			assertThat(e.getMessage(), containsString("context path")); //$NON-NLS-1$
		}
	}

	@Test
	public void testGatewayErrorKeepsTheTextOfTheHtmlPage() {
		// What the Advantest gateway answers without a VPN connection
		server.on(PROPERTIES_PATH, new Response(503, "Service Unavailable", //$NON-NLS-1$
				"<html>\n  <head><title>503 Service Unavailable</title></head>" //$NON-NLS-1$
						+ "\n  <body><h1>Service unavailable</h1>" //$NON-NLS-1$
						+ "\n    <div>No server is available to handle this" //$NON-NLS-1$
						+ " request.</div>" //$NON-NLS-1$
						+ "\n    <div>You may want to activate your VPN to" //$NON-NLS-1$
						+ " avoid this error.</div>\n  </body>\n</html>") //$NON-NLS-1$
								.contentType("text/html")); //$NON-NLS-1$

		try {
			client().getCurrentUser();
			throw new AssertionError("expected an IOException"); //$NON-NLS-1$
		} catch (IOException e) {
			assertThat(e.getMessage(), containsString("HTTP 503")); //$NON-NLS-1$
			assertThat(e.getMessage(), containsString("VPN is not")); //$NON-NLS-1$
			assertThat(e.getMessage(),
					containsString("activate your VPN")); //$NON-NLS-1$
			assertThat(e.getMessage(), not(containsString("<div>"))); //$NON-NLS-1$
		}
	}

	@Test
	public void testDiagnosticsPassForAWorkingServer() {
		serveApplicationProperties("john.doe"); //$NON-NLS-1$
		server.on(PROJECT_PATH,
				new Response(200, "OK", "{\"key\":\"SOC\"}")); //$NON-NLS-1$ //$NON-NLS-2$
		server.on(REPO_PATH,
				new Response(200, "OK", "{\"slug\":\"smartest\"}")); //$NON-NLS-1$ //$NON-NLS-2$
		server.on(REPO_PATH + "/pull-requests", //$NON-NLS-1$
				new Response(200, "OK", "{\"size\":0,\"values\":[]}")); //$NON-NLS-1$ //$NON-NLS-2$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.toReport(), report.isSuccessful(), is(true));
		assertThat(step(report, "Authentication").getDetail(), //$NON-NLS-1$
				containsString("john.doe")); //$NON-NLS-1$
		assertThat(server.requestedPaths(),
				not(hasItem(containsString("/bitbucket")))); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsBlameAuthenticationWhenAnonymous() {
		serveApplicationProperties(null);
		server.on(WHOAMI_PATH, new Response(200, "OK", "")); //$NON-NLS-1$ //$NON-NLS-2$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.isSuccessful(), is(false));
		assertThat(step(report, "Authentication").getOutcome(), //$NON-NLS-1$
				equalTo(Outcome.FAILED));
		assertThat(step(report, "REST API").getOutcome(), //$NON-NLS-1$
				equalTo(Outcome.OK));
	}

	@Test
	public void testDiagnosticsBlameTheRepositoryWhenSlugIsWrong() {
		serveApplicationProperties("john.doe"); //$NON-NLS-1$
		server.on(PROJECT_PATH,
				new Response(200, "OK", "{\"key\":\"SOC\"}")); //$NON-NLS-1$ //$NON-NLS-2$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.isSuccessful(), is(false));
		assertThat(step(report, "Project SOC").getOutcome(), //$NON-NLS-1$
				equalTo(Outcome.OK));
		assertThat(step(report, "Repository smartest").getOutcome(), //$NON-NLS-1$
				equalTo(Outcome.FAILED));
	}

	@Test
	public void testDiagnosticsStopAtNameResolution() {
		BitbucketClient client = new BitbucketClient(
				"https://no-such-host.invalid", "SOC", "smartest", "token"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		ConnectionDiagnostics report = client.diagnoseConnection();

		assertThat(report.isSuccessful(), is(false));
		assertThat(step(report, "Name resolution").getOutcome(), //$NON-NLS-1$
				equalTo(Outcome.FAILED));
		assertThat(step(report, "Name resolution").getDetail(), //$NON-NLS-1$
				containsString("VPN")); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsRejectAMalformedUrl() {
		BitbucketClient client = new BitbucketClient("socgit.example.com", //$NON-NLS-1$
				"SOC", "smartest", "token"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		ConnectionDiagnostics report = client.diagnoseConnection();

		assertThat(report.isSuccessful(), is(false));
		assertThat(step(report, "Server URL").getDetail(), //$NON-NLS-1$
				containsString("https://")); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsFindsAContextPath() {
		server.on(PROPERTIES_PATH,
				new Response(404, "Not Found", "404 Not Found nginx") //$NON-NLS-1$ //$NON-NLS-2$
						.contentType("text/html")); //$NON-NLS-1$
		server.on("/bitbucket" + PROPERTIES_PATH, //$NON-NLS-1$
				new Response(200, "OK", //$NON-NLS-1$
						"{\"version\":\"9.4.2\",\"displayName\":\"Bitbucket\"}")); //$NON-NLS-1$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.isSuccessful(), is(false));
		assertThat(step(report, "REST API").getOutcome(), //$NON-NLS-1$
				equalTo(Outcome.FAILED));
		assertThat(step(report, "REST API").getDetail(), //$NON-NLS-1$
				containsString("/bitbucket")); //$NON-NLS-1$
		assertThat(step(report, "Context path").getOutcome(), //$NON-NLS-1$
				equalTo(Outcome.WARNING));
		assertThat(step(report, "Context path").getDetail(), //$NON-NLS-1$
				containsString(server.url() + "/bitbucket")); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsDoesNotProbeWhenUrlHasAPath() {
		server.on("/stash" + PROPERTIES_PATH, //$NON-NLS-1$
				new Response(200, "OK", "{\"version\":\"9.4.2\"}")); //$NON-NLS-1$ //$NON-NLS-2$

		BitbucketClient client = new BitbucketClient(server.url() + "/stash", //$NON-NLS-1$
				"SOC", "smartest", "secret-token"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		client.diagnoseConnection();

		assertThat(server.requestedPaths(),
				not(hasItem(containsString("/bitbucket")))); //$NON-NLS-1$
		assertThat(server.requestedPaths(),
				not(hasItem(containsString("/git/")))); //$NON-NLS-1$
	}

	@Test
	public void testDiagnosticsNeverLeakTheToken() {
		serveApplicationProperties("john.doe"); //$NON-NLS-1$

		String report = client().diagnoseConnection().toReport();

		assertThat(report, not(containsString("secret-token"))); //$NON-NLS-1$
		assertThat(report, containsString("12 characters")); //$NON-NLS-1$
	}

	private static Step step(ConnectionDiagnostics report, String name) {
		for (Step step : report.getSteps()) {
			if (step.getName().equals(name)) {
				return step;
			}
		}
		throw new AssertionError(
				"no step named '" + name + "' in\n" + report.toReport()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testStepsAreRecordedInOrder() {
		serveApplicationProperties("john.doe"); //$NON-NLS-1$

		ConnectionDiagnostics report = client().diagnoseConnection();

		assertThat(report.getSteps().get(0).getName(),
				equalTo("Configuration")); //$NON-NLS-1$
		assertThat(step(report, "TCP connection"), notNullValue()); //$NON-NLS-1$
	}
}
