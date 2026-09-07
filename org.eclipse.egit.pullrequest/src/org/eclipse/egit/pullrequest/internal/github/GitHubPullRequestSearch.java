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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.github.GitHubSearchQueries.Query;

/**
 * Searches GitHub for pull requests and merges what the queries return.
 * <p>
 * GitHub answers {@code GET /search/issues} with HTTP 422 as soon as a single
 * qualifier in the query names a user, organization or repository the access
 * token may not search. That happens for organizations the token was never
 * SSO-authorized for, for private repositories when the token lacks the
 * {@code repo} scope, and for repositories that were renamed or deleted after
 * they were listed. A rejected query is therefore retried scope by scope, and
 * only the scopes GitHub really refuses are dropped; the listing fails only
 * when no scope is searchable at all.
 */
final class GitHubPullRequestSearch {

	/** Advice shown whenever GitHub refuses to search an owner scope. */
	static final String TOKEN_SCOPE_HINT = "Use a classic personal access " //$NON-NLS-1$
			+ "token with the 'repo' and 'read:org' scopes, and authorize " //$NON-NLS-1$
			+ "it for every organization that enforces SAML single " //$NON-NLS-1$
			+ "sign-on. Fine-grained tokens can only search the " //$NON-NLS-1$
			+ "repositories they were granted."; //$NON-NLS-1$

	/** Advice shown when GitHub throttles the token. */
	static final String RATE_LIMIT_HINT = "The GitHub API rate limit for " //$NON-NLS-1$
			+ "this access token is exhausted. Wait for the limit to " //$NON-NLS-1$
			+ "reset, or search fewer repositories by listing the wanted " //$NON-NLS-1$
			+ "owners on the pull request preference page."; //$NON-NLS-1$

	private GitHubPullRequestSearch() {
		// No instantiation
	}

	/**
	 * Reads one page of search results for a single query.
	 */
	@FunctionalInterface
	interface PageReader {

		/**
		 * Runs one search request.
		 *
		 * @param query
		 *            the search query
		 * @param page
		 *            the one-based page number
		 * @param pageSize
		 *            the number of results per page
		 * @return the pull requests on that page, in result order
		 * @throws IOException
		 *             if the request fails
		 */
		List<GitHubSearchHit> read(String query, int page, int pageSize)
				throws IOException;
	}

	/** Merged outcome of searching every scope. */
	static final class Result {

		private final List<GitHubSearchHit> hits;

		private final List<String> unsearchableScopes;

		Result(List<GitHubSearchHit> hits, List<String> unsearchableScopes) {
			this.hits = hits;
			this.unsearchableScopes = unsearchableScopes;
		}

		/**
		 * @return the found pull requests, without duplicates
		 */
		List<GitHubSearchHit> getHits() {
			return hits;
		}

		/**
		 * @return the owner qualifiers GitHub refused to search
		 */
		List<String> getUnsearchableScopes() {
			return unsearchableScopes;
		}
	}

	/**
	 * Runs every query and merges the pull requests they return.
	 *
	 * @param queries
	 *            the queries covering the scopes to search
	 * @param needed
	 *            how many results are needed per query
	 * @param pageSize
	 *            how many results to request per page
	 * @param reader
	 *            runs the individual search requests
	 * @return the merged result
	 * @throws IOException
	 *             if a search fails for any reason other than a refused
	 *             scope, or if GitHub refuses to search every scope
	 */
	static Result run(List<Query> queries, int needed, int pageSize,
			PageReader reader) throws IOException {
		Map<String, GitHubSearchHit> hits = new LinkedHashMap<>();
		List<String> unsearchable = new ArrayList<>();
		Deque<Query> pending = new ArrayDeque<>(queries);
		int scopeCount = 0;
		for (Query query : queries) {
			scopeCount += query.getScopes().size();
		}

		IOException lastRejection = null;
		while (!pending.isEmpty()) {
			Query query = pending.removeFirst();
			try {
				collect(query.getText(), needed, pageSize, reader, hits);
			} catch (IOException e) {
				if (isRateLimited(e)) {
					throw new IOException(RATE_LIMIT_HINT + " " //$NON-NLS-1$
							+ e.getMessage(), e);
				}
				if (!isRefusedScope(e)) {
					throw e;
				}
				List<Query> parts = query.split();
				if (parts.size() > 1) {
					// Find out which of the batched scopes is the bad one.
					for (int i = parts.size() - 1; i >= 0; i--) {
						pending.addFirst(parts.get(i));
					}
					continue;
				}
				String scope = query.getScopes().get(0);
				unsearchable.add(scope);
				lastRejection = e;
				Activator.logWarning("GitHub cannot search " + scope //$NON-NLS-1$
						+ "; its pull requests are not listed. " //$NON-NLS-1$
						+ e.getMessage());
			}
		}

		if (lastRejection != null && unsearchable.size() == scopeCount) {
			throw new IOException(allScopesRefused(unsearchable),
					lastRejection);
		}
		return new Result(new ArrayList<>(hits.values()),
				Collections.unmodifiableList(unsearchable));
	}

	private static void collect(String query, int needed, int pageSize,
			PageReader reader, Map<String, GitHubSearchHit> hits)
			throws IOException {
		int collected = 0;
		int page = 1;
		while (collected < needed) {
			List<GitHubSearchHit> pageHits = reader.read(query, page,
					pageSize);
			if (pageHits.isEmpty()) {
				return;
			}
			for (GitHubSearchHit hit : pageHits) {
				hits.putIfAbsent(hit.path(), hit);
			}
			collected += pageHits.size();
			if (pageHits.size() < pageSize) {
				return;
			}
			page++;
		}
	}

	/**
	 * Tells whether GitHub rejected the query because one of its qualifiers
	 * names something the token may not search.
	 *
	 * @param e
	 *            the failure reported by the search request
	 * @return true if the scope should be skipped rather than propagated
	 */
	static boolean isRefusedScope(IOException e) {
		String message = e.getMessage();
		return message != null && message.contains("HTTP 422"); //$NON-NLS-1$
	}

	/**
	 * Tells whether GitHub throttled the token.
	 * <p>
	 * Retrying the remaining scopes would only produce the same answer, so
	 * this aborts the listing instead of dropping scopes.
	 *
	 * @param e
	 *            the failure reported by the search request
	 * @return true if GitHub reported a primary or secondary rate limit
	 */
	static boolean isRateLimited(IOException e) {
		String message = e.getMessage();
		if (message == null) {
			return false;
		}
		if (!message.contains("HTTP 403") //$NON-NLS-1$
				&& !message.contains("HTTP 429")) { //$NON-NLS-1$
			return false;
		}
		return message.toLowerCase(Locale.ROOT).contains("rate limit"); //$NON-NLS-1$
	}

	/**
	 * Builds the message for the case where no scope could be searched.
	 *
	 * @param unsearchableScopes
	 *            the owner qualifiers GitHub refused to search
	 * @return an actionable error message
	 */
	static String allScopesRefused(List<String> unsearchableScopes) {
		return "GitHub refused to search " //$NON-NLS-1$
				+ String.join(", ", unsearchableScopes) //$NON-NLS-1$
				+ ". " + TOKEN_SCOPE_HINT; //$NON-NLS-1$
	}
}
