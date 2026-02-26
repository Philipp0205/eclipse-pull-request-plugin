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
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.eclipse.egit.pullrequest.internal.model.ChangedFile;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;
import org.junit.Test;

/**
 * Tests for {@link GitHubJsonParser}
 */
public class GitHubJsonParserTest {

	@Test
	public void testParseInlineComment() {
		String json = "{\"id\":123,\"body\":\"Fix this issue\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"path\":\"src/Main.java\"," //$NON-NLS-1$
				+ "\"line\":42,\"side\":\"RIGHT\",\"user\":{\"login\":\"reviewer\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getId(), equalTo(123L));
		assertThat(comment.getText(), equalTo("Fix this issue")); //$NON-NLS-1$
		assertThat(comment.getPath(), equalTo("src/Main.java")); //$NON-NLS-1$
		assertThat(comment.getLine(), equalTo(42));
		assertThat(comment.getFileType(), equalTo("TO")); //$NON-NLS-1$
		assertThat(comment.getAuthorName(), equalTo("reviewer")); //$NON-NLS-1$
		// Inline comments with path should be marked as review comments
		assertThat(comment.isReviewComment(), equalTo(true));
	}

	@Test
	public void testParseInlineCommentLeftSide() {
		String json = "{\"id\":456,\"body\":\"Old code comment\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"path\":\"src/Utils.java\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"LEFT\",\"user\":{\"login\":\"reviewer\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getFileType(), equalTo("FROM")); //$NON-NLS-1$
	}

	@Test
	public void testParseGeneralComment() {
		String json = "{\"id\":789,\"body\":\"General feedback\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"user\":{\"login\":\"commenter\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getId(), equalTo(789L));
		assertThat(comment.getText(), equalTo("General feedback")); //$NON-NLS-1$
		assertThat(comment.getPath(), nullValue());
		assertThat(comment.getLine(), nullValue());
		// General comments without path should be marked as issue comments
		assertThat(comment.isReviewComment(), equalTo(false));
	}

	@Test
	public void testParseCommentWithBracesInBody() {
		// Test that braces in comment body don't break JSON parsing
		String json = "{\"id\":999,\"body\":\"Fix this: if (x > 0) { return true; }\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"path\":\"src/Code.java\"," //$NON-NLS-1$
				+ "\"line\":5,\"side\":\"RIGHT\",\"user\":{\"login\":\"dev\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getText(),
				equalTo("Fix this: if (x > 0) { return true; }")); //$NON-NLS-1$
		assertThat(comment.getLine(), equalTo(5));
	}

	@Test
	public void testParseCommentArrayWithBraces() {
		// Test array parsing with braces in comment bodies
		String json = "[{\"id\":1,\"body\":\"Code: { x: 1 }\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"path\":\"test.js\"," //$NON-NLS-1$
				+ "\"line\":1,\"side\":\"RIGHT\",\"user\":{\"login\":\"user1\"}}," //$NON-NLS-1$
				+ "{\"id\":2,\"body\":\"Another: } {\",\"created_at\":\"2026-01-15T10:10:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:15:00Z\",\"path\":\"test.js\"," //$NON-NLS-1$
				+ "\"line\":2,\"side\":\"RIGHT\",\"user\":{\"login\":\"user2\"}}]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser
				.parseReviewComments(json);

		assertThat(comments, hasSize(2));
		assertThat(comments.get(0).getText(), equalTo("Code: { x: 1 }")); //$NON-NLS-1$
		assertThat(comments.get(1).getText(), equalTo("Another: } {")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentWithNullLine() {
		// Test original_line fallback when line is null (outdated comments)
		String json = "{\"id\":555,\"body\":\"Outdated comment\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"path\":\"src/Old.java\"," //$NON-NLS-1$
				+ "\"line\":null,\"original_line\":25,\"side\":\"RIGHT\",\"user\":{\"login\":\"reviewer\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getLine(), equalTo(25));
		assertThat(comment.getPath(), equalTo("src/Old.java")); //$NON-NLS-1$
	}

	@Test
	public void testParseEmptyCommentArray() {
		String json = "[]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser
				.parseReviewComments(json);

		assertThat(comments, hasSize(0));
	}

	@Test
	public void testParseMultipleInlineComments() {
		String json = "[{\"id\":1,\"body\":\"Comment 1\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"path\":\"file1.java\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"user\":{\"login\":\"user1\"}}," //$NON-NLS-1$
				+ "{\"id\":2,\"body\":\"Comment 2\",\"created_at\":\"2026-01-15T10:10:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:15:00Z\",\"path\":\"file2.java\"," //$NON-NLS-1$
				+ "\"line\":20,\"side\":\"LEFT\",\"user\":{\"login\":\"user2\"}}]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser
				.parseReviewComments(json);

		assertThat(comments, hasSize(2));
		assertThat(comments.get(0).getPath(), equalTo("file1.java")); //$NON-NLS-1$
		assertThat(comments.get(0).getLine(), equalTo(10));
		assertThat(comments.get(0).getFileType(), equalTo("TO")); //$NON-NLS-1$
		assertThat(comments.get(1).getPath(), equalTo("file2.java")); //$NON-NLS-1$
		assertThat(comments.get(1).getLine(), equalTo(20));
		assertThat(comments.get(1).getFileType(), equalTo("FROM")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentWithInReplyToId() {
		// Test that in_reply_to_id field is extracted
		String json = "{\"id\":200,\"body\":\"Reply to comment\",\"created_at\":\"2026-01-15T11:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T11:00:00Z\",\"path\":\"src/Main.java\"," //$NON-NLS-1$
				+ "\"line\":42,\"side\":\"RIGHT\",\"in_reply_to_id\":100," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"reviewer2\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getId(), equalTo(200L));
		assertThat(comment.getInReplyToId(), equalTo(100L));
		assertThat(comment.getText(), equalTo("Reply to comment")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentThread() {
		// Test that comments are grouped into threads correctly
		String json = "[" //$NON-NLS-1$
				+ "{\"id\":1,\"body\":\"Root comment\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"user\":{\"login\":\"user1\"}}," //$NON-NLS-1$
				+ "{\"id\":2,\"body\":\"Reply 1\",\"created_at\":\"2026-01-15T11:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T11:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"in_reply_to_id\":1," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"user2\"}}," //$NON-NLS-1$
				+ "{\"id\":3,\"body\":\"Reply 2\",\"created_at\":\"2026-01-15T12:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T12:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"in_reply_to_id\":1," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"user3\"}}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser.parseComments(
				json, null);

		// Should return only 1 root comment
		assertThat(comments, hasSize(1));

		PullRequestComment root = comments.get(0);
		assertThat(root.getId(), equalTo(1L));
		assertThat(root.getText(), equalTo("Root comment")); //$NON-NLS-1$
		assertThat(root.getInReplyToId(), equalTo(-1L));

		// Root should have 2 replies
		List<PullRequestComment> replies = root.getReplies();
		assertThat(replies, hasSize(2));

		// Replies should be in chronological order
		assertThat(replies.get(0).getId(), equalTo(2L));
		assertThat(replies.get(0).getText(), equalTo("Reply 1")); //$NON-NLS-1$
		assertThat(replies.get(0).getInReplyToId(), equalTo(1L));

		assertThat(replies.get(1).getId(), equalTo(3L));
		assertThat(replies.get(1).getText(), equalTo("Reply 2")); //$NON-NLS-1$
		assertThat(replies.get(1).getInReplyToId(), equalTo(1L));
	}

	@Test
	public void testParseCommentThreadOrdering() {
		// Test that replies are sorted chronologically even if received
		// out-of-order
		String json = "[" //$NON-NLS-1$
				+ "{\"id\":1,\"body\":\"Root\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":5,\"side\":\"RIGHT\",\"user\":{\"login\":\"user1\"}}," //$NON-NLS-1$
				+ "{\"id\":3,\"body\":\"Third reply\",\"created_at\":\"2026-01-15T14:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T14:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":5,\"side\":\"RIGHT\",\"in_reply_to_id\":1," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"user3\"}}," //$NON-NLS-1$
				+ "{\"id\":2,\"body\":\"First reply\",\"created_at\":\"2026-01-15T12:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T12:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":5,\"side\":\"RIGHT\",\"in_reply_to_id\":1," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"user2\"}}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser.parseComments(
				json, null);

		assertThat(comments, hasSize(1));
		List<PullRequestComment> replies = comments.get(0).getReplies();
		assertThat(replies, hasSize(2));

		// Should be sorted by created date (ID 2 before ID 3)
		assertThat(replies.get(0).getId(), equalTo(2L));
		assertThat(replies.get(1).getId(), equalTo(3L));
	}

	@Test
	public void testParseCommentOrphanedReply() {
		// Test that a reply without a parent in the result set is treated as a
		// root
		String json = "[" //$NON-NLS-1$
				+ "{\"id\":2,\"body\":\"Orphaned reply\",\"created_at\":\"2026-01-15T11:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T11:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"in_reply_to_id\":1," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"user2\"}}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser.parseComments(
				json, null);

		// Orphaned reply should be treated as root
		assertThat(comments, hasSize(1));
		assertThat(comments.get(0).getId(), equalTo(2L));
		assertThat(comments.get(0).getText(), equalTo("Orphaned reply")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentsMixedThreadsAndRoots() {
		// Test mix of threaded and non-threaded comments
		String json = "[" //$NON-NLS-1$
				+ "{\"id\":1,\"body\":\"Root 1\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:00:00Z\",\"path\":\"file1.txt\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"user\":{\"login\":\"user1\"}}," //$NON-NLS-1$
				+ "{\"id\":2,\"body\":\"Root 2\",\"created_at\":\"2026-01-15T10:30:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:30:00Z\",\"path\":\"file2.txt\"," //$NON-NLS-1$
				+ "\"line\":20,\"side\":\"RIGHT\",\"user\":{\"login\":\"user2\"}}," //$NON-NLS-1$
				+ "{\"id\":3,\"body\":\"Reply to 1\",\"created_at\":\"2026-01-15T11:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T11:00:00Z\",\"path\":\"file1.txt\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"in_reply_to_id\":1," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"user3\"}}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser.parseComments(
				json, null);

		// Should return 2 root comments
		assertThat(comments, hasSize(2));

		// First root has 1 reply
		PullRequestComment root1 = comments.get(0);
		assertThat(root1.getId(), equalTo(1L));
		assertThat(root1.getReplies(), hasSize(1));
		assertThat(root1.getReplies().get(0).getId(), equalTo(3L));

		// Second root has no replies
		PullRequestComment root2 = comments.get(1);
		assertThat(root2.getId(), equalTo(2L));
		assertThat(root2.getReplies(), hasSize(0));
	}

	@Test
	public void testParseCommentsIssueCommentsNotThreaded() {
		// Test that issue comments (general PR comments) are not grouped into
		// threads
		String reviewJson = "[" //$NON-NLS-1$
				+ "{\"id\":1,\"body\":\"Review comment\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:00:00Z\",\"path\":\"file.txt\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\",\"user\":{\"login\":\"user1\"}}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		String issueJson = "[" //$NON-NLS-1$
				+ "{\"id\":100,\"body\":\"General comment 1\",\"created_at\":\"2026-01-15T10:30:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:30:00Z\",\"user\":{\"login\":\"user2\"}}," //$NON-NLS-1$
				+ "{\"id\":101,\"body\":\"General comment 2\",\"created_at\":\"2026-01-15T11:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T11:00:00Z\",\"user\":{\"login\":\"user3\"}}" //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$

		List<PullRequestComment> comments = GitHubJsonParser.parseComments(
				reviewJson, issueJson);

		// Should have 3 root comments total (1 review + 2 issue)
		assertThat(comments, hasSize(3));

		// All should be root level (no replies)
		for (PullRequestComment comment : comments) {
			assertThat(comment.getReplies(), hasSize(0));
		}

		// First comment should be marked as review comment
		assertThat(comments.get(0).isReviewComment(), equalTo(true));

		// Last two comments should be marked as issue comments
		assertThat(comments.get(1).isReviewComment(), equalTo(false));
		assertThat(comments.get(2).isReviewComment(), equalTo(false));
	}

	@Test
	public void testParseChangedFileWithPatch() {
		String json = "[{\"sha\":\"abc123\"," //$NON-NLS-1$
				+ "\"filename\":\"src/Main.java\"," //$NON-NLS-1$
				+ "\"status\":\"modified\"," //$NON-NLS-1$
				+ "\"additions\":2,\"deletions\":1," //$NON-NLS-1$
				+ "\"changes\":3," //$NON-NLS-1$
				+ "\"patch\":\"@@ -1,4 +1,5 @@\\n import java.util.List;\\n-import java.util.ArrayList;\\n+import java.util.LinkedList;\\n+import java.util.Set;\\n import java.util.Map;\"}]"; //$NON-NLS-1$

		List<ChangedFile> files = GitHubJsonParser
				.parseChangedFiles(json);

		assertThat(files, hasSize(1));
		ChangedFile file = files.get(0);
		assertThat(file.getPath(), notNullValue());
		assertThat(file.getPath().getToString(),
				equalTo("src/Main.java")); //$NON-NLS-1$
		assertThat(file.getType(), equalTo("MODIFY")); //$NON-NLS-1$

		// Patch should be parsed and stored
		assertThat(file.getPatch(), notNullValue());
		// Verify the patch contains the hunk header
		assertThat(file.getPatch().contains("@@ -1,4 +1,5 @@"), //$NON-NLS-1$
				equalTo(true));
	}

	@Test
	public void testParseChangedFileWithoutPatch() {
		// Binary files don't have a patch field
		String json = "[{\"sha\":\"def456\"," //$NON-NLS-1$
				+ "\"filename\":\"image.png\"," //$NON-NLS-1$
				+ "\"status\":\"added\"," //$NON-NLS-1$
				+ "\"additions\":0,\"deletions\":0," //$NON-NLS-1$
				+ "\"changes\":0}]"; //$NON-NLS-1$

		List<ChangedFile> files = GitHubJsonParser
				.parseChangedFiles(json);

		assertThat(files, hasSize(1));
		ChangedFile file = files.get(0);
		assertThat(file.getPath().getToString(),
				equalTo("image.png")); //$NON-NLS-1$
		assertThat(file.getType(), equalTo("ADD")); //$NON-NLS-1$
		assertThat(file.getPatch(), nullValue());
	}

	@Test
	public void testParsePullRequestRefSha() {
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"body\":\"desc\"," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature-branch\"," //$NON-NLS-1$
				+ "\"sha\":\"abc123def456\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"my-repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"owner/my-repo\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"owner\"}}}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\"," //$NON-NLS-1$
				+ "\"sha\":\"789xyz000111\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"my-repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"owner/my-repo\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"owner\"}}}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}," //$NON-NLS-1$
				+ "\"html_url\":\"https://github.com/owner/my-repo/pull/42\"," //$NON-NLS-1$
				+ "\"comments\":5}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());

		// Head ref (fromRef) should have branch name and SHA
		assertThat(pr.getFromRef(), notNullValue());
		assertThat(pr.getFromRef().getId(),
				equalTo("feature-branch")); //$NON-NLS-1$
		assertThat(pr.getFromRef().getLatestCommit(),
				equalTo("abc123def456")); //$NON-NLS-1$

		// Base ref (toRef) should have branch name and SHA
		assertThat(pr.getToRef(), notNullValue());
		assertThat(pr.getToRef().getId(),
				equalTo("main")); //$NON-NLS-1$
		assertThat(pr.getToRef().getLatestCommit(),
				equalTo("789xyz000111")); //$NON-NLS-1$
	}

	@Test
	public void testParseCloneUrl() {
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"body\":\"desc\"," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature-branch\"," //$NON-NLS-1$
				+ "\"sha\":\"abc123def456\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"fork-repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"forker/fork-repo\"," //$NON-NLS-1$
				+ "\"clone_url\":\"https://github.com/forker/fork-repo.git\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"forker\"}}}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\"," //$NON-NLS-1$
				+ "\"sha\":\"789xyz000111\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"my-repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"owner/my-repo\"," //$NON-NLS-1$
				+ "\"clone_url\":\"https://github.com/owner/my-repo.git\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"owner\"}}}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}," //$NON-NLS-1$
				+ "\"html_url\":\"https://github.com/owner/my-repo/pull/42\"," //$NON-NLS-1$
				+ "\"comments\":5}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());

		// Head ref (fromRef) should have fork's clone URL
		assertThat(pr.getFromRef().getRepository(), notNullValue());
		assertThat(pr.getFromRef().getRepository().getCloneUrl(),
				equalTo("https://github.com/forker/fork-repo.git")); //$NON-NLS-1$

		// Base ref (toRef) should have base repo's clone URL
		assertThat(pr.getToRef().getRepository(), notNullValue());
		assertThat(pr.getToRef().getRepository().getCloneUrl(),
				equalTo("https://github.com/owner/my-repo.git")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentCountWithUrlFields() {
		// Test that comment count is correctly extracted even when
		// comments_url, review_comments_url appear before comments field
		// (regression test for substring matching bug)
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"body\":\"desc\"," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"comments_url\":\"https://api.github.com/repos/owner/repo/issues/42/comments\"," //$NON-NLS-1$
				+ "\"review_comments_url\":\"https://api.github.com/repos/owner/repo/pulls/42/comments\"," //$NON-NLS-1$
				+ "\"review_comments\":3," //$NON-NLS-1$
				+ "\"comments\":7," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature\"," //$NON-NLS-1$
				+ "\"sha\":\"abc123\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"owner/repo\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"owner\"}}}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\"," //$NON-NLS-1$
				+ "\"sha\":\"xyz789\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"owner/repo\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"owner\"}}}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}," //$NON-NLS-1$
				+ "\"html_url\":\"https://github.com/owner/repo/pull/42\"}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());
		// The comment count should be 7, not 0 or 3
		assertThat(pr.getCommentCount(), equalTo(7));
	}

	@Test
	public void testParsePullRequestRefShaWithCommentCount() {
		// Add assertion for comment count in existing test
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"body\":\"desc\"," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature-branch\"," //$NON-NLS-1$
				+ "\"sha\":\"abc123def456\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"my-repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"owner/my-repo\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"owner\"}}}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\"," //$NON-NLS-1$
				+ "\"sha\":\"789xyz000111\"," //$NON-NLS-1$
				+ "\"repo\":{\"name\":\"my-repo\"," //$NON-NLS-1$
				+ "\"full_name\":\"owner/my-repo\"," //$NON-NLS-1$
				+ "\"owner\":{\"login\":\"owner\"}}}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}," //$NON-NLS-1$
				+ "\"html_url\":\"https://github.com/owner/my-repo/pull/42\"," //$NON-NLS-1$
				+ "\"comments\":5}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());
		assertThat(pr.getCommentCount(), equalTo(5));

		// Head ref (fromRef) should have branch name and SHA
		assertThat(pr.getFromRef(), notNullValue());
		assertThat(pr.getFromRef().getId(),
				equalTo("feature-branch")); //$NON-NLS-1$
		assertThat(pr.getFromRef().getLatestCommit(),
				equalTo("abc123def456")); //$NON-NLS-1$

		// Base ref (toRef) should have branch name and SHA
		assertThat(pr.getToRef(), notNullValue());
		assertThat(pr.getToRef().getId(),
				equalTo("main")); //$NON-NLS-1$
		assertThat(pr.getToRef().getLatestCommit(),
				equalTo("789xyz000111")); //$NON-NLS-1$
	}

	@Test
	public void testParseReviewersEmpty() {
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"draft\":false," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"requested_reviewers\":[]," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature\",\"sha\":\"abc123\"}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\",\"sha\":\"def456\"}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), notNullValue());
		assertThat(pr.getReviewers(), hasSize(0));
	}

	@Test
	public void testParseSingleReviewer() {
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"draft\":false," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"requested_reviewers\":[" //$NON-NLS-1$
				+ "{\"login\":\"reviewer1\"," //$NON-NLS-1$
				+ "\"name\":\"Reviewer One\"," //$NON-NLS-1$
				+ "\"email\":\"reviewer1@example.com\"}]," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature\",\"sha\":\"abc123\"}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\",\"sha\":\"def456\"}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), notNullValue());
		assertThat(pr.getReviewers(), hasSize(1));

		PullRequest.PullRequestParticipant reviewer =
				pr.getReviewers().get(0);
		assertThat(reviewer.getUser().getName(),
				equalTo("reviewer1")); //$NON-NLS-1$
		assertThat(reviewer.getUser().getDisplayName(),
				equalTo("Reviewer One")); //$NON-NLS-1$
		assertThat(reviewer.getUser().getEmailAddress(),
				equalTo("reviewer1@example.com")); //$NON-NLS-1$
		assertThat(reviewer.getRole(), equalTo("REVIEWER")); //$NON-NLS-1$
		assertThat(reviewer.isApproved(), equalTo(false));
	}

	@Test
	public void testParseMultipleReviewers() {
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"draft\":false," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"requested_reviewers\":[" //$NON-NLS-1$
				+ "{\"login\":\"alice\",\"name\":\"Alice\"}," //$NON-NLS-1$
				+ "{\"login\":\"bob\",\"name\":\"Bob\"}," //$NON-NLS-1$
				+ "{\"login\":\"charlie\"}]," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature\",\"sha\":\"abc123\"}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\",\"sha\":\"def456\"}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), hasSize(3));
		assertThat(pr.getReviewers().get(0).getUser().getName(),
				equalTo("alice")); //$NON-NLS-1$
		assertThat(pr.getReviewers().get(1).getUser().getName(),
				equalTo("bob")); //$NON-NLS-1$
		assertThat(pr.getReviewers().get(2).getUser().getName(),
				equalTo("charlie")); //$NON-NLS-1$
	}

	@Test
	public void testParseReviewersWithTeams() {
		// GitHub supports team reviewers, which should be handled gracefully
		String json = "{\"number\":42,\"title\":\"Test PR\"," //$NON-NLS-1$
				+ "\"state\":\"open\",\"draft\":false," //$NON-NLS-1$
				+ "\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"requested_reviewers\":[" //$NON-NLS-1$
				+ "{\"login\":\"reviewer1\"}]," //$NON-NLS-1$
				+ "\"requested_teams\":[" //$NON-NLS-1$
				+ "{\"slug\":\"core-team\",\"name\":\"Core Team\"}]," //$NON-NLS-1$
				+ "\"head\":{\"ref\":\"feature\",\"sha\":\"abc123\"}," //$NON-NLS-1$
				+ "\"base\":{\"ref\":\"main\",\"sha\":\"def456\"}," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"author\"}}"; //$NON-NLS-1$

		PullRequest pr = GitHubJsonParser.parseSinglePullRequest(json);

		assertThat(pr, notNullValue());
		// Should have both individual and team reviewers
		assertThat(pr.getReviewers(), hasSize(2));
		assertThat(pr.getReviewers().get(0).getUser().getName(),
				equalTo("reviewer1")); //$NON-NLS-1$
		assertThat(pr.getReviewers().get(1).getUser().getName(),
				equalTo("core-team")); //$NON-NLS-1$
		assertThat(pr.getReviewers().get(1).getUser().getDisplayName(),
				equalTo("Core Team")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommit() {
		String json = "{\"sha\":\"abc123def456\",\"commit\":{\"message\":\"Fix bug in parser\",\"author\":{\"name\":\"John Doe\",\"email\":\"john@example.com\",\"date\":\"2026-01-15T10:30:00Z\"}},\"parents\":[{\"sha\":\"parent1\"},{\"sha\":\"parent2\"}]}"; //$NON-NLS-1$

		PullRequestCommit commit = GitHubJsonParser.parseCommit(json);

		assertThat(commit, notNullValue());
		assertThat(commit.getId(), equalTo("abc123def456")); //$NON-NLS-1$
		assertThat(commit.getShortId(), equalTo("abc123d")); //$NON-NLS-1$
		assertThat(commit.getMessage(), equalTo("Fix bug in parser")); //$NON-NLS-1$
		assertThat(commit.getFirstLine(), equalTo("Fix bug in parser")); //$NON-NLS-1$
		assertThat(commit.getAuthorName(), equalTo("John Doe")); //$NON-NLS-1$
		assertThat(commit.getAuthorEmail(), equalTo("john@example.com")); //$NON-NLS-1$
		assertThat(commit.getAuthorDate(), equalTo(1736937000000L));
		assertThat(commit.getParents(), hasSize(2));
		assertThat(commit.getParents().get(0), equalTo("parent1")); //$NON-NLS-1$
		assertThat(commit.getParents().get(1), equalTo("parent2")); //$NON-NLS-1$
		assertThat(commit.isMergeCommit(), equalTo(true));
	}

	@Test
	public void testParseCommitMultilineMessage() {
		String json = "{\"sha\":\"xyz789\",\"commit\":{\"message\":\"First line\\n\\nDetailed description\\non multiple lines\",\"author\":{\"name\":\"Jane Smith\",\"email\":\"jane@example.com\",\"date\":\"2026-01-16T14:20:00Z\"}},\"parents\":[{\"sha\":\"single-parent\"}]}"; //$NON-NLS-1$

		PullRequestCommit commit = GitHubJsonParser.parseCommit(json);

		assertThat(commit, notNullValue());
		assertThat(commit.getMessage(),
				equalTo("First line\\n\\nDetailed description\\non multiple lines")); //$NON-NLS-1$
		assertThat(commit.getFirstLine(), equalTo("First line")); //$NON-NLS-1$
		assertThat(commit.getParents(), hasSize(1));
		assertThat(commit.isMergeCommit(), equalTo(false));
	}

	@Test
	public void testParseCommits() {
		String json = "[{\"sha\":\"commit1\",\"commit\":{\"message\":\"First commit\",\"author\":{\"name\":\"Author1\",\"email\":\"author1@test.com\",\"date\":\"2026-01-10T09:00:00Z\"}},\"parents\":[{\"sha\":\"p1\"}]}," //$NON-NLS-1$
				+ "{\"sha\":\"commit2\",\"commit\":{\"message\":\"Second commit\",\"author\":{\"name\":\"Author2\",\"email\":\"author2@test.com\",\"date\":\"2026-01-11T10:00:00Z\"}},\"parents\":[{\"sha\":\"p2\"}]}]"; //$NON-NLS-1$

		List<PullRequestCommit> commits = GitHubJsonParser.parseCommits(json);

		assertThat(commits, hasSize(2));
		assertThat(commits.get(0).getId(), equalTo("commit1")); //$NON-NLS-1$
		assertThat(commits.get(0).getMessage(), equalTo("First commit")); //$NON-NLS-1$
		assertThat(commits.get(0).getAuthorName(), equalTo("Author1")); //$NON-NLS-1$
		assertThat(commits.get(1).getId(), equalTo("commit2")); //$NON-NLS-1$
		assertThat(commits.get(1).getMessage(), equalTo("Second commit")); //$NON-NLS-1$
		assertThat(commits.get(1).getAuthorName(), equalTo("Author2")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommitsEmptyArray() {
		String json = "[]"; //$NON-NLS-1$

		List<PullRequestCommit> commits = GitHubJsonParser.parseCommits(json);

		assertThat(commits, hasSize(0));
	}

	@Test
	public void testParseCommentWithAvatarUrl() {
		// Test that avatar_url is extracted from user object
		String json = "{\"id\":888,\"body\":\"Test comment\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\",\"path\":\"src/Test.java\"," //$NON-NLS-1$
				+ "\"line\":10,\"side\":\"RIGHT\"," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"testuser\"," //$NON-NLS-1$
				+ "\"avatar_url\":\"https://avatars.githubusercontent.com/u/123?v=4\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getAuthorName(), equalTo("testuser")); //$NON-NLS-1$
		assertThat(comment.getAuthorAvatarUrl(),
				equalTo("https://avatars.githubusercontent.com/u/123?v=4")); //$NON-NLS-1$
	}

	@Test
	public void testParseCommentWithoutAvatarUrl() {
		// Test that missing avatar_url doesn't break parsing
		String json = "{\"id\":999,\"body\":\"No avatar\",\"created_at\":\"2026-01-15T10:00:00Z\"," //$NON-NLS-1$
				+ "\"updated_at\":\"2026-01-15T10:05:00Z\"," //$NON-NLS-1$
				+ "\"user\":{\"login\":\"noavatar\"}}"; //$NON-NLS-1$

		PullRequestComment comment = GitHubJsonParser.parseSingleComment(json);

		assertThat(comment, notNullValue());
		assertThat(comment.getAuthorName(), equalTo("noavatar")); //$NON-NLS-1$
		assertThat(comment.getAuthorAvatarUrl(), nullValue());
	}
}
