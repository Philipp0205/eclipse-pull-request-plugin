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
