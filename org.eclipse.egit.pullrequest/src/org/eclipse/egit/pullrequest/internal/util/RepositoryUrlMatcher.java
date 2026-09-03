/*******************************************************************************
 * Copyright (C) 2026, Philipp Hoenisch and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jgit.transport.URIish;

/**
 * Matches Git remote URLs against the identity of a hosted repository.
 * <p>
 * A hosted repository is identified by the server host plus the pair
 * {@code project/slug} (Bitbucket) or {@code owner/repo} (GitHub). The same
 * repository can be cloned through very different URLs, all of which have to
 * be recognized:
 *
 * <pre>
 * https://server/bitbucket/scm/SOC/smartest.git
 * https://user&#64;server/bitbucket/scm/soc/smartest
 * ssh://git&#64;server:7999/SOC/smartest.git
 * git&#64;server:SOC/smartest.git
 * </pre>
 *
 * Matching therefore compares the host separately from the last two path
 * segments instead of looking for a fixed substring, and reports how good a
 * match is so that callers can prefer the best candidate.
 */
public final class RepositoryUrlMatcher {

	/**
	 * How well a remote URL matches a repository identity, ordered from no
	 * match to a perfect match.
	 */
	public enum MatchQuality {

		/** The remote URL denotes a different repository. */
		NONE,

		/**
		 * Only the repository name matches, on the expected host. The project
		 * or owner segment is different or absent.
		 */
		SLUG_ONLY,

		/**
		 * Project and repository name match, but the host is unrelated, for
		 * instance a local mirror of the server.
		 */
		PATH_ONLY,

		/**
		 * Project and repository name match on a host that is a plausible
		 * alias of the server, for instance a short host name or a dedicated
		 * SSH host in the same domain.
		 */
		HOST_ALIAS,

		/** Host, project and repository name all match. */
		EXACT;
	}

	private final Set<String> hosts;

	private final String project;

	private final String slug;

	private RepositoryUrlMatcher(Set<String> hosts, String project,
			String slug) {
		this.hosts = hosts;
		this.project = project;
		this.slug = slug;
	}

	/**
	 * Creates a matcher for a hosted repository.
	 *
	 * @param project
	 *            the Bitbucket project key or GitHub owner of the repository;
	 *            may be {@code null} if unknown
	 * @param slug
	 *            the repository slug or name
	 * @param serverUrls
	 *            URLs or bare host names the repository is served from, in
	 *            descending order of trust; {@code null} entries are ignored
	 * @return the matcher, or {@code null} if no repository name was given
	 */
	public static RepositoryUrlMatcher create(String project, String slug,
			String... serverUrls) {
		if (isEmpty(slug)) {
			return null;
		}
		Set<String> hostSet = new LinkedHashSet<>();
		if (serverUrls != null) {
			for (String serverUrl : serverUrls) {
				String host = extractHost(serverUrl);
				if (host != null) {
					hostSet.add(host);
				}
			}
		}
		return new RepositoryUrlMatcher(hostSet, normalize(project),
				normalize(slug));
	}

	/**
	 * Rates how well a remote URL matches this repository.
	 *
	 * @param remoteUrl
	 *            the remote URL to inspect; may be {@code null}
	 * @return the match quality, never {@code null}
	 */
	public MatchQuality match(String remoteUrl) {
		if (isEmpty(remoteUrl)) {
			return MatchQuality.NONE;
		}
		List<String> segments = pathSegments(remoteUrl);
		if (segments.isEmpty()) {
			return MatchQuality.NONE;
		}
		boolean slugMatches = slug
				.equals(segments.get(segments.size() - 1));
		if (!slugMatches) {
			return MatchQuality.NONE;
		}
		boolean projectMatches = project != null && segments.size() >= 2
				&& project.equals(segments.get(segments.size() - 2));

		String remoteHost = extractHost(remoteUrl);
		if (remoteHost != null) {
			for (String host : hosts) {
				if (host.equals(remoteHost)) {
					return projectMatches ? MatchQuality.EXACT
							: MatchQuality.SLUG_ONLY;
				}
			}
			if (projectMatches) {
				for (String host : hosts) {
					if (isHostAlias(host, remoteHost)) {
						return MatchQuality.HOST_ALIAS;
					}
				}
			}
		}
		return projectMatches ? MatchQuality.PATH_ONLY : MatchQuality.NONE;
	}

	/**
	 * Rates how well any of the given remote URLs matches this repository.
	 *
	 * @param remoteUrls
	 *            the remote URLs to inspect; may be {@code null}
	 * @return the best match quality of all URLs, never {@code null}
	 */
	public MatchQuality matchBest(Collection<String> remoteUrls) {
		MatchQuality best = MatchQuality.NONE;
		if (remoteUrls == null) {
			return best;
		}
		for (String remoteUrl : remoteUrls) {
			MatchQuality quality = match(remoteUrl);
			if (quality.ordinal() > best.ordinal()) {
				best = quality;
				if (best == MatchQuality.EXACT) {
					break;
				}
			}
		}
		return best;
	}

	/**
	 * Returns a human readable description of the repository this matcher
	 * looks for, for use in log messages.
	 *
	 * @return the description
	 */
	public String describe() {
		StringBuilder result = new StringBuilder();
		if (project != null) {
			result.append(project).append('/');
		}
		result.append(slug);
		if (!hosts.isEmpty()) {
			result.append(" on ").append(String.join(", ", hosts)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result.toString();
	}

	@Override
	public String toString() {
		return describe();
	}

	/**
	 * Extracts the host name from a URL, a bare host name, or a host name
	 * followed by a path.
	 *
	 * @param url
	 *            the URL to inspect; may be {@code null}
	 * @return the lower case host name, or {@code null} if the URL denotes a
	 *         local path without a host
	 */
	static String extractHost(String url) {
		if (isEmpty(url)) {
			return null;
		}
		String candidate = url.trim();
		try {
			String host = new URIish(candidate).getHost();
			if (!isEmpty(host)) {
				return normalize(host);
			}
		} catch (Exception e) {
			// Fall through to manual parsing below.
		}
		// URIish reports no host for bare host names such as
		// "server.example.com/bitbucket", which users do enter as server URL.
		String rest = stripScheme(candidate);
		int at = rest.indexOf('@');
		if (at >= 0) {
			rest = rest.substring(at + 1);
		}
		int end = rest.length();
		for (int i = 0; i < rest.length(); i++) {
			char c = rest.charAt(i);
			if (c == '/' || c == ':' || c == '\\') {
				end = i;
				break;
			}
		}
		String host = rest.substring(0, end);
		if (isEmpty(host) || host.indexOf('.') < 0) {
			// Without a dot this is much more likely a path segment than a
			// host name, so do not guess.
			return null;
		}
		return normalize(host);
	}

	/**
	 * Splits the repository path of a remote URL into normalized segments.
	 *
	 * @param url
	 *            the remote URL
	 * @return the lower case path segments without a trailing {@code .git}
	 */
	static List<String> pathSegments(String url) {
		List<String> segments = new ArrayList<>();
		if (isEmpty(url)) {
			return segments;
		}
		String path = null;
		try {
			path = new URIish(url.trim()).getPath();
		} catch (Exception e) {
			// Fall through to manual parsing below.
		}
		if (isEmpty(path)) {
			path = stripHost(url.trim());
		}
		for (String segment : path.replace('\\', '/').split("/")) { //$NON-NLS-1$
			if (!segment.isEmpty()) {
				segments.add(normalize(segment));
			}
		}
		if (!segments.isEmpty()) {
			int last = segments.size() - 1;
			String name = segments.get(last);
			if (name.endsWith(".git") && name.length() > 4) { //$NON-NLS-1$
				segments.set(last, name.substring(0, name.length() - 4));
			}
		}
		return segments;
	}

	private static boolean isHostAlias(String expected, String actual) {
		if (expected.equals(actual)) {
			return true;
		}
		String[] expectedLabels = expected.split("\\."); //$NON-NLS-1$
		String[] actualLabels = actual.split("\\."); //$NON-NLS-1$
		if (expectedLabels.length == 0 || actualLabels.length == 0) {
			return false;
		}
		// A short host name configured in DNS search domains or in the SSH
		// configuration, such as "server" for "server.example.com".
		if (expectedLabels[0].equals(actualLabels[0])) {
			return true;
		}
		// A dedicated Git host in the same domain, such as "git.example.com"
		// for the web front end "bitbucket.example.com".
		if (expectedLabels.length >= 2 && actualLabels.length >= 2) {
			String expectedDomain = expectedLabels[expectedLabels.length - 2]
					+ '.' + expectedLabels[expectedLabels.length - 1];
			String actualDomain = actualLabels[actualLabels.length - 2] + '.'
					+ actualLabels[actualLabels.length - 1];
			return expectedDomain.equals(actualDomain);
		}
		return false;
	}

	private static String stripScheme(String url) {
		int scheme = url.indexOf("://"); //$NON-NLS-1$
		return scheme >= 0 ? url.substring(scheme + 3) : url;
	}

	private static String stripHost(String url) {
		String rest = stripScheme(url);
		int at = rest.indexOf('@');
		if (at >= 0) {
			rest = rest.substring(at + 1);
		}
		int slash = rest.indexOf('/');
		int colon = rest.indexOf(':');
		if (colon >= 0 && (slash < 0 || colon < slash)) {
			String afterColon = rest.substring(colon + 1);
			int pathStart = afterColon.indexOf('/');
			if (pathStart > 0 && isNumeric(
					afterColon.substring(0, pathStart))) {
				// host:port/path
				return afterColon.substring(pathStart);
			}
			return afterColon;
		}
		return slash >= 0 ? rest.substring(slash) : rest;
	}

	private static boolean isNumeric(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				return false;
			}
		}
		return !value.isEmpty();
	}

	private static String normalize(String value) {
		return value == null ? null
				: value.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean isEmpty(String value) {
		return value == null || value.trim().isEmpty();
	}
}
