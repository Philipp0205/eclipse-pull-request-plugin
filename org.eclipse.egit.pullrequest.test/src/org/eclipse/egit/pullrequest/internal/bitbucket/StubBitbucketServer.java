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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal HTTP server on the loopback interface that answers canned responses,
 * used to exercise the request and error handling of {@link BitbucketClient}
 * without a real Bitbucket instance.
 */
class StubBitbucketServer implements AutoCloseable {

	/** A canned response. */
	static class Response {

		private final int status;

		private final String reason;

		private final String body;

		private final Map<String, String> headers = new LinkedHashMap<>();

		Response(int status, String reason, String body) {
			this.status = status;
			this.reason = reason;
			this.body = body;
			headers.put("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		Response header(String name, String value) {
			headers.put(name, value);
			return this;
		}

		Response contentType(String value) {
			headers.put("Content-Type", value); //$NON-NLS-1$
			return this;
		}
	}

	private final ServerSocket serverSocket;

	private final Map<String, Response> responses = new ConcurrentHashMap<>();

	private final List<String> requestedPaths = new CopyOnWriteArrayList<>();

	private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();

	private volatile boolean running = true;

	StubBitbucketServer() throws IOException {
		serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(this::serve, "stub-bitbucket"); //$NON-NLS-1$
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * @return the base URL of this server
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
		responses.put(path, response);
	}

	/**
	 * @return the paths that were requested, in order
	 */
	List<String> requestedPaths() {
		return requestedPaths;
	}

	/**
	 * @return the Authorization headers that were received, in order
	 */
	List<String> authorizationHeaders() {
		return authorizationHeaders;
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
		String[] parts = requestLine.split(" "); //$NON-NLS-1$
		String target = parts.length > 1 ? parts[1] : ""; //$NON-NLS-1$
		String line;
		while ((line = in.readLine()) != null && !line.isEmpty()) {
			if (line.toLowerCase(Locale.ROOT).startsWith("authorization:")) { //$NON-NLS-1$
				authorizationHeaders
						.add(line.substring(line.indexOf(':') + 1).trim());
			}
		}
		requestedPaths.add(target);

		int query = target.indexOf('?');
		String path = query < 0 ? target : target.substring(0, query);
		Response response = responses.get(path);
		if (response == null) {
			response = new Response(404, "Not Found", //$NON-NLS-1$
					"{\"errors\":[{\"message\":\"nothing here\"}]}"); //$NON-NLS-1$
		}
		write(client.getOutputStream(), response);
	}

	private void write(OutputStream out, Response response) throws IOException {
		byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
		StringBuilder head = new StringBuilder();
		head.append("HTTP/1.1 ").append(response.status).append(' ') //$NON-NLS-1$
				.append(response.reason).append("\r\n"); //$NON-NLS-1$
		for (Map.Entry<String, String> header : response.headers.entrySet()) {
			head.append(header.getKey()).append(": ") //$NON-NLS-1$
					.append(header.getValue()).append("\r\n"); //$NON-NLS-1$
		}
		head.append("Content-Length: ").append(body.length).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
		head.append("Connection: close\r\n\r\n"); //$NON-NLS-1$
		out.write(head.toString().getBytes(StandardCharsets.UTF_8));
		out.write(body);
		out.flush();
	}
}
