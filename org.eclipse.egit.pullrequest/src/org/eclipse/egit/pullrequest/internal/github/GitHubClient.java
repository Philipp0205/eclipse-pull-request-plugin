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
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderCapabilities;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * REST client for GitHub API v3
 */
public class GitHubClient implements IPullRequestClient {

	private static final String API_BASE_URL = "https://api.github.com"; //$NON-NLS-1$

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
		// GitHub doesn't have a direct "resolve comment" API
		// Comments can be marked as resolved via the review thread API
		// For now, we'll throw UnsupportedOperationException
		// TODO: Implement via GraphQL or review thread resolution
		throw new UnsupportedOperationException(
				"GitHub comment state updates not yet implemented"); //$NON-NLS-1$
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
}
