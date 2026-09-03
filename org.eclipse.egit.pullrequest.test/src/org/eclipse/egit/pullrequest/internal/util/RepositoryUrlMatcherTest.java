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

import java.util.Arrays;

import org.eclipse.egit.pullrequest.internal.util.RepositoryUrlMatcher.MatchQuality;
import org.junit.Test;

/**
 * Tests matching of Git remote URLs against a hosted repository identity.
 */
public class RepositoryUrlMatcherTest {

	private static final String SERVER = "https://server.example.com/bitbucket"; //$NON-NLS-1$

	private static RepositoryUrlMatcher bitbucket() {
		return RepositoryUrlMatcher.create("SOC", "smartest", //$NON-NLS-1$ //$NON-NLS-2$
				SERVER + "/scm/soc/smartest.git", SERVER); //$NON-NLS-1$
	}

	@Test
	public void testHttpsCloneUrlWithContextPath() {
		assertThat(bitbucket().match(
				"https://server.example.com/bitbucket/scm/SOC/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testHttpsCloneUrlWithUserInfoAndWithoutGitSuffix() {
		assertThat(bitbucket().match(
				"https://jdoe@server.example.com/bitbucket/scm/soc/smartest"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testSshCloneUrlWithPort() {
		// Bitbucket SSH URLs carry neither the context path nor the /scm/
		// segment, which used to prevent any match.
		assertThat(bitbucket().match(
				"ssh://git@server.example.com:7999/SOC/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testScpStyleCloneUrl() {
		assertThat(bitbucket()
				.match("git@server.example.com:SOC/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testShortHostNameIsAlias() {
		assertThat(bitbucket().match("ssh://git@server:7999/SOC/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.HOST_ALIAS));
	}

	@Test
	public void testDedicatedGitHostInSameDomainIsAlias() {
		assertThat(bitbucket()
				.match("ssh://git@git.example.com/SOC/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.HOST_ALIAS));
	}

	@Test
	public void testLocalMirrorMatchesPathOnly() {
		assertThat(bitbucket().match("/var/mirrors/soc/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.PATH_ONLY));
	}

	@Test
	public void testSameNameInOtherProjectMatchesSlugOnly() {
		assertThat(bitbucket().match(
				"https://server.example.com/bitbucket/scm/zenith/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.SLUG_ONLY));
	}

	@Test
	public void testOtherRepositoryDoesNotMatch() {
		assertThat(bitbucket().match(
				"https://server.example.com/bitbucket/scm/SOC/other.git"), //$NON-NLS-1$
				equalTo(MatchQuality.NONE));
	}

	@Test
	public void testRepositoryNameIsNotMatchedAsSubstring() {
		assertThat(bitbucket().match(
				"https://server.example.com/bitbucket/scm/SOC/smartest-doc.git"), //$NON-NLS-1$
				equalTo(MatchQuality.NONE));
	}

	@Test
	public void testBareServerHostWithoutSchemeIsAccepted() {
		RepositoryUrlMatcher matcher = RepositoryUrlMatcher.create("SOC", //$NON-NLS-1$
				"smartest", "server.example.com/bitbucket"); //$NON-NLS-1$ //$NON-NLS-2$
		assertThat(matcher.match(
				"ssh://git@server.example.com:7999/soc/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testPersonalForkProject() {
		RepositoryUrlMatcher matcher = RepositoryUrlMatcher.create("~JDOE", //$NON-NLS-1$
				"smartest", SERVER); //$NON-NLS-1$
		assertThat(matcher.match(
				"ssh://git@server.example.com:7999/~jdoe/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testGitHubUrls() {
		RepositoryUrlMatcher matcher = RepositoryUrlMatcher.create(
				"Philipp0205", "eclipse-pullrequest-plugin", //$NON-NLS-1$ //$NON-NLS-2$
				"https://github.com/Philipp0205/eclipse-pullrequest-plugin.git", //$NON-NLS-1$
				"github.com"); //$NON-NLS-1$
		assertThat(matcher.match(
				"git@github.com:philipp0205/eclipse-pullrequest-plugin.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testServerUrlWithContextPathAndSshRemote() {
		// The reported case: the server URL preference carries the Bitbucket
		// context path while the clone uses SSH, so neither the context path
		// nor the /scm/ segment appears in the remote URL.
		RepositoryUrlMatcher matcher = RepositoryUrlMatcher.create("SOC", //$NON-NLS-1$
				"smartest", null, //$NON-NLS-1$
				"https://socgit.example.com/bitbucket"); //$NON-NLS-1$
		assertThat(matcher.match(
				"ssh://git@socgit.example.com:7999/soc/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
		assertThat(matcher.match(
				"https://socgit.example.com/bitbucket/scm/SOC/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testMatchBestPicksHighestQuality() {
		assertThat(bitbucket().matchBest(Arrays.asList(
				"https://server.example.com/bitbucket/scm/other/thing.git", //$NON-NLS-1$
				"ssh://git@server.example.com:7999/SOC/smartest.git")), //$NON-NLS-1$
				equalTo(MatchQuality.EXACT));
	}

	@Test
	public void testUnknownProjectStillMatchesOnSlug() {
		RepositoryUrlMatcher matcher = RepositoryUrlMatcher.create(null,
				"smartest", SERVER); //$NON-NLS-1$
		assertThat(matcher.match(
				"ssh://git@server.example.com:7999/SOC/smartest.git"), //$NON-NLS-1$
				equalTo(MatchQuality.SLUG_ONLY));
	}

	@Test
	public void testMissingSlugYieldsNoMatcher() {
		assertThat(RepositoryUrlMatcher.create("SOC", null, SERVER), //$NON-NLS-1$
				nullValue());
	}

	@Test
	public void testNullAndGarbageUrlsAreSafe() {
		RepositoryUrlMatcher matcher = bitbucket();
		assertThat(matcher, notNullValue());
		assertThat(matcher.match(null), equalTo(MatchQuality.NONE));
		assertThat(matcher.match(""), equalTo(MatchQuality.NONE)); //$NON-NLS-1$
		assertThat(matcher.match("not a url at all"), //$NON-NLS-1$
				equalTo(MatchQuality.NONE));
		assertThat(matcher.matchBest(null), equalTo(MatchQuality.NONE));
	}

	@Test
	public void testExtractHost() {
		assertThat(RepositoryUrlMatcher
				.extractHost("ssh://git@server.example.com:7999/a/b.git"), //$NON-NLS-1$
				equalTo("server.example.com")); //$NON-NLS-1$
		assertThat(RepositoryUrlMatcher
				.extractHost("HTTPS://Server.Example.COM/bitbucket"), //$NON-NLS-1$
				equalTo("server.example.com")); //$NON-NLS-1$
		assertThat(RepositoryUrlMatcher.extractHost("/var/mirrors/a/b.git"), //$NON-NLS-1$
				nullValue());
	}

	@Test
	public void testPathSegments() {
		assertThat(RepositoryUrlMatcher
				.pathSegments("ssh://git@server:7999/SOC/smartest.git"), //$NON-NLS-1$
				equalTo(Arrays.asList("soc", "smartest"))); //$NON-NLS-1$ //$NON-NLS-2$
		assertThat(RepositoryUrlMatcher.pathSegments(
				"https://server.example.com/bitbucket/scm/SOC/smartest/"), //$NON-NLS-1$
				equalTo(Arrays.asList("bitbucket", "scm", "soc", "smartest"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertThat(RepositoryUrlMatcher
				.pathSegments("git@server.example.com:SOC/smartest.git"), //$NON-NLS-1$
				equalTo(Arrays.asList("soc", "smartest"))); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
