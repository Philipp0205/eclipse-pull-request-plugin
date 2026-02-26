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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.junit.Test;

/**
 * Tests for {@link BitbucketClient}
 */
public class BitbucketClientTest {

	@Test
	public void testClientConstruction() {
		BitbucketClient client = new BitbucketClient(
				"https://bitbucket.example.com", "TEST", "test-repo", "test-token"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertThat(client, notNullValue());
	}

	@Test
	public void testClientConstructionWithTrailingSlash() {
		BitbucketClient client = new BitbucketClient(
				"https://bitbucket.example.com/", "TEST", "test-repo", "test-token"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertThat(client, notNullValue());
	}

	@Test
	public void testPullRequestModel() {
		PullRequest pr = new PullRequest();
		pr.setId(123);
		pr.setTitle("Test PR"); //$NON-NLS-1$
		pr.setState("OPEN"); //$NON-NLS-1$

		assertThat(pr.getId(), equalTo(123L));
		assertThat(pr.getTitle(), equalTo("Test PR")); //$NON-NLS-1$
		assertThat(pr.getState(), equalTo("OPEN")); //$NON-NLS-1$
	}

	@Test
	public void testPullRequestParticipant() {
		PullRequest.User user = new PullRequest.User();
		user.setName("jdoe"); //$NON-NLS-1$
		user.setDisplayName("John Doe"); //$NON-NLS-1$
		user.setEmailAddress("jdoe@example.com"); //$NON-NLS-1$

		PullRequest.PullRequestParticipant participant = new PullRequest.PullRequestParticipant();
		participant.setUser(user);
		participant.setRole("AUTHOR"); //$NON-NLS-1$
		participant.setApproved(false);

		assertThat(participant.getUser().getName(), equalTo("jdoe")); //$NON-NLS-1$
		assertThat(participant.getUser().getDisplayName(),
				equalTo("John Doe")); //$NON-NLS-1$
		assertThat(participant.getRole(), equalTo("AUTHOR")); //$NON-NLS-1$
		assertThat(participant.isApproved(), equalTo(false));
	}

	@Test
	public void testPullRequestRef() {
		PullRequest.Project project = new PullRequest.Project();
		project.setKey("PROJ"); //$NON-NLS-1$
		project.setName("Test Project"); //$NON-NLS-1$

		PullRequest.Repository repo = new PullRequest.Repository();
		repo.setSlug("test-repo"); //$NON-NLS-1$
		repo.setName("Test Repository"); //$NON-NLS-1$
		repo.setProject(project);

		PullRequest.PullRequestRef ref = new PullRequest.PullRequestRef();
		ref.setId("refs/heads/feature/test"); //$NON-NLS-1$
		ref.setDisplayId("feature/test"); //$NON-NLS-1$
		ref.setRepository(repo);

		assertThat(ref.getId(), equalTo("refs/heads/feature/test")); //$NON-NLS-1$
		assertThat(ref.getDisplayId(), equalTo("feature/test")); //$NON-NLS-1$
		assertThat(ref.getRepository().getSlug(), equalTo("test-repo")); //$NON-NLS-1$
		assertThat(ref.getRepository().getProject().getKey(),
				equalTo("PROJ")); //$NON-NLS-1$
	}

	@Test
	public void testParseBitbucketCloneUrl() {
		String json = "{\"id\":42,\"version\":1,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"OPEN\",\"open\":true,\"closed\":false," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000,\"updatedDate\":1705319100000," //$NON-NLS-1$
				+ "\"fromRef\":{\"id\":\"refs/heads/feature/test\"," //$NON-NLS-1$
				+ "\"displayId\":\"feature/test\"," //$NON-NLS-1$
				+ "\"latestCommit\":\"abc123\"," //$NON-NLS-1$
				+ "\"repository\":{\"slug\":\"fork-repo\"," //$NON-NLS-1$
				+ "\"name\":\"Fork Repository\"," //$NON-NLS-1$
				+ "\"project\":{\"key\":\"FORK\",\"name\":\"Fork Project\"}," //$NON-NLS-1$
				+ "\"links\":{\"clone\":[" //$NON-NLS-1$
				+ "{\"href\":\"https://bitbucket.example.com/scm/fork/fork-repo.git\",\"name\":\"http\"}," //$NON-NLS-1$
				+ "{\"href\":\"ssh://git@bitbucket.example.com:7999/fork/fork-repo.git\",\"name\":\"ssh\"}" //$NON-NLS-1$
				+ "]}}}," //$NON-NLS-1$
				+ "\"toRef\":{\"id\":\"refs/heads/main\"," //$NON-NLS-1$
				+ "\"displayId\":\"main\"," //$NON-NLS-1$
				+ "\"latestCommit\":\"def456\"," //$NON-NLS-1$
				+ "\"repository\":{\"slug\":\"test-repo\"," //$NON-NLS-1$
				+ "\"name\":\"Test Repository\"," //$NON-NLS-1$
				+ "\"project\":{\"key\":\"PROJ\",\"name\":\"Test Project\"}," //$NON-NLS-1$
				+ "\"links\":{\"clone\":[" //$NON-NLS-1$
				+ "{\"href\":\"https://bitbucket.example.com/scm/proj/test-repo.git\",\"name\":\"http\"}," //$NON-NLS-1$
				+ "{\"href\":\"ssh://git@bitbucket.example.com:7999/proj/test-repo.git\",\"name\":\"ssh\"}" //$NON-NLS-1$
				+ "]}}}," //$NON-NLS-1$
				+ "\"author\":{\"user\":{\"name\":\"jdoe\",\"displayName\":\"John Doe\"," //$NON-NLS-1$
				+ "\"emailAddress\":\"jdoe@example.com\"}}}"; //$NON-NLS-1$

		PullRequest pr = BitbucketJsonParser
				.parseSinglePullRequest(json, null);

		assertThat(pr, notNullValue());

		// Fork repo should have HTTP clone URL
		assertThat(pr.getFromRef().getRepository(), notNullValue());
		assertThat(pr.getFromRef().getRepository().getCloneUrl(), equalTo(
				"https://bitbucket.example.com/scm/fork/fork-repo.git")); //$NON-NLS-1$

		// Base repo should have HTTP clone URL
		assertThat(pr.getToRef().getRepository(), notNullValue());
		assertThat(pr.getToRef().getRepository().getCloneUrl(), equalTo(
				"https://bitbucket.example.com/scm/proj/test-repo.git")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentWithAvatarUrl() {
		String baseUrl = "https://bitbucket.example.com"; //$NON-NLS-1$
		String json = "{\"id\":1,\"version\":1,\"text\":\"Test comment\"," //$NON-NLS-1$
				+ "\"author\":{\"name\":\"jdoe\",\"emailAddress\":\"jdoe@example.com\"," //$NON-NLS-1$
				+ "\"displayName\":\"John Doe\",\"slug\":\"jdoe\"}," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000,\"updatedDate\":1705319100000}"; //$NON-NLS-1$

		PullRequestComment comment = BitbucketJsonParser.parseComment(json,
				baseUrl);

		assertThat(comment, notNullValue());
		assertThat(comment.getAuthorName(), equalTo("John Doe")); //$NON-NLS-1$
		assertThat(comment.getAuthorAvatarUrl(), equalTo(
				"https://bitbucket.example.com/users/jdoe/avatar.png")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentWithoutSlug() {
		String baseUrl = "https://bitbucket.example.com"; //$NON-NLS-1$
		String json = "{\"id\":1,\"version\":1,\"text\":\"Test comment\"," //$NON-NLS-1$
				+ "\"author\":{\"name\":\"jdoe\",\"emailAddress\":\"jdoe@example.com\"," //$NON-NLS-1$
				+ "\"displayName\":\"John Doe\"}," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000,\"updatedDate\":1705319100000}"; //$NON-NLS-1$

		PullRequestComment comment = BitbucketJsonParser.parseComment(json,
				baseUrl);

		assertThat(comment, notNullValue());
		assertThat(comment.getAuthorName(), equalTo("John Doe")); //$NON-NLS-1$
		// When slug is missing, avatar URL should fall back to using name
		assertThat(comment.getAuthorAvatarUrl(), equalTo(
				"https://bitbucket.example.com/users/jdoe/avatar.png")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentsArray() {
		String baseUrl = "https://bitbucket.example.com"; //$NON-NLS-1$
		String json = "{\"size\":2,\"limit\":25,\"isLastPage\":true," //$NON-NLS-1$
				+ "\"values\":[" //$NON-NLS-1$
				+ "{\"id\":1,\"version\":1,\"text\":\"First comment\"," //$NON-NLS-1$
				+ "\"author\":{\"name\":\"jdoe\",\"displayName\":\"John Doe\",\"slug\":\"jdoe\"}," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000}," //$NON-NLS-1$
				+ "{\"id\":2,\"version\":1,\"text\":\"Second comment\"," //$NON-NLS-1$
				+ "\"author\":{\"name\":\"asmith\",\"displayName\":\"Alice Smith\",\"slug\":\"asmith\"}," //$NON-NLS-1$
				+ "\"createdDate\":1705319100000}" //$NON-NLS-1$
				+ "]}"; //$NON-NLS-1$

		List<PullRequestComment> comments = BitbucketJsonParser
				.parseCommentArray(json, baseUrl);

		assertThat(comments, notNullValue());
		assertThat(comments.size(), equalTo(2));

		PullRequestComment first = comments.get(0);
		assertThat(first.getAuthorName(), equalTo("John Doe")); //$NON-NLS-1$
		assertThat(first.getAuthorAvatarUrl(), equalTo(
				"https://bitbucket.example.com/users/jdoe/avatar.png")); //$NON-NLS-1$

		PullRequestComment second = comments.get(1);
		assertThat(second.getAuthorName(), equalTo("Alice Smith")); //$NON-NLS-1$
		assertThat(second.getAuthorAvatarUrl(), equalTo(
				"https://bitbucket.example.com/users/asmith/avatar.png")); //$NON-NLS-1$
	}
}
