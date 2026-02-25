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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.egit.pullrequest.internal.model.ChangedFile;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderCapabilities;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * REST client for GitHub API v3
 */
public class GitHubClient implements IPullRequestClient {

	private static final String API_BASE_URL = "https://api.github.com"; //$NON-NLS-1$

	private static final String GRAPHQL_URL = "https://api.github.com/graphql"; //$NON-NLS-1$

	private static final int DEFAULT_TIMEOUT = 30000; // 30 seconds

	private final String owner;

	private final String repo;

	private final String token;

	private final PullRequestProviderCapabilities capabilities;

	/**
	 * Creates a new GitHub client
	 *
	 * @param owner
	 *            the repository owner (user or organization)
	 * @param repo
	 *            the repository name
	 * @param token
	 *            the GitHub access token
	 */
	public GitHubClient(@NonNull String owner, @NonNull String repo,
			@NonNull String token) {
		this.owner = owner;
		this.repo = repo;
		this.token = token;
		this.capabilities = PullRequestProviderCapabilities
				.forProvider(PullRequestProviderType.GITHUB);
	}

	@Override
	public @NonNull PullRequestProviderType getProviderType() {
		return PullRequestProviderType.GITHUB;
	}

	@Override
	public @NonNull PullRequestProviderCapabilities getCapabilities() {
		return capabilities;
	}

	@Override
	public @NonNull List<PullRequest> getPullRequests(@Nullable String state,
			@Nullable String authorUsername, @Nullable String reviewerUsername,
			int limit, int start)
			throws IOException {
		StringBuilder urlBuilder = new StringBuilder();
		urlBuilder.append("/repos/").append(owner).append("/").append(repo) //$NON-NLS-1$ //$NON-NLS-2$
				.append("/pulls"); //$NON-NLS-1$

		// Build query parameters
		StringBuilder query = new StringBuilder();

		// Map state parameter (GitHub uses: open, closed, all)
		String githubState = "open"; //$NON-NLS-1$
		if (state != null) {
			if (state.equalsIgnoreCase("MERGED") || state.equalsIgnoreCase("DECLINED")) { //$NON-NLS-1$ //$NON-NLS-2$
				githubState = "closed"; //$NON-NLS-1$
			} else if (state.equalsIgnoreCase("ALL")) { //$NON-NLS-1$
				githubState = "all"; //$NON-NLS-1$
			} else {
				githubState = state.toLowerCase();
			}
		}
		query.append("state=").append(githubState); //$NON-NLS-1$

		// GitHub pagination: per_page and page
		if (limit > 0) {
			query.append("&per_page=").append(limit); //$NON-NLS-1$
		}
		if (start > 0) {
			// GitHub uses page numbers (1-indexed), not offset
			int page = (start / (limit > 0 ? limit : 30)) + 1;
			query.append("&page=").append(page); //$NON-NLS-1$
		}

		urlBuilder.append("?").append(query); //$NON-NLS-1$

		String json = doGet(urlBuilder.toString());
		List<PullRequest> pulls = GitHubJsonParser.parsePullRequests(json);

		// Filter by author if specified
		if (authorUsername != null && !authorUsername.isEmpty()) {
			pulls.removeIf(pr -> pr.getAuthor() == null
					|| pr.getAuthor().getUser() == null || !authorUsername
							.equals(pr.getAuthor().getUser().getName()));
		}

		// Note: GitHub API doesn't support filtering by reviewer directly
		// Would need to fetch reviews for each PR, which is inefficient
		// For now, reviewer filter is not implemented

		return pulls;
	}

	@Override
	public @NonNull PullRequest getPullRequest(long pullRequestId)
			throws IOException {
		String path = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId;
		String json = doGet(path);
		return GitHubJsonParser.parseSinglePullRequest(json);
	}

	@Override
	public @NonNull List<ChangedFile> getPullRequestChanges(long pullRequestId)
			throws IOException {
		String path = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId + "/files?per_page=100"; //$NON-NLS-1$
		List<String> pages = doGetAllPages(path);
		List<ChangedFile> result = new ArrayList<>();
		for (String page : pages) {
			result.addAll(GitHubJsonParser.parseChangedFiles(page));
		}
		return result;
	}

	@Override
	public @NonNull List<PullRequestComment> getPullRequestComments(
			long pullRequestId) throws IOException {
		// GitHub has two types of comments:
		// 1. Review comments (inline, on code)
		// 2. Issue comments (general PR comments)

		// Fetch all pages of review comments
		String reviewCommentsPath = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId + "/comments?per_page=100"; //$NON-NLS-1$
		List<String> reviewPages = doGetAllPages(reviewCommentsPath);

		// Fetch all pages of issue comments
		String issueCommentsPath = "/repos/" + owner + "/" + repo + "/issues/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId + "/comments?per_page=100"; //$NON-NLS-1$
		List<String> issuePages = doGetAllPages(issueCommentsPath);

		List<PullRequestComment> result = new ArrayList<>();
		for (String page : reviewPages) {
			result.addAll(
					GitHubJsonParser.parseComments(page, null));
		}
		for (String page : issuePages) {
			result.addAll(
					GitHubJsonParser.parseComments(null, page));
		}

		// Fetch thread resolution states via GraphQL and update comments
		updateCommentResolutionStates(pullRequestId, result);

		return result;
	}

	/**
	 * Updates the resolution state of comments by fetching thread information
	 * via GraphQL. GitHub's REST API doesn't include thread resolution status,
	 * so we need to query it separately.
	 *
	 * @param pullRequestId
	 *            the pull request number
	 * @param comments
	 *            the list of comments to update (modified in place)
	 * @throws IOException
	 *             if the GraphQL query fails
	 */
	private void updateCommentResolutionStates(long pullRequestId,
			List<PullRequestComment> comments) throws IOException {
		if (comments.isEmpty()) {
			return;
		}

		try {
			// Query all review threads with their resolution status
			// We need to paginate through all threads (GitHub limits to 100 per page)
		String query = "query { repository(owner: \"" + owner //$NON-NLS-1$
				+ "\", name: \"" + repo //$NON-NLS-1$
				+ "\") { pullRequest(number: " + pullRequestId //$NON-NLS-1$
				+ ") { reviewThreads(first: 100) { nodes { id isResolved " //$NON-NLS-1$
				+ "comments(first: 100) { nodes { databaseId } } } } } } }"; //$NON-NLS-1$

			Activator.logInfo("updateCommentResolutionStates: Executing GraphQL query for PR " //$NON-NLS-1$
					+ pullRequestId);
			Activator.logInfo("updateCommentResolutionStates: Query = " + query); //$NON-NLS-1$

			String result = executeGraphQL(query);
			
			Activator.logInfo("updateCommentResolutionStates: GraphQL response length: " //$NON-NLS-1$
					+ (result != null ? result.length() : 0));
			Activator.logInfo("updateCommentResolutionStates: GraphQL response: " //$NON-NLS-1$
					+ result);
			
			// Check for errors in response
			if (result != null && result.contains("\"errors\"")) { //$NON-NLS-1$
				Activator.logError("updateCommentResolutionStates: GraphQL returned errors: " //$NON-NLS-1$
						+ result, null);
			}

			// Parse the GraphQL response to build a map of comment ID -> resolved state
			java.util.Map<Long, Boolean> resolvedStates = parseThreadResolutionStates(
					result);

			Activator.logInfo("updateCommentResolutionStates: Parsed " //$NON-NLS-1$
					+ resolvedStates.size() + " comment resolution states"); //$NON-NLS-1$

			// Update comment states based on thread resolution
			for (PullRequestComment comment : comments) {
				// Only update review comments (inline comments)
				if (comment.isReviewComment()) {
					Boolean isResolved = resolvedStates.get(comment.getId());
					if (isResolved != null) {
						String newState = isResolved ? "RESOLVED" : "OPEN"; //$NON-NLS-1$ //$NON-NLS-2$
						comment.setState(newState);
						Activator.logInfo("updateCommentResolutionStates: Set comment " //$NON-NLS-1$
								+ comment.getId() + " state to " + newState); //$NON-NLS-1$
					} else {
						Activator.logInfo("updateCommentResolutionStates: No resolution state found for comment " //$NON-NLS-1$
								+ comment.getId());
					}
				}
			}
		} catch (IOException e) {
			// Log the error but don't fail the entire comment fetch
			// Comments will just show as OPEN if we can't get resolution state
			Activator.logError(
					"Failed to fetch thread resolution states: " //$NON-NLS-1$
							+ e.getMessage(),
					e);
		}
	}

	/**
	 * Parses GraphQL response to extract thread resolution states.
	 *
	 * @param graphqlResult
	 *            the GraphQL response JSON
	 * @return map of comment database ID to resolved state
	 */
	private java.util.Map<Long, Boolean> parseThreadResolutionStates(
			String graphqlResult) {
		java.util.Map<Long, Boolean> result = new java.util.HashMap<>();

		Activator.logInfo("parseThreadResolutionStates: Parsing GraphQL result"); //$NON-NLS-1$
		Activator.logInfo("parseThreadResolutionStates: Input length = " //$NON-NLS-1$
				+ (graphqlResult != null ? graphqlResult.length() : 0));
		
		if (graphqlResult == null || graphqlResult.isEmpty()) {
			Activator.logWarning("parseThreadResolutionStates: GraphQL result is null or empty"); //$NON-NLS-1$
			return result;
		}

		// Parse the JSON structure to find all threads and their comments
		// Structure: data.repository.pullRequest.reviewThreads.nodes[]
		// Each node has: id, isResolved, comments.nodes[].databaseId

		int nodesStart = graphqlResult.indexOf("\"nodes\":["); //$NON-NLS-1$
		Activator.logInfo("parseThreadResolutionStates: nodesStart index = " + nodesStart); //$NON-NLS-1$
		if (nodesStart == -1) {
			Activator.logWarning("parseThreadResolutionStates: No 'nodes' array found in GraphQL response"); //$NON-NLS-1$
			Activator.logWarning("parseThreadResolutionStates: Response snippet: " //$NON-NLS-1$
					+ graphqlResult.substring(0, Math.min(200, graphqlResult.length())));
			return result;
		}

		String nodesSection = graphqlResult.substring(nodesStart);
		String[] threadBlocks = nodesSection.split("\\{\"id\":"); //$NON-NLS-1$
		
		Activator.logInfo("parseThreadResolutionStates: Found " //$NON-NLS-1$
				+ (threadBlocks.length - 1) + " thread blocks"); //$NON-NLS-1$

		for (int i = 1; i < threadBlocks.length; i++) {
			String threadBlock = threadBlocks[i];

			// Extract isResolved
			boolean isResolved = threadBlock.contains("\"isResolved\":true"); //$NON-NLS-1$
			
			Activator.logInfo("parseThreadResolutionStates: Thread " + i //$NON-NLS-1$
					+ " isResolved=" + isResolved); //$NON-NLS-1$

			// Extract comment database IDs from this thread
			String commentNodesMarker = "\"comments\":{\"nodes\":["; //$NON-NLS-1$
			int commentStart = threadBlock.indexOf(commentNodesMarker);
			if (commentStart != -1) {
				String commentsSection = threadBlock.substring(
						commentStart + commentNodesMarker.length());
				String[] commentBlocks = commentsSection.split(
						"\\{\"databaseId\":"); //$NON-NLS-1$
				
				Activator.logInfo("parseThreadResolutionStates: Thread " + i //$NON-NLS-1$
						+ " has " + (commentBlocks.length - 1) + " comments"); //$NON-NLS-1$ //$NON-NLS-2$

				for (int j = 1; j < commentBlocks.length; j++) {
					String commentBlock = commentBlocks[j];
					int endIndex = commentBlock.indexOf('}');
					if (endIndex != -1) {
						try {
							long commentId = Long.parseLong(
									commentBlock.substring(0, endIndex));
							result.put(commentId, isResolved);
							Activator.logInfo("parseThreadResolutionStates: Mapped comment " //$NON-NLS-1$
									+ commentId + " -> " + isResolved); //$NON-NLS-1$
						} catch (NumberFormatException e) {
							// Skip malformed comment ID
							Activator.logWarning("parseThreadResolutionStates: Failed to parse comment ID from: " //$NON-NLS-1$
									+ commentBlock.substring(0, Math.min(50, endIndex)));
						}
					}
				}
			} else {
				Activator.logWarning("parseThreadResolutionStates: No comments found in thread " + i); //$NON-NLS-1$
			}
		}

		Activator.logInfo("parseThreadResolutionStates: Parsed " //$NON-NLS-1$
				+ result.size() + " total comment states"); //$NON-NLS-1$

		return result;
	}

	@Override
	public byte[] getFileContent(@NonNull String commitId,
			@NonNull String path) throws IOException {
		// GitHub API for getting file content at specific commit
		String apiPath = "/repos/" + owner + "/" + repo + "/contents/" + path //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ "?ref=" + commitId; //$NON-NLS-1$

		HttpURLConnection conn = null;
		try {
			conn = createConnection(apiPath, "GET"); //$NON-NLS-1$

			int responseCode = conn.getResponseCode();
			if (responseCode == 404) {
				// File doesn't exist at this commit (likely deleted)
				return new byte[0];
			}
			if (responseCode != 200) {
				throw new IOException(
						"Failed to get file content: HTTP " + responseCode); //$NON-NLS-1$
			}

			String json = readResponse(conn);
			return GitHubJsonParser.parseFileContent(json);

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	@Override
	public @NonNull PullRequestComment addComment(long pullRequestId,
			@NonNull String text, long parentCommentId)
			throws IOException {
		if (parentCommentId != -1) {
			// Reply to existing comment
			// GitHub API: POST /repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies
			String path = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ pullRequestId + "/comments/" + parentCommentId //$NON-NLS-1$
					+ "/replies"; //$NON-NLS-1$
			String body = "{\"body\":\"" + escapeJson(text) + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$
			String json = doPost(path, body);
			return GitHubJsonParser.parseSingleComment(json);
		} else {
			// Create general PR comment (issue comment)
			String path = "/repos/" + owner + "/" + repo + "/issues/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ pullRequestId + "/comments"; //$NON-NLS-1$
			String body = "{\"body\":\"" + escapeJson(text) + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$
			String json = doPost(path, body);
			return GitHubJsonParser.parseSingleComment(json);
		}
	}

	@Override
	public @NonNull PullRequestComment addInlineComment(long pullRequestId,
			@NonNull String text, @NonNull String path, int line,
			@NonNull String lineType, @NonNull String fileType,
			@NonNull String commitId) throws IOException {
		// GitHub API: POST /repos/{owner}/{repo}/pulls/{pull_number}/comments
		String apiPath = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId + "/comments"; //$NON-NLS-1$

		// Map fileType (FROM/TO) to GitHub's side (LEFT/RIGHT)
		// FROM = old file = LEFT side
		// TO = new file = RIGHT side
		String side = "FROM".equals(fileType) ? "LEFT" : "RIGHT"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		// Build JSON body
		StringBuilder body = new StringBuilder();
		body.append("{\"body\":\"").append(escapeJson(text)).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		body.append(",\"commit_id\":\"").append(commitId).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		body.append(",\"path\":\"").append(escapeJson(path)).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		body.append(",\"line\":").append(line); //$NON-NLS-1$
		body.append(",\"side\":\"").append(side).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		body.append("}"); //$NON-NLS-1$

		String json = doPost(apiPath, body.toString());
		return GitHubJsonParser.parseSingleComment(json);
	}

	@Override
	public @NonNull PullRequestComment updateCommentSeverity(
			long pullRequestId, long commentId, int commentVersion,
			@NonNull String severity) throws IOException {
		throw new UnsupportedOperationException(
				"GitHub does not support comment severity"); //$NON-NLS-1$
	}

	@Override
	public @NonNull PullRequestComment updateCommentState(long pullRequestId,
			long commentId, int commentVersion, @NonNull String state)
			throws IOException {
		// GitHub requires GraphQL to resolve/unresolve review threads
		// First, get the comment to find its node_id
		PullRequestComment comment = getCommentById(commentId);
		
		// Debug: Log the comment's threadId
		String commentThreadId = comment.getThreadId();
		Activator.logInfo("updateCommentState: commentId=" + commentId //$NON-NLS-1$
				+ ", threadId(node_id)=" + commentThreadId); //$NON-NLS-1$
		
		// Use GraphQL to find the thread ID from the comment's node_id
		String threadId = getThreadIdFromComment(commentThreadId);
		
		Activator.logInfo("updateCommentState: resolved threadId=" + threadId); //$NON-NLS-1$
		
		if (threadId == null || threadId.isEmpty()) {
			throw new IOException(
					"Unable to find review thread for comment " + commentId); //$NON-NLS-1$
		}

		// Map state to GraphQL mutation
		boolean shouldResolve = "RESOLVED".equalsIgnoreCase(state); //$NON-NLS-1$
		String mutationName = shouldResolve ? "resolveReviewThread" //$NON-NLS-1$
				: "unresolveReviewThread"; //$NON-NLS-1$

		// Build GraphQL mutation
		String mutation = "mutation { " + mutationName + "(input: {threadId: \"" //$NON-NLS-1$ //$NON-NLS-2$
				+ threadId + "\"}) { thread { isResolved } } }"; //$NON-NLS-1$

		// Execute GraphQL mutation
		String result = executeGraphQL(mutation);
		
		// Verify mutation succeeded
		if (!result.contains("\"isResolved\":" + shouldResolve)) { //$NON-NLS-1$
			throw new IOException("Failed to update comment state: " + result); //$NON-NLS-1$
		}

		// Update comment state and return
		comment.setState(state);
		return comment;
	}

	/**
	 * Fetches the thread ID for a given comment node_id using GraphQL
	 *
	 * @param commentNodeId
	 *            the comment's node_id
	 * @return the thread's node_id, or null if not found
	 * @throws IOException
	 *             if the request fails
	 */
	private String getThreadIdFromComment(String commentNodeId)
			throws IOException {
		if (commentNodeId == null || commentNodeId.isEmpty()) {
			Activator.logInfo("getThreadIdFromComment: commentNodeId is null or empty"); //$NON-NLS-1$
			return null;
		}

		// GitHub's GraphQL schema: PullRequestReviewComment doesn't have a direct pullRequestReviewThread field
		// Instead, we need to query the comment to get its pull request, then find the thread containing this comment
		String query = "query { node(id: \"" + commentNodeId //$NON-NLS-1$
				+ "\") { ... on PullRequestReviewComment { " //$NON-NLS-1$
				+ "databaseId pullRequest { reviewThreads(first: 100) { " //$NON-NLS-1$
				+ "nodes { id comments(first: 100) { nodes { databaseId } } } } } } } }"; //$NON-NLS-1$

		String result = executeGraphQL(query);
		
		Activator.logInfo("getThreadIdFromComment: GraphQL result=" + result); //$NON-NLS-1$
		
		// Extract the comment's database ID from the result
		long commentDbId = extractCommentDatabaseId(result);
		if (commentDbId == -1) {
			Activator.logInfo("getThreadIdFromComment: Could not extract comment databaseId"); //$NON-NLS-1$
			return null;
		}
		
		// Find the thread that contains this comment
		String threadId = extractThreadIdContainingComment(result, commentDbId);
		
		if (threadId == null) {
			Activator.logInfo("getThreadIdFromComment: No thread found containing comment " + commentDbId); //$NON-NLS-1$
		} else {
			Activator.logInfo("getThreadIdFromComment: extracted threadId=" + threadId); //$NON-NLS-1$
		}
		
		return threadId;
	}

	/**
	 * Extracts the databaseId of the comment from GraphQL result
	 */
	private long extractCommentDatabaseId(String result) {
		String marker = "\"databaseId\":"; //$NON-NLS-1$
		int start = result.indexOf(marker);
		if (start == -1) {
			return -1;
		}
		start += marker.length();
		
		// Skip whitespace and find end of number
		while (start < result.length() && Character.isWhitespace(result.charAt(start))) {
			start++;
		}
		
		int end = start;
		while (end < result.length() && Character.isDigit(result.charAt(end))) {
			end++;
		}
		
		if (end == start) {
			return -1;
		}
		
		try {
			return Long.parseLong(result.substring(start, end));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * Extracts the thread ID that contains the given comment database ID
	 */
	private String extractThreadIdContainingComment(String result, long commentDbId) {
		// Parse through the reviewThreads nodes to find which thread contains our comment
		// JSON structure: reviewThreads: { nodes: [ { id: "...", comments: { nodes: [ { databaseId: ... } ] } } ] }
		
		int nodesStart = result.indexOf("\"reviewThreads\""); //$NON-NLS-1$
		if (nodesStart == -1) {
			return null;
		}
		
		// Find all thread objects
		int pos = nodesStart;
		while (pos < result.length()) {
			// Find next thread ID
			int threadIdPos = result.indexOf("\"id\":\"", pos); //$NON-NLS-1$
			if (threadIdPos == -1) {
				break;
			}
			
			int threadIdStart = threadIdPos + 6; // length of "\"id\":\""
			int threadIdEnd = result.indexOf("\"", threadIdStart); //$NON-NLS-1$
			if (threadIdEnd == -1) {
				break;
			}
			
			String threadId = result.substring(threadIdStart, threadIdEnd);
			
			// Check if this thread contains our comment
			// Find the comments array for this thread
			int commentsStart = result.indexOf("\"comments\"", threadIdStart); //$NON-NLS-1$
			if (commentsStart == -1 || commentsStart > result.indexOf("\"id\":\"", threadIdEnd)) { //$NON-NLS-1$
				// No more comments in this thread or we've moved to next thread
				pos = threadIdEnd;
				continue;
			}
			
			// Look for our comment's databaseId within this thread's comments
			int nextThreadPos = result.indexOf("\"id\":\"", threadIdEnd); //$NON-NLS-1$
			int searchEnd = (nextThreadPos == -1) ? result.length() : nextThreadPos;
			String threadSection = result.substring(commentsStart, searchEnd);
			
			// Check if this thread section contains our comment ID
			String commentIdMarker = "\"databaseId\":" + commentDbId; //$NON-NLS-1$
			if (threadSection.contains(commentIdMarker)) {
				return threadId;
			}
			
			pos = threadIdEnd;
		}
		
		return null;
	}

	/**
	 * Fetches a single comment by ID
	 *
	 * @param commentId
	 *            the comment ID
	 * @return the comment
	 * @throws IOException
	 *             if the request fails
	 */
	private PullRequestComment getCommentById(long commentId)
			throws IOException {
		// Try review comment endpoint first
		try {
			String path = "/repos/" + owner + "/" + repo + "/pulls/comments/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ commentId;
			String json = doGet(path);
			return GitHubJsonParser.parseSingleComment(json);
		} catch (IOException e) {
			// If not found, try issue comment endpoint
			String path = "/repos/" + owner + "/" + repo + "/issues/comments/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ commentId;
			String json = doGet(path);
			return GitHubJsonParser.parseSingleComment(json);
		}
	}

	@Override
	public @NonNull PullRequestComment editComment(long pullRequestId,
			long commentId, int version, @NonNull String newText,
			boolean isReviewComment) throws IOException {
		// GitHub API for editing comments:
		// Review comments: PATCH /repos/{owner}/{repo}/pulls/comments/{comment_id}
		// Issue comments: PATCH /repos/{owner}/{repo}/issues/comments/{comment_id}
		
		String path;
		if (isReviewComment) {
			path = "/repos/" + owner + "/" + repo + "/pulls/comments/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ commentId;
		} else {
			path = "/repos/" + owner + "/" + repo + "/issues/comments/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ commentId;
		}

		String body = "{\"body\":\"" + escapeJson(newText) + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$
		String json = doPatch(path, body);
		return GitHubJsonParser.parseSingleComment(json);
	}

	@Override
	public void deleteComment(long pullRequestId, long commentId, int version,
			boolean isReviewComment) throws IOException {
		// GitHub API for deleting comments:
		// Review comments: DELETE /repos/{owner}/{repo}/pulls/comments/{comment_id}
		// Issue comments: DELETE /repos/{owner}/{repo}/issues/comments/{comment_id}
		
		String path;
		if (isReviewComment) {
			path = "/repos/" + owner + "/" + repo + "/pulls/comments/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ commentId;
		} else {
			path = "/repos/" + owner + "/" + repo + "/issues/comments/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ commentId;
		}

		doDelete(path);
	}

	@Override
	public @NonNull PullRequest updatePullRequestDescription(
			long pullRequestId, int version, @NonNull String description)
			throws IOException {
		// GitHub API: PATCH /repos/{owner}/{repo}/pulls/{pull_number}
		// Body: {"body": "new description"}
		// Version parameter is ignored for GitHub (no optimistic locking)

		String path = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId;
		String body = "{\"body\":\"" + escapeJson(description) + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$
		String json = doPatch(path, body);
		return GitHubJsonParser.parseSinglePullRequest(json);
	}

	@Override
	public boolean testConnection() {
		try {
			// Try to get the repository info
			String path = "/repos/" + owner + "/" + repo; //$NON-NLS-1$ //$NON-NLS-2$
			doGet(path);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public @NonNull String getCurrentUser() throws IOException {
		String json = doGet("/user"); //$NON-NLS-1$
		return GitHubJsonParser.parseCurrentUser(json);
	}

	@Override
	public @NonNull List<PullRequestCommit> getPullRequestCommits(
			long pullRequestId) throws IOException {
		String path = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId + "/commits?per_page=100"; //$NON-NLS-1$

		List<String> pages = doGetAllPages(path);
		List<PullRequestCommit> allCommits = new ArrayList<>();

		for (String page : pages) {
			List<PullRequestCommit> commits = GitHubJsonParser
					.parseCommits(page);
			allCommits.addAll(commits);
		}

		return allCommits;
	}

	/**
	 * Performs a GET request to the GitHub API
	 *
	 * @param path
	 *            the API path (relative to API base URL)
	 * @return the response body as string
	 * @throws IOException
	 *             if the request fails
	 */
	private String doGet(String path) throws IOException {
		HttpURLConnection conn = null;
		try {
			conn = createConnection(path, "GET"); //$NON-NLS-1$

			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				String error = readError(conn);
				throw new IOException(
						"GitHub API request failed: HTTP " + responseCode //$NON-NLS-1$
								+ " - " + error); //$NON-NLS-1$
			}

			return readResponse(conn);

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private static final Pattern LINK_NEXT_PATTERN = Pattern
			.compile("<([^>]+)>;\\s*rel=\"next\""); //$NON-NLS-1$

	/**
	 * Performs paginated GET requests to the GitHub API, following {@code Link}
	 * header pagination until all pages have been fetched.
	 *
	 * @param path
	 *            the API path (relative to API base URL), should include
	 *            {@code per_page} query parameter for optimal page size
	 * @return list of JSON response bodies, one per page
	 * @throws IOException
	 *             if any request fails
	 */
	private List<String> doGetAllPages(String path) throws IOException {
		List<String> pages = new ArrayList<>();
		String nextUrl = API_BASE_URL + path;

		while (nextUrl != null) {
			HttpURLConnection conn = null;
			try {
				URL url = new URL(nextUrl);
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET"); //$NON-NLS-1$
				conn.setConnectTimeout(DEFAULT_TIMEOUT);
				conn.setReadTimeout(DEFAULT_TIMEOUT);
				conn.setRequestProperty("Authorization", //$NON-NLS-1$
						"Bearer " + token); //$NON-NLS-1$
				conn.setRequestProperty("Accept", //$NON-NLS-1$
						"application/vnd.github+json"); //$NON-NLS-1$
				conn.setRequestProperty("X-GitHub-Api-Version", //$NON-NLS-1$
						"2022-11-28"); //$NON-NLS-1$
				conn.setRequestProperty("Content-Type", //$NON-NLS-1$
						"application/json"); //$NON-NLS-1$

				int responseCode = conn.getResponseCode();
				if (responseCode != 200) {
					String error = readError(conn);
					throw new IOException(
							"GitHub API request failed: HTTP " + responseCode //$NON-NLS-1$
									+ " - " + error); //$NON-NLS-1$
				}

				pages.add(readResponse(conn));
				nextUrl = parseLinkNext(conn);
			} finally {
				if (conn != null) {
					conn.disconnect();
				}
			}
		}
		return pages;
	}

	/**
	 * Parses the {@code Link} response header to extract the URL for the next
	 * page of results.
	 *
	 * @param conn
	 *            the HTTP connection
	 * @return the URL for the next page, or {@code null} if there is no next
	 *         page
	 */
	private static String parseLinkNext(HttpURLConnection conn) {
		String linkHeader = conn.getHeaderField("Link"); //$NON-NLS-1$
		if (linkHeader == null) {
			return null;
		}
		Matcher matcher = LINK_NEXT_PATTERN.matcher(linkHeader);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * Performs a POST request to the GitHub API
	 *
	 * @param path
	 *            the API path (relative to API base URL)
	 * @param body
	 *            the request body (JSON)
	 * @return the response body as string
	 * @throws IOException
	 *             if the request fails
	 */
	private String doPost(String path, String body) throws IOException {
		HttpURLConnection conn = null;
		try {
			conn = createConnection(path, "POST"); //$NON-NLS-1$
			conn.setDoOutput(true);

			// Write request body
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}

			int responseCode = conn.getResponseCode();
			if (responseCode != 200 && responseCode != 201) {
				String error = readError(conn);
				throw new IOException(
						"GitHub API request failed: HTTP " + responseCode //$NON-NLS-1$
								+ " - " + error); //$NON-NLS-1$
			}

			return readResponse(conn);

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Performs a PATCH request to the GitHub API
	 *
	 * @param path
	 *            the API path (relative to API base URL)
	 * @param body
	 *            the request body (JSON)
	 * @return the response body as string
	 * @throws IOException
	 *             if the request fails
	 */
	private String doPatch(String path, String body) throws IOException {
		HttpURLConnection conn = null;
		try {
			conn = createConnection(path, "PATCH"); //$NON-NLS-1$
			conn.setDoOutput(true);

			// Write request body
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}

			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				String error = readError(conn);
				throw new IOException(
						"GitHub API request failed: HTTP " + responseCode //$NON-NLS-1$
								+ " - " + error); //$NON-NLS-1$
			}

			return readResponse(conn);

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Performs a PUT request to the GitHub API
	 *
	 * @param path
	 *            the API path (relative to API base URL)
	 * @param body
	 *            the JSON body to send
	 * @return the response body
	 * @throws IOException
	 *             if the request fails
	 */
	private String doPut(String path, String body) throws IOException {
		HttpURLConnection conn = null;
		try {
			conn = createConnection(path, "PUT"); //$NON-NLS-1$
			conn.setDoOutput(true);

			// Write request body
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}

			int responseCode = conn.getResponseCode();
			if (responseCode < 200 || responseCode >= 300) {
				String error = readError(conn);
				throw new IOException(
						"GitHub API request failed: HTTP " + responseCode //$NON-NLS-1$
								+ " - " + error); //$NON-NLS-1$
			}

			if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
				return ""; //$NON-NLS-1$
			}

			return readResponse(conn);

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Performs a DELETE request to the GitHub API
	 *
	 * @param path
	 *            the API path (relative to API base URL)
	 * @throws IOException
	 *             if the request fails
	 */
	private void doDelete(String path) throws IOException {
		HttpURLConnection conn = null;
		try {
			conn = createConnection(path, "DELETE"); //$NON-NLS-1$

			int responseCode = conn.getResponseCode();
			if (responseCode != 204 && responseCode != 200) {
				String error = readError(conn);
				throw new IOException(
						"GitHub API request failed: HTTP " + responseCode //$NON-NLS-1$
								+ " - " + error); //$NON-NLS-1$
			}

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Execute a generic HTTP request
	 *
	 * @param path
	 *            the API path (relative to API base URL)
	 * @param method
	 *            the HTTP method (GET, POST, PUT, PATCH, DELETE)
	 * @param jsonBody
	 *            the JSON body (can be null for GET/DELETE)
	 * @return the response string, or empty string for DELETE
	 * @throws IOException
	 *             if the request fails
	 */
	private String executeRequest(String path, String method,
			String jsonBody) throws IOException {
		if ("DELETE".equals(method)) { //$NON-NLS-1$
			doDelete(path);
			return ""; //$NON-NLS-1$
		} else if ("POST".equals(method)) { //$NON-NLS-1$
			return doPost(path, jsonBody);
		} else if ("PATCH".equals(method)) { //$NON-NLS-1$
			return doPatch(path, jsonBody);
		} else if ("GET".equals(method)) { //$NON-NLS-1$
			return doGet(path);
		} else {
			throw new IOException("Unsupported HTTP method: " + method); //$NON-NLS-1$
		}
	}

	/**
	 * Executes a GraphQL query or mutation against GitHub's GraphQL API
	 *
	 * @param query
	 *            the GraphQL query or mutation string
	 * @return the response body as string
	 * @throws IOException
	 *             if the request fails
	 */
	private String executeGraphQL(String query) throws IOException {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(GRAPHQL_URL);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST"); //$NON-NLS-1$
			conn.setConnectTimeout(DEFAULT_TIMEOUT);
			conn.setReadTimeout(DEFAULT_TIMEOUT);
			conn.setDoOutput(true);

			// Set headers for GraphQL
			conn.setRequestProperty("Authorization", "Bearer " + token); //$NON-NLS-1$ //$NON-NLS-2$
			conn.setRequestProperty("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$

			// Build GraphQL request body
			String requestBody = "{\"query\":\"" + escapeJson(query) + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$

			// Write request body
			try (OutputStream os = conn.getOutputStream()) {
				byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			}

			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				String error = readError(conn);
				throw new IOException(
						"GitHub GraphQL request failed: HTTP " + responseCode //$NON-NLS-1$
								+ " - " + error); //$NON-NLS-1$
			}

			return readResponse(conn);

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Creates an HTTP connection to the GitHub API
	 *
	 * @param path
	 *            the API path
	 * @param method
	 *            the HTTP method
	 * @return the configured connection
	 * @throws IOException
	 *             if connection setup fails
	 */
	private HttpURLConnection createConnection(String path, String method)
			throws IOException {
		URL url = new URL(API_BASE_URL + path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		// HttpURLConnection doesn't support PATCH by default in Java
		// Use POST with X-HTTP-Method-Override header as a workaround
		boolean usePatchWorkaround = "PATCH".equals(method); //$NON-NLS-1$
		if (usePatchWorkaround) {
			conn.setRequestMethod("POST"); //$NON-NLS-1$
		} else {
			conn.setRequestMethod(method);
		}

		conn.setConnectTimeout(DEFAULT_TIMEOUT);
		conn.setReadTimeout(DEFAULT_TIMEOUT);

		// Set headers
		conn.setRequestProperty("Authorization", "Bearer " + token); //$NON-NLS-1$ //$NON-NLS-2$
		conn.setRequestProperty("Accept", "application/vnd.github+json"); //$NON-NLS-1$ //$NON-NLS-2$
		conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); //$NON-NLS-1$ //$NON-NLS-2$
		conn.setRequestProperty("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$

		// Tell GitHub to treat POST as PATCH
		if (usePatchWorkaround) {
			conn.setRequestProperty("X-HTTP-Method-Override", "PATCH"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return conn;
	}

	/**
	 * Reads the response body from a connection
	 *
	 * @param conn
	 *            the connection
	 * @return the response as string
	 * @throws IOException
	 *             if reading fails
	 */
	private String readResponse(HttpURLConnection conn) throws IOException {
		try (InputStream is = conn.getInputStream();
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(is, StandardCharsets.UTF_8))) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line).append('\n');
			}
			return response.toString();
		}
	}

	/**
	 * Reads the error response from a connection
	 *
	 * @param conn
	 *            the connection
	 * @return the error message
	 */
	private String readError(HttpURLConnection conn) {
		try (InputStream es = conn.getErrorStream()) {
			if (es == null) {
				return "Unknown error"; //$NON-NLS-1$
			}
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(es, StandardCharsets.UTF_8));
			StringBuilder error = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				error.append(line).append('\n');
			}
			return error.toString();
		} catch (IOException e) {
			return "Error reading error response: " + e.getMessage(); //$NON-NLS-1$
		}
	}

	/**
	 * Escapes a string for use in JSON
	 *
	 * @param text
	 *            the text to escape
	 * @return the escaped text
	 */
	private String escapeJson(String text) {
		return text.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\"", "\\\"") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\n", "\\n") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\r", "\\r") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\t", "\\t"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public void submitReview(long pullRequestId, @NonNull String event,
			@Nullable String body) throws IOException {
		String path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/pulls/" + pullRequestId + "/reviews"; //$NON-NLS-1$ //$NON-NLS-2$

		String json = body != null && !body.isEmpty()
				? "{\"event\":\"" + escapeJson(event) + "\",\"body\":\"" //$NON-NLS-1$ //$NON-NLS-2$
						+ escapeJson(body) + "\"}" //$NON-NLS-1$
				: "{\"event\":\"" + escapeJson(event) + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$

		doPost(path, json);
	}

	@Override
	public void unapproveReview(long pullRequestId) throws IOException {
		// GitHub: dismiss the latest APPROVED review by current user
		// First, list reviews to find the latest approval
		String path = "/repos/" + owner + "/" + repo //$NON-NLS-1$ //$NON-NLS-2$
				+ "/pulls/" + pullRequestId + "/reviews"; //$NON-NLS-1$ //$NON-NLS-2$
		List<String> pages = doGetAllPages(path);

		// Find the latest review with state "APPROVED" by current user
		String currentUser = getCurrentUser();
		long reviewId = -1;
		for (String page : pages) {
			long id = findLatestApprovalReviewId(page, currentUser);
			if (id > reviewId) {
				reviewId = id;
			}
		}
		if (reviewId == -1) {
			return; // No approval to dismiss
		}

		// Dismiss the review
		String dismissPath = path + "/" + reviewId + "/dismissals"; //$NON-NLS-1$ //$NON-NLS-2$
		String dismissBody = "{\"message\":\"Review dismissed\"}"; //$NON-NLS-1$
		// GitHub uses PUT to dismiss
		doPut(dismissPath, dismissBody);
	}

	/**
	 * Finds the ID of the latest APPROVED review by the given user from a
	 * reviews JSON array response.
	 *
	 * @param reviewsJson
	 *            the JSON array of reviews
	 * @param username
	 *            the username to match
	 * @return the review ID, or -1 if not found
	 */
	private long findLatestApprovalReviewId(String reviewsJson,
			String username) {
		long latestId = -1;
		int idx = 0;
		while (true) {
			int objStart = reviewsJson.indexOf('{', idx);
			if (objStart == -1) {
				break;
			}
			int objEnd = findMatchingBrace(reviewsJson, objStart);
			if (objEnd == -1) {
				break;
			}
			String obj = reviewsJson.substring(objStart, objEnd + 1);
			String state = GitHubJsonParser.extractString(obj, "state"); //$NON-NLS-1$
			String userObj = GitHubJsonParser.extractObject(obj, "user"); //$NON-NLS-1$
			String login = userObj != null
					? GitHubJsonParser.extractString(userObj, "login") //$NON-NLS-1$
					: null;
			if ("APPROVED".equals(state) //$NON-NLS-1$
					&& username.equals(login)) {
				long id = GitHubJsonParser.extractLong(obj, "id"); //$NON-NLS-1$
				if (id > latestId) {
					latestId = id;
				}
			}
			idx = objEnd + 1;
		}
		return latestId;
	}

	/**
	 * Finds the matching closing brace for an opening brace.
	 *
	 * @param json
	 *            the JSON string
	 * @param startIdx
	 *            the index of the opening brace
	 * @return the index of the matching closing brace, or -1 if not found
	 */
	private int findMatchingBrace(String json, int startIdx) {
		int depth = 0;
		boolean inString = false;
		boolean escaped = false;
		for (int i = startIdx; i < json.length(); i++) {
			char c = json.charAt(i);
			if (escaped) {
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}
			if (c == '"') {
				inString = !inString;
				continue;
			}
			if (!inString) {
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	@Override
	public @NonNull List<PullRequest.PullRequestParticipant> getReviewers(
			long pullRequestId) throws IOException {
		// Get the full PR which includes reviewers
		PullRequest pr = getPullRequest(pullRequestId);
		List<PullRequest.PullRequestParticipant> reviewers = pr
				.getReviewers();
		return reviewers != null ? reviewers
				: java.util.Collections.emptyList();
	}

	@Override
	public void addReviewer(long pullRequestId, @NonNull String username)
			throws IOException {
		// GitHub supports adding a single reviewer or multiple in one call
		List<String> reviewers = new ArrayList<>();
		reviewers.add(username);
		addReviewers(pullRequestId, reviewers);
	}

	@Override
	public void removeReviewer(long pullRequestId, @NonNull String username)
			throws IOException {
		String path = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId + "/requested_reviewers"; //$NON-NLS-1$

		// Build JSON body with reviewers array
		StringBuilder json = new StringBuilder();
		json.append("{\"reviewers\":[\""); //$NON-NLS-1$
		json.append(escapeJson(username));
		json.append("\"]}"); //$NON-NLS-1$

		executeRequest(path, "DELETE", json.toString()); //$NON-NLS-1$
	}

	@Override
	public void addReviewers(long pullRequestId,
			@NonNull List<String> usernames) throws IOException {
		String path = "/repos/" + owner + "/" + repo + "/pulls/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ pullRequestId + "/requested_reviewers"; //$NON-NLS-1$

		// Build JSON body with reviewers array
		StringBuilder json = new StringBuilder();
		json.append("{\"reviewers\":["); //$NON-NLS-1$
		for (int i = 0; i < usernames.size(); i++) {
			if (i > 0) {
				json.append(","); //$NON-NLS-1$
			}
			json.append("\""); //$NON-NLS-1$
			json.append(escapeJson(usernames.get(i)));
			json.append("\""); //$NON-NLS-1$
		}
		json.append("]}"); //$NON-NLS-1$

		executeRequest(path, "POST", json.toString()); //$NON-NLS-1$
	}
}
