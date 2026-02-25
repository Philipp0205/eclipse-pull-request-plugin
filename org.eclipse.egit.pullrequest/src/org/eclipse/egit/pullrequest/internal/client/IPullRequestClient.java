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
package org.eclipse.egit.pullrequest.internal.client;

import java.io.IOException;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.model.ChangedFile;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * Interface for interacting with pull request providers (Bitbucket, GitHub,
 * etc.). Provides a provider-agnostic API for fetching and managing pull
 * requests, comments, and file changes.
 */
public interface IPullRequestClient {

	/**
	 * Retrieves pull requests for the configured repository
	 *
	 * @param state
	 *            the PR state filter (e.g., "OPEN", "MERGED", "DECLINED" for
	 *            Bitbucket; "open", "closed" for GitHub), or null for all
	 * @param authorUsername
	 *            filter by author username, or null for all
	 * @param reviewerUsername
	 *            filter by reviewer username, or null for all (may not be
	 *            supported by all providers)
	 * @param limit
	 *            the maximum number of results
	 * @param start
	 *            the start index for pagination
	 * @return list of pull requests
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	List<PullRequest> getPullRequests(@Nullable String state,
			@Nullable String authorUsername, @Nullable String reviewerUsername,
			int limit, int start) throws IOException;

	/**
	 * Retrieves a specific pull request by ID or number
	 *
	 * @param pullRequestId
	 *            the pull request ID (Bitbucket) or number (GitHub)
	 * @return the pull request
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	PullRequest getPullRequest(long pullRequestId) throws IOException;

	/**
	 * Retrieves changed files for a pull request
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @return list of changed files
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	List<ChangedFile> getPullRequestChanges(long pullRequestId)
			throws IOException;

	/**
	 * Retrieves comments for a pull request
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @return list of comments (including replies as nested comments)
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	List<PullRequestComment> getPullRequestComments(long pullRequestId)
			throws IOException;

	/**
	 * Retrieves raw file content at a specific commit
	 *
	 * @param commitId
	 *            the commit SHA or branch name
	 * @param path
	 *            the file path
	 * @return raw file content as byte array
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	byte[] getFileContent(@NonNull String commitId, @NonNull String path)
			throws IOException;

	/**
	 * Adds a comment to a pull request
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param text
	 *            the comment text
	 * @param parentCommentId
	 *            the parent comment ID for replies, or -1 for top-level
	 *            comments
	 * @return the created comment
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	PullRequestComment addComment(long pullRequestId, @NonNull String text,
			long parentCommentId) throws IOException;

	/**
	 * Adds an inline comment to a specific line in a pull request file
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param text
	 *            the comment text
	 * @param path
	 *            the file path in the repository
	 * @param line
	 *            the line number (1-based)
	 * @param lineType
	 *            the line type: "ADDED", "REMOVED", or "CONTEXT"
	 * @param fileType
	 *            the file side: "FROM" (old/left) or "TO" (new/right)
	 * @param commitId
	 *            the commit SHA (required by GitHub; may be ignored by other
	 *            providers)
	 * @return the created comment
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	PullRequestComment addInlineComment(long pullRequestId,
			@NonNull String text, @NonNull String path, int line,
			@NonNull String lineType, @NonNull String fileType,
			@NonNull String commitId) throws IOException;

	/**
	 * Updates the severity of a comment (Bitbucket-specific feature for
	 * marking comments as tasks). Providers that don't support this feature
	 * should throw UnsupportedOperationException.
	 *
	 * @param pullRequestId
	 *            the pull request ID
	 * @param commentId
	 *            the comment ID
	 * @param version
	 *            the comment version (for optimistic locking)
	 * @param severity
	 *            the new severity ("NORMAL" or "BLOCKER")
	 * @return the updated comment
	 * @throws IOException
	 *             if the request fails
	 * @throws UnsupportedOperationException
	 *             if the provider doesn't support task severity
	 */
	@NonNull
	PullRequestComment updateCommentSeverity(long pullRequestId,
			long commentId, int version, @NonNull String severity)
			throws IOException;

	/**
	 * Updates the state of a comment (e.g., to resolve or reopen)
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param commentId
	 *            the comment ID
	 * @param version
	 *            the comment version (for optimistic locking, may be ignored
	 *            by some providers)
	 * @param state
	 *            the new state (e.g., "OPEN" or "RESOLVED")
	 * @return the updated comment
	 * @throws IOException
	 *             if the request fails
	 * @throws UnsupportedOperationException
	 *             if the provider doesn't support comment state
	 */
	@NonNull
	PullRequestComment updateCommentState(long pullRequestId, long commentId,
			int version, @NonNull String state) throws IOException;

	/**
	 * Edits an existing comment's text
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param commentId
	 *            the comment ID
	 * @param version
	 *            the comment version (for optimistic locking)
	 * @param newText
	 *            the new comment text
	 * @param isReviewComment
	 *            true if this is a review comment (inline), false for general
	 *            comments
	 * @return the updated comment
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	PullRequestComment editComment(long pullRequestId, long commentId,
			int version, @NonNull String newText, boolean isReviewComment)
			throws IOException;

	/**
	 * Deletes a comment
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param commentId
	 *            the comment ID
	 * @param version
	 *            the comment version (for optimistic locking)
	 * @param isReviewComment
	 *            true if this is a review comment (inline), false for general
	 *            comments
	 * @throws IOException
	 *             if the request fails
	 */
	void deleteComment(long pullRequestId, long commentId, int version,
			boolean isReviewComment) throws IOException;

	/**
	 * Updates the description of a pull request
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param version
	 *            the pull request version (for optimistic locking, may be
	 *            ignored by some providers like GitHub)
	 * @param description
	 *            the new description text
	 * @return the updated pull request
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	PullRequest updatePullRequestDescription(long pullRequestId, int version,
			@NonNull String description) throws IOException;

	/**
	 * Tests the connection to the provider
	 *
	 * @return true if the connection is successful
	 */
	boolean testConnection();

	/**
	 * Gets the current authenticated user's information
	 *
	 * @return the username or login of the authenticated user
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	String getCurrentUser() throws IOException;

	/**
	 * Retrieves the list of reviewers for a pull request.
	 *
	 * @param pullRequestId
	 *            the pull request identifier
	 * @return list of reviewers, never null
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	List<PullRequest.PullRequestParticipant> getReviewers(long pullRequestId)
			throws IOException;

	/**
	 * Adds a reviewer to a pull request.
	 *
	 * @param pullRequestId
	 *            the pull request identifier
	 * @param username
	 *            the username to add as reviewer
	 * @throws IOException
	 *             if the request fails
	 */
	void addReviewer(long pullRequestId, @NonNull String username)
			throws IOException;

	/**
	 * Removes a reviewer from a pull request.
	 *
	 * @param pullRequestId
	 *            the pull request identifier
	 * @param username
	 *            the username to remove
	 * @throws IOException
	 *             if the request fails
	 */
	void removeReviewer(long pullRequestId, @NonNull String username)
			throws IOException;

	/**
	 * Adds multiple reviewers to a pull request.
	 *
	 * @param pullRequestId
	 *            the pull request identifier
	 * @param usernames
	 *            the list of usernames to add as reviewers
	 * @throws IOException
	 *             if the request fails
	 */
	void addReviewers(long pullRequestId, @NonNull List<String> usernames)
			throws IOException;

	/**
	 * Submits a review for a pull request. The review event determines the
	 * action: approve, request changes, or leave a comment.
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @param event
	 *            the review event: "APPROVE", "REQUEST_CHANGES", or "COMMENT"
	 * @param body
	 *            optional review body text, may be null for approvals
	 * @throws IOException
	 *             if the request fails
	 * @throws UnsupportedOperationException
	 *             if the provider does not support review submission
	 */
	void submitReview(long pullRequestId, @NonNull String event,
			@Nullable String body) throws IOException;

	/**
	 * Removes the current user's approval from a pull request. For GitHub this
	 * dismisses the review; for Bitbucket this removes the approval.
	 *
	 * @param pullRequestId
	 *            the pull request ID or number
	 * @throws IOException
	 *             if the request fails
	 */
	void unapproveReview(long pullRequestId) throws IOException;

	/**
	 * Get the list of commits in the pull request.
	 *
	 * @param pullRequestId
	 *            the pull request ID
	 * @return list of commits in chronological order
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	List<PullRequestCommit> getPullRequestCommits(long pullRequestId)
			throws IOException;

	/**
	 * Retrieves changed files for a specific commit.
	 *
	 * @param commitSha
	 *            the commit SHA
	 * @return list of changed files in the commit
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	List<ChangedFile> getCommitChanges(@NonNull String commitSha)
			throws IOException;

	/**
	 * Retrieves changed files for a range of commits. The range is inclusive
	 * of both base and head commits.
	 *
	 * @param baseCommitSha
	 *            the starting commit SHA (older commit)
	 * @param headCommitSha
	 *            the ending commit SHA (newer commit)
	 * @return list of changed files across the commit range
	 * @throws IOException
	 *             if the request fails
	 */
	@NonNull
	List<ChangedFile> getCommitRangeChanges(@NonNull String baseCommitSha,
			@NonNull String headCommitSha) throws IOException;

	/**
	 * @return the capabilities of this provider, indicating which features are
	 *         supported
	 */
	@NonNull
	PullRequestProviderCapabilities getCapabilities();

	/**
	 * @return the provider type (BITBUCKET, GITHUB, etc.)
	 */
	@NonNull
	PullRequestProviderType getProviderType();
}
