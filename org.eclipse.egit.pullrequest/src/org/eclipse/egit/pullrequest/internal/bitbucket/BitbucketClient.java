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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderCapabilities;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.egit.pullrequest.internal.model.ChangedFile;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * REST client for Bitbucket Data Center API
 */
public class BitbucketClient implements IPullRequestClient {

	private static final String API_BASE_PATH = "/rest/api/1.0"; //$NON-NLS-1$

	private static final int DEFAULT_TIMEOUT = 30000; // 30 seconds

	private final String serverUrl;

	private final String projectKey;

	private final String repositorySlug;

	private final String token;

	private final PullRequestProviderCapabilities capabilities;

	/**
	 * Creates a new Bitbucket client
	 *
	 * @param serverUrl
	 *            the Bitbucket server URL (e.g., https://bitbucket.example.com)
	 * @param projectKey
	 *            the project key (e.g., "PROJ")
	 * @param repositorySlug
	 *            the repository slug (e.g., "my-repo")
	 * @param token
	 *            the personal access token for authentication
	 */
	public BitbucketClient(@NonNull String serverUrl,
			@NonNull String projectKey, @NonNull String repositorySlug,
			@NonNull String token) {
		this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl; //$NON-NLS-1$
		this.projectKey = projectKey;
		this.repositorySlug = repositorySlug;
		this.token = token;
		this.capabilities = new PullRequestProviderCapabilities(true, true,
				true, true,
				"OPEN", "MERGED", "DECLINED", "ALL"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	@Override
	@NonNull
	public PullRequestProviderType getProviderType() {
		return PullRequestProviderType.BITBUCKET;
	}

	@Override
	@NonNull
	public PullRequestProviderCapabilities getCapabilities() {
		return capabilities;
	}

	@Override
	@NonNull
	public List<PullRequest> getPullRequests(@Nullable String state,
			@Nullable String authorUsername, @Nullable String reviewerUsername,
			int limit, int start) throws IOException {
		StringBuilder urlBuilder = new StringBuilder();
		urlBuilder.append(serverUrl).append(API_BASE_PATH)
				.append("/projects/").append(projectKey) //$NON-NLS-1$
				.append("/repos/").append(repositorySlug) //$NON-NLS-1$
				.append("/pull-requests"); //$NON-NLS-1$

		urlBuilder.append("?limit=").append(Math.min(limit, 1000)); //$NON-NLS-1$
		urlBuilder.append("&start=").append(start); //$NON-NLS-1$

		if (state != null && !state.isEmpty() && !"ALL".equals(state)) { //$NON-NLS-1$
			urlBuilder.append("&state=").append(state); //$NON-NLS-1$
		}

		if (authorUsername != null && !authorUsername.isEmpty()) {
			urlBuilder.append("&username.1=").append(authorUsername); //$NON-NLS-1$
			urlBuilder.append("&role.1=AUTHOR"); //$NON-NLS-1$
		}

		if (reviewerUsername != null && !reviewerUsername.isEmpty()) {
			urlBuilder.append("&participant.username=") //$NON-NLS-1$
					.append(reviewerUsername);
		}

		String url = urlBuilder.toString();
		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parsePullRequests(jsonResponse);
	}

	@Override
	@NonNull
	public PullRequest getPullRequest(long pullRequestId) throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId; //$NON-NLS-1$

		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parseSinglePullRequest(jsonResponse);
	}

	@Override
	@NonNull
	public List<ChangedFile> getPullRequestChanges(long pullRequestId)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId + "/changes"; //$NON-NLS-1$ //$NON-NLS-2$

		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parseChangedFiles(jsonResponse);
	}

	@Override
	@NonNull
	public List<PullRequestComment> getPullRequestComments(long pullRequestId)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId + "/activities"; //$NON-NLS-1$ //$NON-NLS-2$

		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parseActivities(jsonResponse);
	}

	@Override
	@NonNull
	public byte[] getFileContent(@NonNull String commitId, @NonNull String path)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/raw/" + path + "?at=" + commitId; //$NON-NLS-1$ //$NON-NLS-2$

		return executeGetBinary(url);
	}

	@Override
	@NonNull
	public PullRequestComment addComment(long pullRequestId,
			@NonNull String text, long parentCommentId) throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId + "/comments"; //$NON-NLS-1$ //$NON-NLS-2$

		StringBuilder json = new StringBuilder();
		json.append("{\"text\": \"").append(escapeJson(text)).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		if (parentCommentId >= 0) {
			json.append(", \"parent\": {\"id\": ").append(parentCommentId) //$NON-NLS-1$
					.append("}"); //$NON-NLS-1$
		}
		json.append("}"); //$NON-NLS-1$

		String jsonResponse = executePost(url, json.toString());
		return BitbucketJsonParser.parseSingleComment(jsonResponse);
	}

	@Override
	@NonNull
	public PullRequestComment addInlineComment(long pullRequestId,
			@NonNull String text, @NonNull String path, int line,
			@NonNull String lineType, @NonNull String fileType,
			@NonNull String commitId) throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId + "/comments"; //$NON-NLS-1$ //$NON-NLS-2$

		// Build JSON with anchor object
		StringBuilder json = new StringBuilder();
		json.append("{\"text\": \"").append(escapeJson(text)).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		json.append(", \"anchor\": {"); //$NON-NLS-1$
		json.append("\"path\": \"").append(escapeJson(path)).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		json.append(", \"line\": ").append(line); //$NON-NLS-1$
		json.append(", \"lineType\": \"").append(lineType).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		json.append(", \"fileType\": \"").append(fileType).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
		json.append("}"); //$NON-NLS-1$
		json.append("}"); //$NON-NLS-1$

		String jsonResponse = executePost(url, json.toString());
		return BitbucketJsonParser.parseSingleComment(jsonResponse);
	}

	@Override
	@NonNull
	public PullRequestComment updateCommentSeverity(long pullRequestId,
			long commentId, int version, @NonNull String severity)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
				+ "/comments/" + commentId; //$NON-NLS-1$

		String json = "{\"severity\": \"" + severity + "\", \"version\": " //$NON-NLS-1$ //$NON-NLS-2$
				+ version + "}"; //$NON-NLS-1$

		String jsonResponse = executePut(url, json);
		return BitbucketJsonParser.parseSingleComment(jsonResponse);
	}

	@Override
	@NonNull
	public PullRequestComment updateCommentState(long pullRequestId,
			long commentId, int version, @NonNull String state)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
				+ "/comments/" + commentId; //$NON-NLS-1$

		String json = "{\"state\": \"" + state + "\", \"version\": " //$NON-NLS-1$ //$NON-NLS-2$
				+ version + "}"; //$NON-NLS-1$

		String jsonResponse = executePut(url, json);
		return BitbucketJsonParser.parseSingleComment(jsonResponse);
	}

	@Override
	@NonNull
	public PullRequestComment editComment(long pullRequestId, long commentId,
			int version, @NonNull String newText, boolean isReviewComment)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
				+ "/comments/" + commentId; //$NON-NLS-1$

		String json = "{\"text\": \"" + escapeJson(newText) //$NON-NLS-1$
				+ "\", \"version\": " //$NON-NLS-1$
				+ version + "}"; //$NON-NLS-1$

		String jsonResponse = executePut(url, json);
		return BitbucketJsonParser.parseSingleComment(jsonResponse);
	}

	@Override
	public void deleteComment(long pullRequestId, long commentId, int version,
			boolean isReviewComment) throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
				+ "/comments/" + commentId //$NON-NLS-1$
				+ "?version=" + version; //$NON-NLS-1$

		executeDelete(url);
	}

	@Override
	@NonNull
	public PullRequest updatePullRequestDescription(long pullRequestId,
			int version, @NonNull String description) throws IOException {
		// Bitbucket API: PUT /rest/api/1.0/projects/{key}/repos/{slug}/pull-requests/{id}
		// Body requires: {"title": "...", "description": "...", "version": N}
		// Need to fetch current PR first to get the title

		PullRequest currentPr = getPullRequest(pullRequestId);

		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId; //$NON-NLS-1$

		String json = "{\"title\": \"" + escapeJson(currentPr.getTitle()) //$NON-NLS-1$
				+ "\", \"description\": \"" + escapeJson(description) //$NON-NLS-1$
				+ "\", \"version\": " + version + "}"; //$NON-NLS-1$ //$NON-NLS-2$

		String jsonResponse = executePut(url, json);
		return BitbucketJsonParser.parseSinglePullRequest(jsonResponse);
	}

	@Override
	public boolean testConnection() {
		try {
			String url = serverUrl + API_BASE_PATH + "/application-properties"; //$NON-NLS-1$
			executeGet(url);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	@NonNull
	public String getCurrentUser() throws IOException {
		String url = serverUrl + API_BASE_PATH + "/users/current"; //$NON-NLS-1$
		String jsonResponse = executeGet(url);
		// Parse username from response: {"name":"username",...}
		return BitbucketJsonParser.extractJsonString(jsonResponse, "name"); //$NON-NLS-1$
	}

	@Override
	@NonNull
	public List<PullRequestCommit> getPullRequestCommits(long pullRequestId)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
				+ "/commits?limit=1000"; //$NON-NLS-1$
		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parseCommits(jsonResponse);
	}

	private String executeGet(String urlString) throws IOException {
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		try {
			// Set request method and headers
			connection.setRequestMethod("GET"); //$NON-NLS-1$
			connection.setRequestProperty("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$

			// Add Bearer token authentication
			String auth = "Bearer " + token; //$NON-NLS-1$
			connection.setRequestProperty("Authorization", auth); //$NON-NLS-1$

			// Set timeouts
			connection.setConnectTimeout(DEFAULT_TIMEOUT);
			connection.setReadTimeout(DEFAULT_TIMEOUT);

			// Check response code
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				return readResponse(connection.getInputStream());
			} else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
				throw new IOException(
						"Authentication failed. Check your access token."); //$NON-NLS-1$
			} else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
				throw new IOException(
						"Resource not found. Check project key and repository slug."); //$NON-NLS-1$
			} else {
				String errorMessage = readResponse(connection.getErrorStream());
				throw new IOException("Request failed with status " //$NON-NLS-1$
						+ responseCode + ": " + errorMessage); //$NON-NLS-1$
			}
		} finally {
			connection.disconnect();
		}
	}

	private String executePost(String urlString, String jsonBody)
			throws IOException {
		return executeWriteRequest(urlString, jsonBody, "POST"); //$NON-NLS-1$
	}

	private String executePut(String urlString, String jsonBody)
			throws IOException {
		return executeWriteRequest(urlString, jsonBody, "PUT"); //$NON-NLS-1$
	}

	private void executeDelete(String urlString) throws IOException {
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		try {
			connection.setRequestMethod("DELETE"); //$NON-NLS-1$
			connection.setRequestProperty("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$

			String auth = "Bearer " + token; //$NON-NLS-1$
			connection.setRequestProperty("Authorization", auth); //$NON-NLS-1$

			connection.setConnectTimeout(DEFAULT_TIMEOUT);
			connection.setReadTimeout(DEFAULT_TIMEOUT);

			int responseCode = connection.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_NO_CONTENT
					&& responseCode != HttpURLConnection.HTTP_OK) {
				if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
					throw new IOException(
							"Authentication failed. Check your access token."); //$NON-NLS-1$
				} else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
					throw new IOException("Resource not found."); //$NON-NLS-1$
				} else if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
					String errorMessage = readResponse(
							connection.getErrorStream());
					throw new IOException(
							"Conflict (version mismatch). " + errorMessage); //$NON-NLS-1$
				} else {
					String errorMessage = readResponse(
							connection.getErrorStream());
					throw new IOException("Request failed with status " //$NON-NLS-1$
							+ responseCode + ": " + errorMessage); //$NON-NLS-1$
				}
			}
		} finally {
			connection.disconnect();
		}
	}

	private String executeWriteRequest(String urlString, String jsonBody,
			String method) throws IOException {
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		try {
			connection.setRequestMethod(method);
			connection.setRequestProperty("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
			connection.setRequestProperty("Content-Type", //$NON-NLS-1$
					"application/json"); //$NON-NLS-1$

			String auth = "Bearer " + token; //$NON-NLS-1$
			connection.setRequestProperty("Authorization", auth); //$NON-NLS-1$

			connection.setConnectTimeout(DEFAULT_TIMEOUT);
			connection.setReadTimeout(DEFAULT_TIMEOUT);
			connection.setDoOutput(true);

			byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
			try (OutputStream os = connection.getOutputStream()) {
				os.write(bodyBytes);
			}

			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK
					|| responseCode == HttpURLConnection.HTTP_CREATED) {
				return readResponse(connection.getInputStream());
			} else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
				throw new IOException(
						"Authentication failed. Check your access token."); //$NON-NLS-1$
			} else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
				throw new IOException(
						"Resource not found. Check project key and repository slug."); //$NON-NLS-1$
			} else if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
				String errorMessage = readResponse(connection.getErrorStream());
				throw new IOException(
						"Conflict (version mismatch). " + errorMessage); //$NON-NLS-1$
			} else {
				String errorMessage = readResponse(connection.getErrorStream());
				throw new IOException("Request failed with status " //$NON-NLS-1$
						+ responseCode + ": " + errorMessage); //$NON-NLS-1$
			}
		} finally {
			connection.disconnect();
		}
	}

	private String readResponse(InputStream inputStream) throws IOException {
		if (inputStream == null) {
			return ""; //$NON-NLS-1$
		}

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			return response.toString();
		}
	}

	private byte[] executeGetBinary(String urlString) throws IOException {
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		try {
			// Set request method and headers
			connection.setRequestMethod("GET"); //$NON-NLS-1$
			connection.setRequestProperty("Accept", "*/*"); //$NON-NLS-1$ //$NON-NLS-2$

			// Add Bearer token authentication
			String auth = "Bearer " + token; //$NON-NLS-1$
			connection.setRequestProperty("Authorization", auth); //$NON-NLS-1$

			// Set timeouts
			connection.setConnectTimeout(DEFAULT_TIMEOUT);
			connection.setReadTimeout(DEFAULT_TIMEOUT);

			// Check response code
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				return readBinaryResponse(connection.getInputStream());
			} else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
				// Return empty byte array for non-existent files (added/deleted)
				return new byte[0];
			} else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
				throw new IOException(
						"Authentication failed. Check your access token."); //$NON-NLS-1$
			} else {
				String errorMessage = readResponse(connection.getErrorStream());
				throw new IOException("Request failed with status " //$NON-NLS-1$
						+ responseCode + ": " + errorMessage); //$NON-NLS-1$
			}
		} finally {
			connection.disconnect();
		}
	}

	private byte[] readBinaryResponse(InputStream inputStream)
			throws IOException {
		if (inputStream == null) {
			return new byte[0];
		}

		try (InputStream in = inputStream) {
			return in.readAllBytes();
		}
	}

	@Override
	public void submitReview(long pullRequestId, @NonNull String event,
			@Nullable String body) throws IOException {
		if ("APPROVE".equals(event)) { //$NON-NLS-1$
			// POST .../approve
			String url = serverUrl + API_BASE_PATH + "/projects/" //$NON-NLS-1$
					+ projectKey + "/repos/" + repositorySlug //$NON-NLS-1$
					+ "/pull-requests/" + pullRequestId + "/approve"; //$NON-NLS-1$ //$NON-NLS-2$
			executePost(url, ""); //$NON-NLS-1$
		} else if ("REQUEST_CHANGES".equals(event)) { //$NON-NLS-1$
			// PUT participant status to NEEDS_WORK
			String currentUser = getCurrentUser();
			String url = serverUrl + API_BASE_PATH + "/projects/" //$NON-NLS-1$
					+ projectKey + "/repos/" + repositorySlug //$NON-NLS-1$
					+ "/pull-requests/" + pullRequestId //$NON-NLS-1$
					+ "/participants/" + currentUser; //$NON-NLS-1$
			String json = "{\"user\":{\"name\":\"" //$NON-NLS-1$
					+ escapeJson(currentUser)
					+ "\"},\"status\":\"NEEDS_WORK\"}"; //$NON-NLS-1$
			executePut(url, json);
		} else if ("COMMENT".equals(event) //$NON-NLS-1$
				&& body != null && !body.isEmpty()) {
			// Add a general comment with the review body
			addComment(pullRequestId, body, -1);
		}
	}

	@Override
	public void unapproveReview(long pullRequestId) throws IOException {
		// DELETE .../approve
		String url = serverUrl + API_BASE_PATH + "/projects/" //$NON-NLS-1$
				+ projectKey + "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId + "/approve"; //$NON-NLS-1$ //$NON-NLS-2$
		executeDelete(url);
	}

	/**
	 * Builds the base URL for pull request API endpoints.
	 *
	 * @param pullRequestId
	 *            the pull request ID
	 * @param suffix
	 *            additional path suffix (e.g., "/approve", "/comments")
	 * @return the complete URL
	 */
	private String buildPullRequestUrl(long pullRequestId, String suffix) {
		return serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId + suffix; //$NON-NLS-1$
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
	@NonNull
	public List<PullRequest.PullRequestParticipant> getReviewers(
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
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug + "/pull-requests/" + pullRequestId //$NON-NLS-1$ //$NON-NLS-2$
				+ "/participants/" + username; //$NON-NLS-1$

		// Build request body
		String requestBody = "{\"user\":{\"name\":\"" + username //$NON-NLS-1$
				+ "\"},\"role\":\"REVIEWER\"}"; //$NON-NLS-1$

		executeRequest(url, "PUT", requestBody); //$NON-NLS-1$
	}

	@Override
	public void removeReviewer(long pullRequestId, @NonNull String username)
			throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug + "/pull-requests/" + pullRequestId //$NON-NLS-1$ //$NON-NLS-2$
				+ "/participants/" + username; //$NON-NLS-1$

		executeRequest(url, "DELETE", null); //$NON-NLS-1$
	}

	@Override
	public void addReviewers(long pullRequestId,
			@NonNull List<String> usernames) throws IOException {
		// Bitbucket doesn't support batch add, so add one by one
		for (String username : usernames) {
			addReviewer(pullRequestId, username);
		}
	}

	/**
	 * Execute an HTTP request with the specified method and optional body
	 *
	 * @param urlString
	 *            the full URL
	 * @param method
	 *            HTTP method (GET, POST, PUT, DELETE)
	 * @param jsonBody
	 *            the JSON body for POST/PUT requests, null for GET/DELETE
	 * @return the response string, or empty string for DELETE
	 * @throws IOException
	 *             if the request fails
	 */
	private String executeRequest(String urlString, String method,
			String jsonBody) throws IOException {
		if ("DELETE".equals(method)) { //$NON-NLS-1$
			executeDelete(urlString);
			return ""; //$NON-NLS-1$
		} else if ("PUT".equals(method)) { //$NON-NLS-1$
			return executePut(urlString, jsonBody);
		} else if ("POST".equals(method)) { //$NON-NLS-1$
			return executePost(urlString, jsonBody);
		} else if ("GET".equals(method)) { //$NON-NLS-1$
			return executeGet(urlString);
		} else {
			throw new IOException("Unsupported HTTP method: " + method); //$NON-NLS-1$
		}
	}

	@Override
	public @NonNull List<ChangedFile> getCommitChanges(
			@NonNull String commitSha) throws IOException {
		// Bitbucket: GET /rest/api/1.0/projects/{key}/repos/{slug}/commits/{sha}/changes
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/commits/" + commitSha + "/changes?limit=1000"; //$NON-NLS-1$ //$NON-NLS-2$

		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parseChangedFiles(jsonResponse);
	}

	@Override
	public @NonNull List<ChangedFile> getCommitRangeChanges(
			@NonNull String baseCommitSha, @NonNull String headCommitSha)
			throws IOException {
		// Bitbucket: GET /rest/api/1.0/projects/{key}/repos/{slug}/commits/{sha}/changes
		// with sinceId parameter to get changes between commits
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/commits/" + headCommitSha + "/changes?sinceId=" //$NON-NLS-1$ //$NON-NLS-2$
				+ baseCommitSha + "&limit=1000"; //$NON-NLS-1$

		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parseChangedFiles(jsonResponse);
	}
}
