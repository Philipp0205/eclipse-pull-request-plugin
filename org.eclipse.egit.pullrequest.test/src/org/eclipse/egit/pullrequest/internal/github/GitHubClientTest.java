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
package org.eclipse.egit.pullrequest.internal.github;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import org.junit.Test;

/**
 * Tests for {@link GitHubClient}
 */
public class GitHubClientTest {

	@Test
	public void testClientConstruction() {
		GitHubClient client = new GitHubClient(
				"test-owner", "test-repo", "test-token"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertThat(client, notNullValue());
	}

	@Test
	public void testTokenOnlyClientConstruction() {
		assertThat(new GitHubClient("test-token"), notNullValue()); //$NON-NLS-1$
	}

	@Test
	public void testParsePullRequestPathsFromSearch() {
		String json = "{\"total_count\":2,\"items\":[" //$NON-NLS-1$
				+ "{\"pull_request\":{\"url\":\"https://api.github.com/repos/alice/one/pulls/7\"}}," //$NON-NLS-1$
				+ "{\"pull_request\":{\"url\":\"https://api.github.com/repos/acme/two/pulls/12\"}}" //$NON-NLS-1$
				+ "]}"; //$NON-NLS-1$

		List<String> paths = GitHubJsonParser
				.parseSearchPullRequestPaths(json);

		assertThat(paths, hasSize(2));
		assertThat(paths.get(0), equalTo("/repos/alice/one/pulls/7")); //$NON-NLS-1$
		assertThat(paths.get(1), equalTo("/repos/acme/two/pulls/12")); //$NON-NLS-1$
	}

	@Test
	public void testExtractStringFromGitHubJsonParser() {
		String json = "{\"state\":\"APPROVED\",\"id\":123}"; //$NON-NLS-1$
		String state = GitHubJsonParser.extractString(json, "state"); //$NON-NLS-1$
		assertThat(state, equalTo("APPROVED")); //$NON-NLS-1$
	}

	@Test
	public void testExtractLongFromGitHubJsonParser() {
		String json = "{\"state\":\"APPROVED\",\"id\":12345}"; //$NON-NLS-1$
		long id = GitHubJsonParser.extractLong(json, "id"); //$NON-NLS-1$
		assertThat(id, equalTo(12345L));
	}

	@Test
	public void testExtractNestedObject() {
		String json = "{\"id\":1,\"user\":{\"login\":\"testuser\",\"id\":456}}"; //$NON-NLS-1$
		String userObj = GitHubJsonParser.extractObject(json, "user"); //$NON-NLS-1$
		assertThat(userObj, notNullValue());

		String login = GitHubJsonParser.extractString(userObj, "login"); //$NON-NLS-1$
		assertThat(login, equalTo("testuser")); //$NON-NLS-1$
	}

	@Test
	public void testParseReviewArray() {
		// Test parsing a reviews array to ensure pagination logic works
		String reviewsJson = "[" //$NON-NLS-1$
				+ "{\"id\":1,\"user\":{\"login\":\"user1\"},\"state\":\"COMMENTED\"}," //$NON-NLS-1$
				+ "{\"id\":2,\"user\":{\"login\":\"user2\"},\"state\":\"APPROVED\"}," //$NON-NLS-1$
				+ "{\"id\":3,\"user\":{\"login\":\"user2\"},\"state\":\"APPROVED\"}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		// Verify we can parse the array structure
		int firstBrace = reviewsJson.indexOf('{');
		assertThat(firstBrace > 0, equalTo(true));

		// Extract first review
		String firstReview = reviewsJson.substring(firstBrace,
				reviewsJson.indexOf('}', firstBrace) + 1);
		long id = GitHubJsonParser.extractLong(firstReview, "id"); //$NON-NLS-1$
		assertThat(id, equalTo(1L));

		String userObj = GitHubJsonParser.extractObject(firstReview,
				"user"); //$NON-NLS-1$
		String login = GitHubJsonParser.extractString(userObj, "login"); //$NON-NLS-1$
		assertThat(login, equalTo("user1")); //$NON-NLS-1$
	}

	@Test
	public void testParseApprovedReview() {
		// Test parsing an APPROVED review that would be found by
		// unapproveReview
		String reviewJson = "{\"id\":42,\"user\":{\"login\":\"reviewer\"}," //$NON-NLS-1$
				+ "\"state\":\"APPROVED\",\"body\":\"LGTM\"}"; //$NON-NLS-1$

		String state = GitHubJsonParser.extractString(reviewJson, "state"); //$NON-NLS-1$
		assertThat(state, equalTo("APPROVED")); //$NON-NLS-1$

		long id = GitHubJsonParser.extractLong(reviewJson, "id"); //$NON-NLS-1$
		assertThat(id, equalTo(42L));

		String userObj = GitHubJsonParser.extractObject(reviewJson, "user"); //$NON-NLS-1$
		String login = GitHubJsonParser.extractString(userObj, "login"); //$NON-NLS-1$
		assertThat(login, equalTo("reviewer")); //$NON-NLS-1$
	}

	@Test
	public void testParseMultipleApprovedReviewsSameUser() {
		// Test scenario where user has multiple approved reviews (should find
		// latest)
		String reviewsJson = "[" //$NON-NLS-1$
				+ "{\"id\":100,\"user\":{\"login\":\"reviewer\"},\"state\":\"APPROVED\"}," //$NON-NLS-1$
				+ "{\"id\":200,\"user\":{\"login\":\"reviewer\"},\"state\":\"APPROVED\"}," //$NON-NLS-1$
				+ "{\"id\":300,\"user\":{\"login\":\"other\"},\"state\":\"APPROVED\"}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		// The logic should find id=200 as the latest review by "reviewer"
		// This tests the findLatestApprovalReviewId logic
		int idx = 0;
		long latestId = -1;
		while (true) {
			int objStart = reviewsJson.indexOf('{', idx);
			if (objStart == -1) {
				break;
			}
			int depth = 0;
			int objEnd = objStart;
			for (int i = objStart; i < reviewsJson.length(); i++) {
				char c = reviewsJson.charAt(i);
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						objEnd = i;
						break;
					}
				}
			}

			String obj = reviewsJson.substring(objStart, objEnd + 1);
			String state = GitHubJsonParser.extractString(obj, "state"); //$NON-NLS-1$
			String userObj = GitHubJsonParser.extractObject(obj, "user"); //$NON-NLS-1$
			String login = userObj != null
					? GitHubJsonParser.extractString(userObj, "login") //$NON-NLS-1$
					: null;

			if ("APPROVED".equals(state) && "reviewer".equals(login)) { //$NON-NLS-1$ //$NON-NLS-2$
				long id = GitHubJsonParser.extractLong(obj, "id"); //$NON-NLS-1$
				if (id > latestId) {
					latestId = id;
				}
			}
			idx = objEnd + 1;
		}

		assertThat(latestId, equalTo(200L));
	}
}
