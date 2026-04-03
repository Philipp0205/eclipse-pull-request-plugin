package org.eclipse.egit.pullrequest.internal.github;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Implements GitHub OAuth Device Flow for desktop applications
 * 
 * <p>
 * Device Flow is simpler for desktop apps than full OAuth2 flow, as it doesn't
 * require callback URLs or embedded browsers.
 * </p>
 * 
 * <p>
 * Flow:
 * <ol>
 * <li>Request device code from GitHub</li>
 * <li>Display user_code and verification_uri to user</li>
 * <li>Poll GitHub for access token</li>
 * <li>Return access token once user authorizes</li>
 * </ol>
 * </p>
 */
public class GitHubDeviceFlowAuth {

	private static final String DEVICE_CODE_URL = "https://github.com/login/device/code"; //$NON-NLS-1$

	private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"; //$NON-NLS-1$

	private static final int DEFAULT_TIMEOUT = 30000; // 30 seconds

	private String clientId;

	/**
	 * Result of device code request
	 */
	public static class DeviceCodeResponse {
		private String deviceCode;

		private String userCode;

		private String verificationUri;

		private int expiresIn;

		private int interval;

		/**
		 * @return the device code for polling
		 */
		public String getDeviceCode() {
			return deviceCode;
		}

		/**
		 * @return the user code to display to the user
		 */
		public String getUserCode() {
			return userCode;
		}

		/**
		 * @return the verification URI where user should authorize
		 */
		public String getVerificationUri() {
			return verificationUri;
		}

		/**
		 * @return expiration time in seconds
		 */
		public int getExpiresIn() {
			return expiresIn;
		}

		/**
		 * @return polling interval in seconds
		 */
		public int getInterval() {
			return interval;
		}
	}

	/**
	 * Creates a new GitHub Device Flow authenticator
	 *
	 * @param clientId
	 *            the GitHub OAuth app client ID
	 */
	public GitHubDeviceFlowAuth(String clientId) {
		this.clientId = clientId;
	}

	/**
	 * Requests a device code from GitHub
	 *
	 * @param scopes
	 *            the requested OAuth scopes (e.g., "repo", "read:user")
	 * @return the device code response
	 * @throws IOException
	 *             if the request fails
	 */
	public DeviceCodeResponse requestDeviceCode(String scopes)
			throws IOException {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(DEVICE_CODE_URL);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST"); //$NON-NLS-1$
			conn.setConnectTimeout(DEFAULT_TIMEOUT);
			conn.setReadTimeout(DEFAULT_TIMEOUT);
			conn.setDoOutput(true);

			// Set headers
			conn.setRequestProperty("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
			conn.setRequestProperty("Content-Type", //$NON-NLS-1$
					"application/x-www-form-urlencoded"); //$NON-NLS-1$

			// Build request body
			String body = "client_id=" + clientId + "&scope=" + scopes; //$NON-NLS-1$ //$NON-NLS-2$

			// Write request
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}

			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				String error = readError(conn);
				throw new IOException(
						"Failed to request device code: HTTP " + responseCode //$NON-NLS-1$
								+ " - " + error); //$NON-NLS-1$
			}

			String json = readResponse(conn);
			return parseDeviceCodeResponse(json);

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Polls GitHub for access token
	 *
	 * @param deviceCode
	 *            the device code from requestDeviceCode
	 * @return the access token, or null if still pending
	 * @throws IOException
	 *             if the request fails
	 * @throws AuthorizationPendingException
	 *             if authorization is still pending (retry later)
	 * @throws SlowDownException
	 *             if polling too fast (increase interval)
	 * @throws ExpiredTokenException
	 *             if the device code has expired
	 * @throws AccessDeniedException
	 *             if the user denied access
	 */
	public String pollForAccessToken(String deviceCode) throws IOException {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(ACCESS_TOKEN_URL);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST"); //$NON-NLS-1$
			conn.setConnectTimeout(DEFAULT_TIMEOUT);
			conn.setReadTimeout(DEFAULT_TIMEOUT);
			conn.setDoOutput(true);

			// Set headers
			conn.setRequestProperty("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
			conn.setRequestProperty("Content-Type", //$NON-NLS-1$
					"application/x-www-form-urlencoded"); //$NON-NLS-1$

			// Build request body
			String body = "client_id=" + clientId + "&device_code=" //$NON-NLS-1$ //$NON-NLS-2$
					+ deviceCode + "&grant_type=urn:ietf:params:oauth:grant-type:device_code"; //$NON-NLS-1$

			// Write request
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}

			int responseCode = conn.getResponseCode();
			String json = readResponse(conn);

			if (responseCode == 200) {
				// Success - parse access token
				return parseAccessToken(json);
			}

			// Check for error conditions
			String error = extractError(json);
			if ("authorization_pending".equals(error)) { //$NON-NLS-1$
				throw new AuthorizationPendingException();
			} else if ("slow_down".equals(error)) { //$NON-NLS-1$
				throw new SlowDownException();
			} else if ("expired_token".equals(error)) { //$NON-NLS-1$
				throw new ExpiredTokenException();
			} else if ("access_denied".equals(error)) { //$NON-NLS-1$
				throw new AccessDeniedException();
			}

			throw new IOException(
					"Failed to poll for access token: " + error); //$NON-NLS-1$

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Parses device code response from JSON
	 *
	 * @param json
	 *            the JSON response to parse
	 * @return the parsed device code response
	 */
	private DeviceCodeResponse parseDeviceCodeResponse(String json) {
		DeviceCodeResponse response = new DeviceCodeResponse();
		response.deviceCode = extractJsonString(json, "device_code"); //$NON-NLS-1$
		response.userCode = extractJsonString(json, "user_code"); //$NON-NLS-1$
		response.verificationUri = extractJsonString(json, "verification_uri"); //$NON-NLS-1$
		response.expiresIn = extractJsonInt(json, "expires_in"); //$NON-NLS-1$
		response.interval = extractJsonInt(json, "interval"); //$NON-NLS-1$
		return response;
	}

	/**
	 * Parses access token from JSON response
	 *
	 * @param json
	 *            the JSON response to parse
	 * @return the access token
	 */
	private String parseAccessToken(String json) {
		return extractJsonString(json, "access_token"); //$NON-NLS-1$
	}

	/**
	 * Extracts error from JSON response
	 *
	 * @param json
	 *            the JSON response to parse
	 * @return the error code or null if none
	 */
	private String extractError(String json) {
		return extractJsonString(json, "error"); //$NON-NLS-1$
	}

	/**
	 * Reads response from connection
	 *
	 * @param conn
	 *            the HTTP connection
	 * @return the response body
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
	 * Reads error response from connection
	 *
	 * @param conn
	 *            the HTTP connection
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

	// Simple JSON parsing helpers

	/**
	 * Extracts a string value from JSON by key
	 *
	 * @param json
	 *            the JSON string to parse
	 * @param key
	 *            the key to extract
	 * @return the string value or null if not found
	 */
	private String extractJsonString(String json, String key) {
		String pattern = "\"" + key + "\""; //$NON-NLS-1$ //$NON-NLS-2$
		int keyIndex = json.indexOf(pattern);
		if (keyIndex == -1) {
			return null;
		}

		int colonIndex = json.indexOf(':', keyIndex);
		if (colonIndex == -1) {
			return null;
		}

		int startIndex = colonIndex + 1;
		while (startIndex < json.length()
				&& Character.isWhitespace(json.charAt(startIndex))) {
			startIndex++;
		}

		if (startIndex >= json.length() || json.charAt(startIndex) != '"') {
			return null;
		}

		int endIndex = startIndex + 1;
		while (endIndex < json.length()) {
			char c = json.charAt(endIndex);
			if (c == '"' && json.charAt(endIndex - 1) != '\\') {
				break;
			}
			endIndex++;
		}

		return json.substring(startIndex + 1, endIndex);
	}

	/**
	 * Extracts an integer value from JSON by key
	 *
	 * @param json
	 *            the JSON string to parse
	 * @param key
	 *            the key to extract
	 * @return the integer value or 0 if not found
	 */
	private int extractJsonInt(String json, String key) {
		String pattern = "\"" + key + "\""; //$NON-NLS-1$ //$NON-NLS-2$
		int keyIndex = json.indexOf(pattern);
		if (keyIndex == -1) {
			return 0;
		}

		int colonIndex = json.indexOf(':', keyIndex);
		if (colonIndex == -1) {
			return 0;
		}

		int startIndex = colonIndex + 1;
		while (startIndex < json.length()
				&& Character.isWhitespace(json.charAt(startIndex))) {
			startIndex++;
		}

		int endIndex = startIndex;
		while (endIndex < json.length()
				&& Character.isDigit(json.charAt(endIndex))) {
			endIndex++;
		}

		if (endIndex == startIndex) {
			return 0;
		}

		try {
			return Integer.parseInt(json.substring(startIndex, endIndex));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Exception thrown when authorization is still pending
	 */
	public static class AuthorizationPendingException extends IOException {
		private static final long serialVersionUID = 1L;

		/**
		 * Creates a new exception
		 */
		public AuthorizationPendingException() {
			super("Authorization is still pending"); //$NON-NLS-1$
		}
	}

	/**
	 * Exception thrown when polling too fast
	 */
	public static class SlowDownException extends IOException {
		private static final long serialVersionUID = 1L;

		/**
		 * Creates a new exception
		 */
		public SlowDownException() {
			super("Polling too fast, slow down"); //$NON-NLS-1$
		}
	}

	/**
	 * Exception thrown when device code has expired
	 */
	public static class ExpiredTokenException extends IOException {
		private static final long serialVersionUID = 1L;

		/**
		 * Creates a new exception
		 */
		public ExpiredTokenException() {
			super("Device code has expired"); //$NON-NLS-1$
		}
	}

	/**
	 * Exception thrown when user denies access
	 */
	public static class AccessDeniedException extends IOException {
		private static final long serialVersionUID = 1L;

		/**
		 * Creates a new exception
		 */
		public AccessDeniedException() {
			super("User denied access"); //$NON-NLS-1$
		}
	}
}
