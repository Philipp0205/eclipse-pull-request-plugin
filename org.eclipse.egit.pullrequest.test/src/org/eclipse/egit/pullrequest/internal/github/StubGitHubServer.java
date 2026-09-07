/*******************************************************************************
 * Copyright (C) 2026, Philipp0205 and others
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal HTTP server on the loopback interface that answers canned responses,
 * used to exercise {@link GitHubClient} without talking to github.com.
 * <p>
 * Responses are registered either for a request path or for a fragment of the
 * query string, so that the different searches sent to
 * {@code /search/issues} can be answered individually.
 */
class StubGitHubServer implements AutoCloseable {

	/** A canned response. */
	static class Response {

		private final int status;

		private final String reason;

		private final String body;

		Response(int status, String reason, String body) {
			this.status = status;
			this.reason = reason;
			this.body = body;
		}
	}

	private final ServerSocket serverSocket;

	private final Map<String, Response> byPath = new ConcurrentHashMap<>();

	private final Map<String, Response> byQuery = new ConcurrentHashMap<>();

	private final List<String> requestedTargets = new CopyOnWriteArrayList<>();

	private volatile boolean running = true;

	StubGitHubServer() throws IOException {
		serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(this::serve, "stub-github"); //$NON-NLS-1$
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * @return the API base URL of this server
	 */
	String url() {
		return "http://" //$NON-NLS-1$
				+ serverSocket.getInetAddress().getHostAddress() + ':'
				+ serverSocket.getLocalPort();
	}

	/**
	 * Registers the response for a path, ignoring any query string.
	 *
	 * @param path
	 *            the request path
	 * @param response
	 *            the response to send
	 */
	void on(String path, Response response) {
		byPath.put(path, response);
	}

	/**
	 * Registers the response for every request whose target contains the given
	 * fragment. Checked before the path responses.
	 *
	 * @param fragment
	 *            a fragment of the encoded request target
	 * @param response
	 *            the response to send
	 */
	void onQueryContaining(String fragment, Response response) {
		byQuery.put(fragment, response);
	}

	/**
	 * @return the request targets that were received, in order
	 */
	List<String> requestedTargets() {
		return requestedTargets;
	}

	@Override
	public void close() throws IOException {
		running = false;
		serverSocket.close();
	}

	private void serve() {
		while (running) {
			try (Socket client = serverSocket.accept()) {
				handle(client);
			} catch (IOException e) {
				return;
			}
		}
	}

	private void handle(Socket client) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(
				client.getInputStream(), StandardCharsets.UTF_8));
		String requestLine = in.readLine();
		if (requestLine == null) {
			return;
		}
		String line;
		while ((line = in.readLine()) != null && !line.isEmpty()) {
			// Headers are not inspected, but must be drained.
		}
		String[] parts = requestLine.split(" "); //$NON-NLS-1$
		String target = parts.length > 1 ? parts[1] : ""; //$NON-NLS-1$
		requestedTargets.add(target);
		write(client.getOutputStream(), responseFor(target));
	}

	private Response responseFor(String target) {
		for (Map.Entry<String, Response> entry : byQuery.entrySet()) {
			if (target.contains(entry.getKey())) {
				return entry.getValue();
			}
		}
		int query = target.indexOf('?');
		String path = query < 0 ? target : target.substring(0, query);
		Response response = byPath.get(path);
		if (response != null) {
			return response;
		}
		return new Response(404, "Not Found", //$NON-NLS-1$
				"{\"message\":\"Not Found\"}"); //$NON-NLS-1$
	}

	private void write(OutputStream out, Response response)
			throws IOException {
		byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
		StringBuilder head = new StringBuilder();
		head.append("HTTP/1.1 ").append(response.status).append(' ') //$NON-NLS-1$
				.append(response.reason).append("\r\n"); //$NON-NLS-1$
		head.append("Content-Type: application/json\r\n"); //$NON-NLS-1$
		head.append("Content-Length: ").append(body.length).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
		head.append("Connection: close\r\n\r\n"); //$NON-NLS-1$
		out.write(head.toString().getBytes(StandardCharsets.UTF_8));
		out.write(body);
		out.flush();
	}
}
