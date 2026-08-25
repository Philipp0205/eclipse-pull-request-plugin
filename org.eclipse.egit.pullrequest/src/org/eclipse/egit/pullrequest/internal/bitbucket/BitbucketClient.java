package org.eclipse.egit.pullrequest.internal.bitbucket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

import javax.net.ssl.SSLException;

import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics.Outcome;
import org.eclipse.egit.pullrequest.internal.client.HttpProxySupport;
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

	/**
	 * Header Bitbucket adds to every response, carrying the URL encoded name of
	 * the authenticated user. Bitbucket Data Center has no "current user"
	 * endpoint, so this header is the documented way to find out who we are.
	 */
	private static final String USERNAME_HEADER = "X-AUSERNAME"; //$NON-NLS-1$

	private static final String WHOAMI_PATH = "/plugins/servlet/applinks/whoami"; //$NON-NLS-1$

	private static final int DEFAULT_TIMEOUT = 30000; // 30 seconds

	private static final int PROBE_TIMEOUT = 10000; // 10 seconds

	private static final int MAX_REPORTED_BODY = 400;

	private final String configuredServerUrl;

	private volatile String serverUrl;

	private volatile boolean contextPathResolved;

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
		this.serverUrl = trimTrailingSlash(serverUrl);
		this.configuredServerUrl = this.serverUrl;
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
		return BitbucketJsonParser.parsePullRequests(jsonResponse, serverUrl);
	}

	@Override
	@NonNull
	public PullRequest getPullRequest(long pullRequestId) throws IOException {
		String url = serverUrl + API_BASE_PATH + "/projects/" + projectKey //$NON-NLS-1$
				+ "/repos/" + repositorySlug //$NON-NLS-1$
				+ "/pull-requests/" + pullRequestId; //$NON-NLS-1$

		String jsonResponse = executeGet(url);
		return BitbucketJsonParser.parseSinglePullRequest(jsonResponse, serverUrl);
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
		return BitbucketJsonParser.parseActivities(jsonResponse, serverUrl);
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
		return BitbucketJsonParser.parseSinglePullRequest(jsonResponse, serverUrl);
	}

	@Override
	public boolean testConnection() {
		try {
			getCurrentUser();
			return true;
		} catch (IOException e) {
			// The cause is already in the log; diagnoseConnection() is the
			// entry point that reports it to the user
			return false;
		}
	}

	@Override
	@NonNull
	public String getCurrentUser() throws IOException {
		// Bitbucket Data Center has no /users/current resource. Any
		// authenticated response carries the user name in X-AUSERNAME, and
		// application-properties is the cheapest endpoint to ask for.
		String url = serverUrl + API_BASE_PATH + "/application-properties"; //$NON-NLS-1$
		HttpURLConnection connection = openConnection(url, "GET", //$NON-NLS-1$
				"application/json"); //$NON-NLS-1$
		try {
			int status = responseCode(connection, "GET", url); //$NON-NLS-1$
			if (status != HttpURLConnection.HTTP_OK) {
				throw httpFailure("GET", url, connection, status); //$NON-NLS-1$
			}
			// Drain the body so the connection can be pooled
			readResponse(connection.getInputStream());
			String username = authenticatedUser(connection);
			if (username != null) {
				return username;
			}
		} finally {
			connection.disconnect();
		}

		String whoami = requestWhoami();
		if (whoami != null) {
			return whoami;
		}
		throw logged(new IOException(
				"Bitbucket accepted the request but reported no authenticated user." //$NON-NLS-1$
						+ " The personal access token was not sent or was" //$NON-NLS-1$
						+ " ignored; requests reached " + serverUrl //$NON-NLS-1$
						+ " anonymously.")); //$NON-NLS-1$
	}

	/**
	 * Reads the authenticated user name from a response.
	 *
	 * @param connection
	 *            a connection whose response headers are available
	 * @return the user name, or null if the request was anonymous
	 */
	private String authenticatedUser(HttpURLConnection connection) {
		String header = connection.getHeaderField(USERNAME_HEADER);
		if (header == null || header.isEmpty()) {
			return null;
		}
		String username = URLDecoder.decode(header, StandardCharsets.UTF_8)
				.trim();
		return username.isEmpty() ? null : username;
	}

	/**
	 * Asks the application links servlet who we are. Some Bitbucket versions
	 * answer this even when X-AUSERNAME is absent.
	 *
	 * @return the user name, or null if the servlet did not answer with one
	 */
	private String requestWhoami() {
		String url = serverUrl + WHOAMI_PATH;
		HttpURLConnection connection = null;
		try {
			connection = openConnection(url, "GET", "text/plain"); //$NON-NLS-1$ //$NON-NLS-2$
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				return null;
			}
			String body = readResponse(connection.getInputStream()).trim();
			return body.isEmpty() ? null : body;
		} catch (IOException e) {
			Activator.logDebug("whoami lookup failed: " + e); //$NON-NLS-1$
			return null;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	@Override
	@NonNull
	public ConnectionDiagnostics diagnoseConnection() {
		ConnectionDiagnostics report = new ConnectionDiagnostics();
		report.add("Configuration", Outcome.OK, //$NON-NLS-1$
				"Server " + serverUrl //$NON-NLS-1$
						+ "\nProject key " + projectKey //$NON-NLS-1$
						+ "\nRepository slug " + repositorySlug //$NON-NLS-1$
						+ "\nAccess token " + describeToken()); //$NON-NLS-1$

		URI uri;
		try {
			uri = new URI(serverUrl);
		} catch (URISyntaxException e) {
			uri = null;
		}
		if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
			report.add("Server URL", Outcome.FAILED, //$NON-NLS-1$
					'\'' + serverUrl + "' is not a valid absolute URL." //$NON-NLS-1$
							+ " Expected a value such as" //$NON-NLS-1$
							+ " https://bitbucket.example.com"); //$NON-NLS-1$
			return logReport(report);
		}

		boolean secure = "https".equalsIgnoreCase(uri.getScheme()); //$NON-NLS-1$
		int port = uri.getPort() > 0 ? uri.getPort() : (secure ? 443 : 80);
		report.add("Server URL", secure ? Outcome.OK : Outcome.WARNING, //$NON-NLS-1$
				"Scheme " + uri.getScheme() + ", host " + uri.getHost() //$NON-NLS-1$ //$NON-NLS-2$
						+ ", port " + port //$NON-NLS-1$
						+ (secure ? "" //$NON-NLS-1$
								: "; plain HTTP transmits the access token" //$NON-NLS-1$
										+ " unencrypted")); //$NON-NLS-1$

		Proxy proxy = null;
		try {
			URL url = new URL(serverUrl);
			proxy = HttpProxySupport.select(url);
			report.add("Network route", Outcome.OK, //$NON-NLS-1$
					HttpProxySupport.describe(url));
		} catch (IOException e) {
			report.add("Network route", Outcome.WARNING, //$NON-NLS-1$
					"Cannot determine the proxy configuration: " //$NON-NLS-1$
							+ e.getMessage());
		}

		if (!checkNameResolution(report, uri.getHost())) {
			return logReport(report);
		}
		if (!checkTcpConnection(report, uri.getHost(), port, proxy)) {
			return logReport(report);
		}
		if (!checkRestApi(report)) {
			return logReport(report);
		}
		checkRepository(report);
		return logReport(report);
	}

	private String describeToken() {
		if (token.isEmpty()) {
			return "not set"; //$NON-NLS-1$
		}
		if (!token.equals(token.trim())) {
			return "set, " + token.length() //$NON-NLS-1$
					+ " characters, but it has leading or trailing whitespace" //$NON-NLS-1$
					+ " which Bitbucket will reject"; //$NON-NLS-1$
		}
		return "set, " + token.length() + " characters"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private boolean checkNameResolution(ConnectionDiagnostics report,
			String host) {
		try {
			StringJoiner addresses = new StringJoiner(", "); //$NON-NLS-1$
			for (InetAddress address : InetAddress.getAllByName(host)) {
				addresses.add(address.getHostAddress());
			}
			report.add("Name resolution", Outcome.OK, //$NON-NLS-1$
					host + " resolves to " + addresses); //$NON-NLS-1$
			return true;
		} catch (UnknownHostException e) {
			report.add("Name resolution", Outcome.FAILED, //$NON-NLS-1$
					host + " cannot be resolved. The server is unreachable" //$NON-NLS-1$
							+ " from this machine; check the spelling of the" //$NON-NLS-1$
							+ " URL and whether the corporate network or VPN" //$NON-NLS-1$
							+ " is connected."); //$NON-NLS-1$
			return false;
		}
	}

	private boolean checkTcpConnection(ConnectionDiagnostics report,
			String host, int port, Proxy proxy) {
		boolean viaProxy = proxy != null
				&& proxy.address() instanceof InetSocketAddress;
		InetSocketAddress target = viaProxy
				? (InetSocketAddress) proxy.address()
				: new InetSocketAddress(host, port);
		String label = viaProxy ? "proxy " + target.getHostString() + ':' //$NON-NLS-1$
				+ target.getPort() : host + ':' + port;
		try (Socket socket = new Socket()) {
			socket.connect(target, PROBE_TIMEOUT);
			report.add("TCP connection", Outcome.OK, //$NON-NLS-1$
					label + " accepts connections"); //$NON-NLS-1$
			return true;
		} catch (IOException e) {
			report.add("TCP connection", Outcome.FAILED, //$NON-NLS-1$
					"Cannot open a connection to " + label + ": " //$NON-NLS-1$ //$NON-NLS-2$
							+ e.getMessage()
							+ ". A firewall, a missing VPN connection or a" //$NON-NLS-1$
							+ " wrong port is the usual cause."); //$NON-NLS-1$
			return false;
		}
	}

	private boolean checkRestApi(ConnectionDiagnostics report) {
		String url = serverUrl + API_BASE_PATH + "/application-properties"; //$NON-NLS-1$
		Probe probe = probe(url);
		if (probe.failure != null) {
			report.add("REST API", Outcome.FAILED, //$NON-NLS-1$
					"GET " + url + " failed: " + probe.failure); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		if (probe.status != HttpURLConnection.HTTP_OK) {
			report.add("REST API", Outcome.FAILED, //$NON-NLS-1$
					"GET " + url + " answered HTTP " + probe.status + ". " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							+ statusHint(probe.status, probe.contentType,
									probe.location)
							+ bodySuffix(probe.body));
			return false;
		}
		report.add("REST API", Outcome.OK, //$NON-NLS-1$
				"application-properties answered: " + probe.body); //$NON-NLS-1$

		if (probe.username != null) {
			report.add("Authentication", Outcome.OK, //$NON-NLS-1$
					"Bitbucket identifies the token as user " //$NON-NLS-1$
							+ probe.username);
			return true;
		}
		String whoami = requestWhoami();
		if (whoami != null) {
			report.add("Authentication", Outcome.OK, //$NON-NLS-1$
					"Bitbucket identifies the token as user " + whoami //$NON-NLS-1$
							+ " (reported by the whoami servlet, the" //$NON-NLS-1$
							+ ' ' + USERNAME_HEADER + " header was absent)"); //$NON-NLS-1$
			return true;
		}
		report.add("Authentication", Outcome.FAILED, //$NON-NLS-1$
				"The request succeeded but Bitbucket returned no " //$NON-NLS-1$
						+ USERNAME_HEADER + " header, so it was handled" //$NON-NLS-1$
						+ " anonymously. The access token is not reaching" //$NON-NLS-1$
						+ " Bitbucket: either it is invalid or a reverse" //$NON-NLS-1$
						+ " proxy in front of the server strips the" //$NON-NLS-1$
						+ " Authorization header."); //$NON-NLS-1$
		return true;
	}

	private void checkRepository(ConnectionDiagnostics report) {
		String projectUrl = serverUrl + API_BASE_PATH + "/projects/" //$NON-NLS-1$
				+ projectKey;
		Probe project = probe(projectUrl);
		report.add("Project " + projectKey, //$NON-NLS-1$
				project.status == HttpURLConnection.HTTP_OK ? Outcome.OK
						: Outcome.FAILED,
				describeProbe(projectUrl, project));

		String repoUrl = projectUrl + "/repos/" + repositorySlug; //$NON-NLS-1$
		Probe repository = probe(repoUrl);
		report.add("Repository " + repositorySlug, //$NON-NLS-1$
				repository.status == HttpURLConnection.HTTP_OK ? Outcome.OK
						: Outcome.FAILED,
				describeProbe(repoUrl, repository));

		String pullRequestUrl = repoUrl + "/pull-requests?state=OPEN&limit=1"; //$NON-NLS-1$
		Probe pullRequests = probe(pullRequestUrl);
		report.add("Pull requests", //$NON-NLS-1$
				pullRequests.status == HttpURLConnection.HTTP_OK ? Outcome.OK
						: Outcome.FAILED,
				describeProbe(pullRequestUrl, pullRequests));
	}

	private String describeProbe(String url, Probe probe) {
		if (probe.failure != null) {
			return "GET " + url + " failed: " + probe.failure; //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (probe.status == HttpURLConnection.HTTP_OK) {
			return "GET " + url + " answered HTTP 200" //$NON-NLS-1$ //$NON-NLS-2$
					+ bodySuffix(probe.body);
		}
		return "GET " + url + " answered HTTP " + probe.status + ". " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ statusHint(probe.status, probe.contentType, probe.location)
				+ bodySuffix(probe.body);
	}

	private static String bodySuffix(String body) {
		return body.isEmpty() ? "" : " Server response: " + body; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private ConnectionDiagnostics logReport(ConnectionDiagnostics report) {
		Activator.logInfo("Bitbucket connection diagnostics for " + serverUrl //$NON-NLS-1$
				+ '\n' + report.toReport());
		return report;
	}

	/**
	 * Outcome of a single diagnostic request. Unlike the regular request
	 * helpers this never throws, so that one failing step does not hide the
	 * information the remaining steps would provide.
	 */
	private static final class Probe {

		int status = -1;

		String body = ""; //$NON-NLS-1$

		String contentType;

		String location;

		String username;

		String failure;
	}

	private Probe probe(String urlString) {
		Probe probe = new Probe();
		HttpURLConnection connection = null;
		try {
			connection = openConnection(urlString, "GET", //$NON-NLS-1$
					"application/json"); //$NON-NLS-1$
			connection.setConnectTimeout(PROBE_TIMEOUT);
			connection.setReadTimeout(PROBE_TIMEOUT);
			probe.status = connection.getResponseCode();
			probe.contentType = connection.getContentType();
			probe.location = connection.getHeaderField("Location"); //$NON-NLS-1$
			probe.username = authenticatedUser(connection);
			probe.body = summarize(
					probe.status == HttpURLConnection.HTTP_OK
							? readResponse(connection.getInputStream())
							: readErrorBody(connection),
					probe.contentType);
		} catch (IOException e) {
			probe.failure = e.getClass().getSimpleName()
					+ (e.getMessage() == null ? "" : ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		return probe;
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

	/**
	 * Opens a connection with authentication, timeouts and the proxy Eclipse is
	 * configured to use, and records the request in the log when verbose
	 * logging is on. The access token is never logged.
	 *
	 * @param urlString
	 *            the target URL
	 * @param method
	 *            the HTTP method
	 * @param accept
	 *            value for the Accept header
	 * @return the prepared connection, not yet connected
	 * @throws IOException
	 *             if the URL is invalid or no connection can be created
	 */
	private HttpURLConnection openConnection(String urlString, String method,
			String accept) throws IOException {
		resolveContextPath();
		String resolvedUrl = replaceConfiguredServerUrl(urlString);
		URL url = new URL(resolvedUrl);
		Proxy proxy = HttpProxySupport.select(url);
		HttpURLConnection connection = (HttpURLConnection) (proxy == null
				? url.openConnection()
				: url.openConnection(proxy));
		connection.setRequestMethod(method);
		connection.setRequestProperty("Accept", accept); //$NON-NLS-1$
		connection.setRequestProperty("Authorization", "Bearer " + token); //$NON-NLS-1$ //$NON-NLS-2$
		connection.setConnectTimeout(DEFAULT_TIMEOUT);
		connection.setReadTimeout(DEFAULT_TIMEOUT);
		Activator.logDebug(method + ' ' + resolvedUrl
				+ (proxy == null ? "" : " (via " + proxy + ')')); //$NON-NLS-1$ //$NON-NLS-2$
		return connection;
	}

	private synchronized void resolveContextPath() {
		if (contextPathResolved) {
			return;
		}
		contextPathResolved = true;

		Probe root = probeServerBase(configuredServerUrl);
		if (root.status == HttpURLConnection.HTTP_OK
				|| root.status == HttpURLConnection.HTTP_UNAUTHORIZED
				|| root.status == HttpURLConnection.HTTP_FORBIDDEN) {
			return;
		}

		String redirectedBase = contextBaseFromRedirect(root.location);
		if (redirectedBase != null
				&& isBitbucketApi(redirectedBase)) {
			serverUrl = redirectedBase;
			return;
		}

		String conventionalBase = configuredServerUrl + "/bitbucket"; //$NON-NLS-1$
		if (isBitbucketApi(conventionalBase)) {
			serverUrl = conventionalBase;
			Activator.logInfo("Detected Bitbucket context path: " + serverUrl); //$NON-NLS-1$
		}
	}

	private boolean isBitbucketApi(String baseUrl) {
		Probe probe = probeServerBase(baseUrl);
		return probe.status == HttpURLConnection.HTTP_OK
				|| probe.status == HttpURLConnection.HTTP_UNAUTHORIZED
				|| probe.status == HttpURLConnection.HTTP_FORBIDDEN;
	}

	private Probe probeServerBase(String baseUrl) {
		Probe result = new Probe();
		HttpURLConnection connection = null;
		try {
			URL url = new URL(baseUrl + API_BASE_PATH
					+ "/application-properties"); //$NON-NLS-1$
			Proxy proxy = HttpProxySupport.select(url);
			connection = (HttpURLConnection) (proxy == null
					? url.openConnection()
					: url.openConnection(proxy));
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("GET"); //$NON-NLS-1$
			connection.setRequestProperty("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
			connection.setRequestProperty("Authorization", //$NON-NLS-1$
					"Bearer " + token); //$NON-NLS-1$
			connection.setConnectTimeout(PROBE_TIMEOUT);
			connection.setReadTimeout(PROBE_TIMEOUT);
			result.status = connection.getResponseCode();
			result.location = connection.getHeaderField("Location"); //$NON-NLS-1$
		} catch (IOException e) {
			result.failure = e.getMessage();
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		return result;
	}

	private String contextBaseFromRedirect(String location) {
		if (location == null || location.isBlank()) {
			return null;
		}
		try {
			URI configured = URI.create(configuredServerUrl);
			URI redirect = configured.resolve(location);
			if (!configured.getHost().equalsIgnoreCase(redirect.getHost())) {
				return null;
			}
			String path = redirect.getPath();
			if (path == null || path.length() < 2) {
				return null;
			}
			int secondSlash = path.indexOf('/', 1);
			String context = secondSlash < 0 ? path
					: path.substring(0, secondSlash);
			return trimTrailingSlash(configuredServerUrl + context);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String replaceConfiguredServerUrl(String urlString) {
		if (!serverUrl.equals(configuredServerUrl)
				&& urlString.startsWith(configuredServerUrl)) {
			return serverUrl
					+ urlString.substring(configuredServerUrl.length());
		}
		return urlString;
	}

	private static String trimTrailingSlash(String url) {
		String result = url.trim();
		while (result.endsWith("/")) { //$NON-NLS-1$
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	/**
	 * Reads the status code, translating transport level failures into a
	 * message that names the likely cause.
	 *
	 * @param connection
	 *            the connection to read from
	 * @param method
	 *            the HTTP method, for the error message
	 * @param urlString
	 *            the target URL, for the error message
	 * @return the HTTP status code
	 * @throws IOException
	 *             if the request never reached the server
	 */
	private int responseCode(HttpURLConnection connection, String method,
			String urlString) throws IOException {
		try {
			return connection.getResponseCode();
		} catch (IOException e) {
			throw transportFailure(method, urlString, e);
		}
	}

	/**
	 * Builds and logs an exception for a request that never reached the server.
	 *
	 * @param method
	 *            the HTTP method
	 * @param urlString
	 *            the target URL
	 * @param cause
	 *            the failure
	 * @return the exception to throw
	 */
	private IOException transportFailure(String method, String urlString,
			IOException cause) {
		String host = hostOf(urlString);
		String explanation;
		if (cause instanceof UnknownHostException) {
			explanation = "the host name " + host //$NON-NLS-1$
					+ " cannot be resolved. Check the server URL and whether" //$NON-NLS-1$
					+ " this machine can reach the corporate network (VPN)."; //$NON-NLS-1$
		} else if (cause instanceof SSLException) {
			explanation = "the TLS handshake with " + host + " failed: " //$NON-NLS-1$ //$NON-NLS-2$
					+ cause.getMessage()
					+ ". If the server certificate is issued by an internal" //$NON-NLS-1$
					+ " certificate authority, add that authority to the" //$NON-NLS-1$
					+ " trust store of the JRE that runs Eclipse."; //$NON-NLS-1$
		} else if (cause instanceof SocketTimeoutException) {
			explanation = "the connection to " + host + " timed out after " //$NON-NLS-1$ //$NON-NLS-2$
					+ DEFAULT_TIMEOUT / 1000
					+ " seconds. A proxy or firewall may be dropping the" //$NON-NLS-1$
					+ " connection."; //$NON-NLS-1$
		} else if (cause instanceof ConnectException
				|| cause instanceof NoRouteToHostException) {
			explanation = "the connection to " + host + " was refused: " //$NON-NLS-1$ //$NON-NLS-2$
					+ cause.getMessage() + '.';
		} else {
			explanation = cause.getClass().getSimpleName() + ": " //$NON-NLS-1$
					+ cause.getMessage();
		}
		return logged(new IOException(method + ' ' + urlString
				+ " could not be sent because " + explanation, cause)); //$NON-NLS-1$
	}

	/**
	 * Builds and logs an exception for a response with an unexpected status.
	 *
	 * @param method
	 *            the HTTP method
	 * @param urlString
	 *            the target URL
	 * @param connection
	 *            the connection carrying the response
	 * @param status
	 *            the HTTP status code
	 * @return the exception to throw
	 */
	private IOException httpFailure(String method, String urlString,
			HttpURLConnection connection, int status) {
		StringBuilder message = new StringBuilder();
		message.append(method).append(' ').append(urlString)
				.append(" failed with HTTP ").append(status); //$NON-NLS-1$
		String reason = responseMessage(connection);
		if (!reason.isEmpty()) {
			message.append(' ').append(reason);
		}
		message.append(". ").append(statusHint(status, //$NON-NLS-1$
				connection.getContentType(),
				connection.getHeaderField("Location"))); //$NON-NLS-1$

		String body = summarize(readErrorBody(connection),
				connection.getContentType());
		if (!body.isEmpty()) {
			message.append(" Server response: ").append(body); //$NON-NLS-1$
		}
		return logged(new IOException(message.toString()));
	}

	/**
	 * Explains what a status code most likely means for this configuration.
	 *
	 * @param status
	 *            the HTTP status code
	 * @param contentType
	 *            the response content type, may be null
	 * @param location
	 *            the value of the Location header, may be null
	 * @return the explanation
	 */
	private String statusHint(int status, String contentType, String location) {
		boolean html = contentType != null
				&& contentType.startsWith("text/html"); //$NON-NLS-1$
		switch (status) {
		case HttpURLConnection.HTTP_UNAUTHORIZED:
			return "Bitbucket rejected the personal access token. Create a" //$NON-NLS-1$
					+ " new one under Profile > Manage account > HTTP access" //$NON-NLS-1$
					+ " tokens, and make sure no reverse proxy in front of" //$NON-NLS-1$
					+ " Bitbucket removes the Authorization header."; //$NON-NLS-1$
		case HttpURLConnection.HTTP_FORBIDDEN:
			return "The token is accepted but lacks permission. It needs at" //$NON-NLS-1$
					+ " least read access to project " + projectKey //$NON-NLS-1$
					+ " and repository " + repositorySlug + '.'; //$NON-NLS-1$
		case HttpURLConnection.HTTP_NOT_FOUND:
			return html
					? "The server answered with an HTML page instead of JSON," //$NON-NLS-1$
							+ " so the REST API is not at this address. Check" //$NON-NLS-1$
							+ " whether Bitbucket runs under a context path" //$NON-NLS-1$
							+ " such as " + serverUrl + "/bitbucket." //$NON-NLS-1$ //$NON-NLS-2$
					: "Check the server URL, the project key (" + projectKey //$NON-NLS-1$
							+ ") and the repository slug (" + repositorySlug //$NON-NLS-1$
							+ "). Both are case sensitive in the REST API."; //$NON-NLS-1$
		case HttpURLConnection.HTTP_MOVED_PERM:
		case HttpURLConnection.HTTP_MOVED_TEMP:
		case HttpURLConnection.HTTP_SEE_OTHER:
			return "The server redirected to " + location //$NON-NLS-1$
					+ ". Java does not resend the Authorization header across" //$NON-NLS-1$
					+ " a redirect, so configure that address directly."; //$NON-NLS-1$
		case HttpURLConnection.HTTP_CONFLICT:
			return "Version conflict: the item was changed on the server," //$NON-NLS-1$
					+ " refresh and retry."; //$NON-NLS-1$
		case HttpURLConnection.HTTP_BAD_GATEWAY:
		case HttpURLConnection.HTTP_UNAVAILABLE:
		case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
			return html
					? "The answer is an HTML error page, so a proxy or load" //$NON-NLS-1$
							+ " balancer in front of Bitbucket refused to" //$NON-NLS-1$
							+ " forward the request. This is what a corporate" //$NON-NLS-1$
							+ " gateway returns when the VPN is not" //$NON-NLS-1$
							+ " connected." //$NON-NLS-1$
					: "Bitbucket is unavailable, it may be starting up or in" //$NON-NLS-1$
							+ " maintenance mode."; //$NON-NLS-1$
		default:
			return html
					? "The response was an HTML page instead of JSON, which" //$NON-NLS-1$
							+ " usually means the request was answered by a" //$NON-NLS-1$
							+ " proxy or login page rather than Bitbucket." //$NON-NLS-1$
					: ""; //$NON-NLS-1$
		}
	}

	private static String responseMessage(HttpURLConnection connection) {
		try {
			String reason = connection.getResponseMessage();
			return reason == null ? "" : reason; //$NON-NLS-1$
		} catch (IOException e) {
			return ""; //$NON-NLS-1$
		}
	}

	private String readErrorBody(HttpURLConnection connection) {
		try {
			return readResponse(connection.getErrorStream());
		} catch (IOException e) {
			return ""; //$NON-NLS-1$
		}
	}

	/**
	 * Shortens a response body for a message. Error pages produced by proxies
	 * are HTML; their markup is dropped so that the sentence they contain stays
	 * readable in the log.
	 *
	 * @param body
	 *            the response body
	 * @param contentType
	 *            the response content type, may be null
	 * @return the shortened body
	 */
	private static String summarize(String body, String contentType) {
		String text = body;
		if (contentType != null && contentType.startsWith("text/html")) { //$NON-NLS-1$
			text = text.replaceAll("<[^>]*>", " ") //$NON-NLS-1$ //$NON-NLS-2$
					.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return truncate(text);
	}

	private static String truncate(String text) {
		if (text.length() <= MAX_REPORTED_BODY) {
			return text;
		}
		return text.substring(0, MAX_REPORTED_BODY) + "..."; //$NON-NLS-1$
	}

	private static String hostOf(String urlString) {
		try {
			return new URL(urlString).getHost();
		} catch (IOException e) {
			return urlString;
		}
	}

	/**
	 * Writes the failure to the plug-in log so that it is available in the
	 * Error Log view even when the caller only shows a short message.
	 *
	 * @param failure
	 *            the failure to log
	 * @return the same exception, for use in a throw statement
	 */
	private static IOException logged(IOException failure) {
		Activator.logError(failure.getMessage(), failure);
		return failure;
	}

	private String executeGet(String urlString) throws IOException {
		HttpURLConnection connection = openConnection(urlString, "GET", //$NON-NLS-1$
				"application/json"); //$NON-NLS-1$
		try {
			int status = responseCode(connection, "GET", urlString); //$NON-NLS-1$
			if (status != HttpURLConnection.HTTP_OK) {
				throw httpFailure("GET", urlString, connection, status); //$NON-NLS-1$
			}
			String body = readResponse(connection.getInputStream());
			Activator.logDebug("GET " + urlString + " -> HTTP 200, " //$NON-NLS-1$ //$NON-NLS-2$
					+ body.length() + " characters"); //$NON-NLS-1$
			return body;
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
		HttpURLConnection connection = openConnection(urlString, "DELETE", //$NON-NLS-1$
				"application/json"); //$NON-NLS-1$
		try {
			int status = responseCode(connection, "DELETE", urlString); //$NON-NLS-1$
			if (status != HttpURLConnection.HTTP_NO_CONTENT
					&& status != HttpURLConnection.HTTP_OK) {
				throw httpFailure("DELETE", urlString, connection, status); //$NON-NLS-1$
			}
			Activator.logDebug("DELETE " + urlString + " -> HTTP " + status); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			connection.disconnect();
		}
	}

	private String executeWriteRequest(String urlString, String jsonBody,
			String method) throws IOException {
		HttpURLConnection connection = openConnection(urlString, method,
				"application/json"); //$NON-NLS-1$
		try {
			connection.setRequestProperty("Content-Type", //$NON-NLS-1$
					"application/json"); //$NON-NLS-1$
			connection.setDoOutput(true);

			byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
			try (OutputStream os = connection.getOutputStream()) {
				os.write(bodyBytes);
			} catch (IOException e) {
				throw transportFailure(method, urlString, e);
			}

			int status = responseCode(connection, method, urlString);
			if (status != HttpURLConnection.HTTP_OK
					&& status != HttpURLConnection.HTTP_CREATED) {
				throw httpFailure(method, urlString, connection, status);
			}
			Activator.logDebug(method + ' ' + urlString + " -> HTTP " + status); //$NON-NLS-1$
			return readResponse(connection.getInputStream());
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
		HttpURLConnection connection = openConnection(urlString, "GET", "*/*"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			int status = responseCode(connection, "GET", urlString); //$NON-NLS-1$
			if (status == HttpURLConnection.HTTP_OK) {
				return readBinaryResponse(connection.getInputStream());
			}
			if (status == HttpURLConnection.HTTP_NOT_FOUND) {
				// Files that were added or deleted do not exist on one side
				Activator.logDebug("GET " + urlString //$NON-NLS-1$
						+ " -> HTTP 404, treated as empty content"); //$NON-NLS-1$
				return new byte[0];
			}
			throw httpFailure("GET", urlString, connection, status); //$NON-NLS-1$
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
