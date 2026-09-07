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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.egit.pullrequest.Activator;

/**
 * Runs one GitHub issue search per owner scope and merges the results.
 * <p>
 * GitHub answers {@code GET /search/issues} with HTTP 422 as soon as a single
 * qualifier in the query names a user, organization or repository the access
 * token may not search. That happens for organizations the token was never
 * SSO-authorized for, for private repositories when the token lacks the
 * {@code repo} scope, and for repositories that were renamed or deleted after
 * they were listed. Because every scope is searched with its own query, such a
 * scope is skipped instead of failing the whole listing; only when GitHub
 * rejects every scope is the failure reported to the caller.
 */
final class GitHubPullRequestSearch {

	/** Advice shown whenever GitHub refuses to search an owner scope. */
	static final String TOKEN_SCOPE_HINT = "Use a classic personal access " //$NON-NLS-1$
			+ "token with the 'repo' and 'read:org' scopes, and authorize " //$NON-NLS-1$
			+ "it for every organization that enforces SAML single " //$NON-NLS-1$
			+ "sign-on. Fine-grained tokens can only search the " //$NON-NLS-1$
			+ "repositories they were granted."; //$NON-NLS-1$

	private GitHubPullRequestSearch() {
		// No instantiation
	}

	/**
	 * Reads one page of pull request paths for a single search query.
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
		 * @return REST pull request paths in result order
		 * @throws IOException
		 *             if the request fails
		 */
		List<String> read(String query, int page, int pageSize)
				throws IOException;
	}

	/** Merged outcome of searching every owner scope. */
	static final class Result {

		private final List<String> pullRequestPaths;

		private final List<String> unsearchableScopes;

		Result(List<String> pullRequestPaths,
				List<String> unsearchableScopes) {
			this.pullRequestPaths = pullRequestPaths;
			this.unsearchableScopes = unsearchableScopes;
		}

		/**
		 * @return REST pull request paths without duplicates
		 */
		List<String> getPullRequestPaths() {
			return pullRequestPaths;
		}

		/**
		 * @return the owner qualifiers GitHub refused to search
		 */
		List<String> getUnsearchableScopes() {
			return unsearchableScopes;
		}
	}

	/**
	 * Searches every query and merges the pull requests they return.
	 *
	 * @param queries
	 *            one search query per owner scope
	 * @param needed
	 *            how many results are needed per scope
	 * @param pageSize
	 *            how many results to request per page
	 * @param reader
	 *            runs the individual search requests
	 * @return the merged result
	 * @throws IOException
	 *             if a search fails for any reason other than an unsearchable
	 *             scope, or if GitHub refuses to search every scope
	 */
	static Result run(List<String> queries, int needed, int pageSize,
			PageReader reader) throws IOException {
		LinkedHashSet<String> paths = new LinkedHashSet<>();
		List<String> unsearchable = new ArrayList<>();
		IOException lastRejection = null;
		for (String query : queries) {
			try {
				collect(query, needed, pageSize, reader, paths);
			} catch (IOException e) {
				if (!isUnsearchableScope(e)) {
					throw e;
				}
				String scope = GitHubSearchQueries.scopeOf(query);
				unsearchable.add(scope);
				lastRejection = e;
				Activator.logWarning(
						"GitHub cannot search " + scope //$NON-NLS-1$
								+ "; its pull requests are not listed. " //$NON-NLS-1$
								+ e.getMessage());
			}
		}
		if (lastRejection != null && unsearchable.size() == queries.size()) {
			throw new IOException(allScopesRejected(unsearchable),
					lastRejection);
		}
		return new Result(new ArrayList<>(paths),
				Collections.unmodifiableList(unsearchable));
	}

	private static void collect(String query, int needed, int pageSize,
			PageReader reader, LinkedHashSet<String> paths)
			throws IOException {
		int collected = 0;
		int page = 1;
		while (collected < needed) {
			List<String> pagePaths = reader.read(query, page, pageSize);
			if (pagePaths.isEmpty()) {
				return;
			}
			paths.addAll(pagePaths);
			collected += pagePaths.size();
			if (pagePaths.size() < pageSize) {
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
	static boolean isUnsearchableScope(IOException e) {
		String message = e.getMessage();
		return message != null && message.contains("HTTP 422"); //$NON-NLS-1$
	}

	/**
	 * Builds the message for the case where no scope could be searched at all.
	 *
	 * @param unsearchableScopes
	 *            the owner qualifiers GitHub refused to search
	 * @return an actionable error message
	 */
	static String allScopesRejected(List<String> unsearchableScopes) {
		return "GitHub refused to search " //$NON-NLS-1$
				+ String.join(", ", unsearchableScopes) //$NON-NLS-1$
				+ ". " + TOKEN_SCOPE_HINT; //$NON-NLS-1$
	}
}
