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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds GitHub issue-search queries that cover pull requests in repositories
 * the authenticated user owns or belongs to.
 */
final class GitHubSearchQueries {

	private GitHubSearchQueries() {
		// No instantiation
	}

	/**
	 * Builds one search query per accessible owner scope.
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
	 * @return search query strings for {@code GET /search/issues}
	 */
	static List<String> accessiblePullRequestQueries(String login,
			List<String> organizationLogins,
			List<String> collaboratorFullNames, String state,
			String authorUsername) {
		List<String> queries = new ArrayList<>();
		if (login != null && !login.isBlank()) {
			queries.add(pullRequestQuery("user:" + login, state, //$NON-NLS-1$
					authorUsername));
		}

		Set<String> organizations = new HashSet<>();
		if (organizationLogins != null) {
			for (String organization : organizationLogins) {
				if (organization == null || organization.isBlank()) {
					continue;
				}
				organizations.add(organization);
				queries.add(pullRequestQuery("org:" + organization, state, //$NON-NLS-1$
						authorUsername));
			}
		}

		if (collaboratorFullNames != null) {
			for (String fullName : collaboratorFullNames) {
				if (isCoveredByUserOrOrg(fullName, login, organizations)) {
					continue;
				}
				queries.add(pullRequestQuery("repo:" + fullName, state, //$NON-NLS-1$
						authorUsername));
			}
		}
		return queries;
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

	/**
	 * Returns the owner qualifier a query was built for.
	 *
	 * @param query
	 *            a query produced by
	 *            {@link #pullRequestQuery(String, String, String)}
	 * @return the {@code user:}, {@code org:} or {@code repo:} qualifier, or
	 *         the whole query if it carries none
	 */
	static String scopeOf(String query) {
		if (query == null) {
			return ""; //$NON-NLS-1$
		}
		for (String token : query.split(" ")) { //$NON-NLS-1$
			if (token.startsWith("user:") //$NON-NLS-1$
					|| token.startsWith("org:") //$NON-NLS-1$
					|| token.startsWith("repo:")) { //$NON-NLS-1$
				return token;
			}
		}
		return query;
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
