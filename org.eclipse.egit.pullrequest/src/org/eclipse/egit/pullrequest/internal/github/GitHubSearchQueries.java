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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds GitHub issue-search queries that cover pull requests in the
 * repositories a token may search.
 * <p>
 * GitHub combines repeated owner qualifiers of the same kind with OR, so
 * several scopes fit into one request. Batching them matters: a user who
 * collaborates on a few hundred repositories would otherwise need a few
 * hundred search requests per refresh and exhaust the API rate limit.
 */
final class GitHubSearchQueries {

	/**
	 * How many owner qualifiers to put into one query. Kept well below the
	 * length at which GitHub starts rejecting queries.
	 */
	private static final int SCOPES_PER_QUERY = 20;

	private GitHubSearchQueries() {
		// No instantiation
	}

	/** One search request, covering one or more owner scopes. */
	static final class Query {

		private final List<String> scopes;

		private final String state;

		private final String authorUsername;

		private final String text;

		Query(List<String> scopes, String state, String authorUsername) {
			this.scopes = List.copyOf(scopes);
			this.state = state;
			this.authorUsername = authorUsername;
			this.text = pullRequestQuery(String.join(" ", scopes), state, //$NON-NLS-1$
					authorUsername);
		}

		/**
		 * @return the owner qualifiers this query covers
		 */
		List<String> getScopes() {
			return scopes;
		}

		/**
		 * @return the query string for {@code GET /search/issues}
		 */
		String getText() {
			return text;
		}

		/**
		 * Splits a batched query so that every scope is searched on its own.
		 * <p>
		 * Used after GitHub rejected the batch, to find out which of its
		 * scopes is the unsearchable one instead of dropping them all.
		 *
		 * @return one query per scope, or this query if it has only one
		 */
		List<Query> split() {
			if (scopes.size() < 2) {
				return List.of(this);
			}
			List<Query> parts = new ArrayList<>();
			for (String scope : scopes) {
				parts.add(new Query(List.of(scope), state, authorUsername));
			}
			return parts;
		}
	}

	/**
	 * Builds the queries covering every scope the authenticated user can
	 * reach.
	 * <p>
	 * Personal repositories use {@code user:login}, organization membership
	 * uses {@code org:name}, and remaining collaborator repositories use
	 * {@code repo:owner/name}. Author is only applied when the list filter
	 * requests it.
	 *
	 * @param login
	 *            authenticated GitHub login
	 * @param organizationLogins
	 *            organizations the user belongs to
	 * @param collaboratorFullNames
	 *            {@code owner/name} repositories the user collaborates on
	 * @param state
	 *            pull request state filter, or {@code null} for open
	 * @param authorUsername
	 *            author filter, or {@code null} for every author
	 * @return the queries to run
	 */
	static List<Query> accessiblePullRequestQueries(String login,
			List<String> organizationLogins,
			List<String> collaboratorFullNames, String state,
			String authorUsername) {
		List<String> scopes = new ArrayList<>();
		if (login != null && !login.isBlank()) {
			scopes.add("user:" + login); //$NON-NLS-1$
		}

		Set<String> organizations = new HashSet<>();
		if (organizationLogins != null) {
			for (String organization : organizationLogins) {
				if (organization == null || organization.isBlank()) {
					continue;
				}
				organizations.add(organization);
				scopes.add("org:" + organization); //$NON-NLS-1$
			}
		}

		if (collaboratorFullNames != null) {
			for (String fullName : collaboratorFullNames) {
				if (isCoveredByUserOrOrg(fullName, login, organizations)) {
					continue;
				}
				scopes.add("repo:" + fullName); //$NON-NLS-1$
			}
		}
		return batch(scopes, state, authorUsername);
	}

	/**
	 * Builds the queries for an explicitly configured set of scopes.
	 *
	 * @param scopes
	 *            entries as returned by {@link #parseScopes(String)}
	 * @param state
	 *            pull request state filter, or {@code null} for open
	 * @param authorUsername
	 *            author filter, or {@code null} for every author
	 * @return the queries to run
	 */
	static List<Query> configuredPullRequestQueries(List<String> scopes,
			String state, String authorUsername) {
		List<String> qualifiers = new ArrayList<>();
		for (String scope : scopes) {
			qualifiers.add(qualifierFor(scope));
		}
		return batch(qualifiers, state, authorUsername);
	}

	/**
	 * Reads the configured search scopes from their preference value.
	 * <p>
	 * Entries are separated by commas or whitespace. An entry may already be
	 * a {@code user:}, {@code org:} or {@code repo:} qualifier; otherwise
	 * {@code owner/name} means a single repository and a bare name means
	 * everything owned by that user or organization.
	 *
	 * @param value
	 *            the preference value, may be {@code null}
	 * @return the scope entries in the order they were written, without
	 *         duplicates
	 */
	static List<String> parseScopes(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		Set<String> scopes = new LinkedHashSet<>();
		for (String entry : value.split("[,\\s]+")) { //$NON-NLS-1$
			if (!entry.isBlank()) {
				scopes.add(entry.trim());
			}
		}
		return List.copyOf(scopes);
	}

	/**
	 * Turns a configured scope entry into a search qualifier.
	 * <p>
	 * A bare name becomes {@code user:name}, which GitHub also accepts for
	 * organizations.
	 *
	 * @param scope
	 *            the configured entry
	 * @return the qualifier to search with
	 */
	static String qualifierFor(String scope) {
		String trimmed = scope.trim();
		if (trimmed.startsWith("user:") //$NON-NLS-1$
				|| trimmed.startsWith("org:") //$NON-NLS-1$
				|| trimmed.startsWith("repo:")) { //$NON-NLS-1$
			return trimmed;
		}
		if (trimmed.indexOf('/') > 0) {
			return "repo:" + trimmed; //$NON-NLS-1$
		}
		return "user:" + trimmed; //$NON-NLS-1$
	}

	private static List<Query> batch(List<String> qualifiers, String state,
			String authorUsername) {
		List<String> unique = new ArrayList<>(
				new LinkedHashSet<>(qualifiers));
		List<Query> queries = new ArrayList<>();
		for (int from = 0; from < unique.size(); from += SCOPES_PER_QUERY) {
			int to = Math.min(from + SCOPES_PER_QUERY, unique.size());
			queries.add(new Query(unique.subList(from, to), state,
					authorUsername));
		}
		return Collections.unmodifiableList(queries);
	}

	static String pullRequestQuery(String ownerQualifier, String state,
			String authorUsername) {
		StringBuilder query = new StringBuilder("is:pr "); //$NON-NLS-1$
		query.append(ownerQualifier);
		if (authorUsername != null && !authorUsername.isBlank()) {
			query.append(" author:").append(authorUsername); //$NON-NLS-1$
		}
		if ("MERGED".equalsIgnoreCase(state)) { //$NON-NLS-1$
			query.append(" is:merged"); //$NON-NLS-1$
		} else if ("DECLINED".equalsIgnoreCase(state)) { //$NON-NLS-1$
			query.append(" is:closed is:unmerged"); //$NON-NLS-1$
		} else if (state == null || !"ALL".equalsIgnoreCase(state)) { //$NON-NLS-1$
			query.append(" is:open"); //$NON-NLS-1$
		}
		return query.toString();
	}

	static boolean isCoveredByUserOrOrg(String fullName, String login,
			Set<String> organizations) {
		if (fullName == null || fullName.isBlank()) {
			return true;
		}
		int slash = fullName.indexOf('/');
		if (slash <= 0) {
			return true;
		}
		String owner = fullName.substring(0, slash);
		return owner.equals(login)
				|| (organizations != null && organizations.contains(owner));
	}
}
