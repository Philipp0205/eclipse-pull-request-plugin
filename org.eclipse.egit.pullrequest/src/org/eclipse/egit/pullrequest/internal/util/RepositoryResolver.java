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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.util.RepositoryUrlMatcher.MatchQuality;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

/**
 * Resolves the local clone of the repository a pull request belongs to.
 * <p>
 * Candidate repositories are collected from everything the workspace knows
 * about: repositories EGit currently has open, repositories of imported
 * projects, and repositories configured in the Git Repositories view. Each
 * candidate's remote URLs are then matched against the pull request's
 * repository identity, and the best match wins.
 */
@SuppressWarnings("restriction")
public class RepositoryResolver {

	private static final String PROVIDER_GITHUB = "GITHUB"; //$NON-NLS-1$

	private static final String GITHUB_HOST = "github.com"; //$NON-NLS-1$

	private RepositoryResolver() {
		// Utility class, no instances
	}

	/**
	 * Resolves the local Git repository for a pull request by matching remote
	 * URLs against the pull request's target repository.
	 * <p>
	 * If no clone of the target repository is found, the source repository is
	 * tried as well so that pull requests from a fork can be reviewed in a
	 * clone of that fork.
	 *
	 * @param pr
	 *            the pull request
	 * @return the matching repository, or {@code null} if not found
	 */
	public static Repository resolve(PullRequest pr) {
		if (pr == null) {
			return null;
		}
		RepositoryUrlMatcher target = matcherFor(
				pr.getToRef() != null ? pr.getToRef().getRepository() : null);
		RepositoryUrlMatcher source = matcherFor(
				pr.getFromRef() != null ? pr.getFromRef().getRepository()
						: null);
		if (target == null && source == null) {
			Activator.logWarning(
					"Pull request has no repository information, cannot" //$NON-NLS-1$
							+ " locate a local clone"); //$NON-NLS-1$
			return null;
		}

		Collection<Repository> candidates = collectCandidates();

		Repository match = null;
		if (target != null) {
			match = findBestMatch(candidates, target);
		}
		if (match == null && source != null) {
			match = findBestMatch(candidates, source);
			if (match != null) {
				Activator.logInfo(
						"Using clone of the pull request source repository " //$NON-NLS-1$
								+ source.describe() + ": " //$NON-NLS-1$
								+ match.getDirectory());
			}
		}
		if (match == null) {
			Activator.logWarning(describeFailure(
					target != null ? target : source, candidates));
		} else {
			RepositoryUrlMatcher used = target != null ? target : source;
			MatchQuality quality = matchQuality(match, used);
			String message = "Resolved pull request repository " //$NON-NLS-1$
					+ used.describe() + " to " + match.getDirectory() //$NON-NLS-1$
					+ " (match: " + quality + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			if (quality.ordinal() < MatchQuality.HOST_ALIAS.ordinal()) {
				// The clone only partially matches, so say so instead of
				// silently showing a diff of a different repository.
				Activator.logWarning(message);
			} else {
				Activator.logDebug(message);
			}
		}
		return match;
	}

	/**
	 * Describes the repository a pull request belongs to, for use in
	 * messages.
	 *
	 * @param pr
	 *            the pull request; may be {@code null}
	 * @return the description, or {@code null} if the repository is unknown
	 */
	public static String describeRepository(PullRequest pr) {
		if (pr == null || pr.getToRef() == null) {
			return null;
		}
		RepositoryUrlMatcher matcher = matcherFor(
				pr.getToRef().getRepository());
		return matcher != null ? matcher.describe() : null;
	}

	/**
	 * Finds the name of the remote in a repository that points at the given
	 * pull request repository.
	 *
	 * @param repository
	 *            the local repository
	 * @param prRepository
	 *            the hosted repository to look for
	 * @return the remote name, or {@code null} if no remote matches
	 */
	public static String findRemoteName(Repository repository,
			PullRequest.Repository prRepository) {
		RepositoryUrlMatcher matcher = matcherFor(prRepository);
		if (repository == null || matcher == null) {
			return null;
		}
		String best = null;
		MatchQuality bestQuality = MatchQuality.NONE;
		for (RemoteConfig remote : remoteConfigs(repository)) {
			MatchQuality quality = matcher.matchBest(urls(remote));
			if (quality.ordinal() > bestQuality.ordinal()) {
				best = remote.getName();
				bestQuality = quality;
				if (bestQuality == MatchQuality.EXACT) {
					break;
				}
			}
		}
		return best;
	}

	/**
	 * Returns the best matching repository of the given candidates.
	 *
	 * @param candidates
	 *            the repositories to inspect
	 * @param matcher
	 *            the matcher describing the wanted repository
	 * @return the best match, or {@code null} if no candidate matches
	 */
	public static Repository findBestMatch(
			Collection<Repository> candidates,
			RepositoryUrlMatcher matcher) {
		if (candidates == null || matcher == null) {
			return null;
		}
		Repository best = null;
		MatchQuality bestQuality = MatchQuality.NONE;
		for (Repository candidate : candidates) {
			MatchQuality quality = matchQuality(candidate, matcher);
			if (quality.ordinal() > bestQuality.ordinal()) {
				best = candidate;
				bestQuality = quality;
				if (bestQuality == MatchQuality.EXACT) {
					break;
				}
			}
		}
		return best;
	}

	/**
	 * Creates a matcher for the identity of a pull request repository.
	 *
	 * @param prRepository
	 *            the repository as reported by the provider; may be
	 *            {@code null}
	 * @return the matcher, or {@code null} if the repository is unknown
	 */
	public static RepositoryUrlMatcher matcherFor(
			PullRequest.Repository prRepository) {
		if (prRepository == null) {
			return null;
		}
		String project = prRepository.getProject() != null
				? prRepository.getProject().getKey()
				: null;
		String slug = prRepository.getSlug();
		String cloneUrl = prRepository.getCloneUrl();

		if (PROVIDER_GITHUB.equalsIgnoreCase(providerType())) {
			return RepositoryUrlMatcher.create(project, slug, cloneUrl,
					GITHUB_HOST);
		}
		// Bitbucket is the default provider, so its server URL is also used
		// when the provider preference was never written.
		return RepositoryUrlMatcher.create(project, slug, cloneUrl,
				preference(PRPreferences.BITBUCKET_SERVER_URL));
	}

	/**
	 * Rates how well a local repository matches a wanted repository.
	 *
	 * @param repository
	 *            the local repository; may be {@code null}
	 * @param matcher
	 *            the matcher describing the wanted repository
	 * @return the best match quality of all remotes, never {@code null}
	 */
	public static MatchQuality matchQuality(Repository repository,
			RepositoryUrlMatcher matcher) {
		if (repository == null) {
			return MatchQuality.NONE;
		}
		MatchQuality best = MatchQuality.NONE;
		for (RemoteConfig remote : remoteConfigs(repository)) {
			MatchQuality quality = matcher.matchBest(urls(remote));
			if (quality.ordinal() > best.ordinal()) {
				best = quality;
				if (best == MatchQuality.EXACT) {
					break;
				}
			}
		}
		return best;
	}

	private static List<RemoteConfig> remoteConfigs(Repository repository) {
		try {
			return RemoteConfig.getAllRemoteConfigs(repository.getConfig());
		} catch (Exception e) {
			Activator.logWarning("Cannot read remotes of repository " //$NON-NLS-1$
					+ repository.getDirectory() + ": " + e.getMessage()); //$NON-NLS-1$
			return new ArrayList<>();
		}
	}

	private static List<String> urls(RemoteConfig remote) {
		List<String> urls = new ArrayList<>();
		for (URIish uri : remote.getURIs()) {
			urls.add(uri.toString());
		}
		for (URIish uri : remote.getPushURIs()) {
			urls.add(uri.toString());
		}
		return urls;
	}

	/**
	 * Collects every local repository the workspace knows about.
	 * <p>
	 * EGit's repository cache only contains repositories that happen to be
	 * open right now, so configured repositories and repositories of imported
	 * projects have to be added explicitly.
	 *
	 * @return the candidate repositories, without duplicates
	 */
	private static Collection<Repository> collectCandidates() {
		Map<File, Repository> byGitDir = new LinkedHashMap<>();
		for (Repository repository : RepositoryCache.INSTANCE
				.getAllRepositories()) {
			add(byGitDir, repository);
		}
		for (IProject project : projects()) {
			try {
				add(byGitDir, ResourceUtil.getRepository(project));
			} catch (Exception e) {
				Activator.logWarning("Cannot determine repository of project " //$NON-NLS-1$
						+ project.getName() + ": " + e.getMessage()); //$NON-NLS-1$
			}
		}
		for (String gitDir : configuredRepositories()) {
			try {
				add(byGitDir, RepositoryCache.INSTANCE
						.lookupRepository(new File(gitDir)));
			} catch (Exception e) {
				Activator.logWarning("Cannot open configured repository " //$NON-NLS-1$
						+ gitDir + ": " + e.getMessage()); //$NON-NLS-1$
			}
		}
		return byGitDir.values();
	}

	private static void add(Map<File, Repository> byGitDir,
			Repository repository) {
		if (repository == null || repository.getDirectory() == null) {
			return;
		}
		byGitDir.putIfAbsent(repository.getDirectory().getAbsoluteFile(),
				repository);
	}

	private static IProject[] projects() {
		try {
			return ResourcesPlugin.getWorkspace().getRoot().getProjects();
		} catch (Exception e) {
			// No workspace available, for instance in headless tests.
			return new IProject[0];
		}
	}

	private static Set<String> configuredRepositories() {
		try {
			return new LinkedHashSet<>(
					RepositoryUtil.INSTANCE.getConfiguredRepositories());
		} catch (Exception e) {
			return new LinkedHashSet<>();
		}
	}

	private static String describeFailure(RepositoryUrlMatcher matcher,
			Collection<Repository> candidates) {
		StringBuilder message = new StringBuilder(
				"No local Git repository found for pull request repository "); //$NON-NLS-1$
		message.append(matcher.describe());
		message.append(". Searched ").append(candidates.size()) //$NON-NLS-1$
				.append(" repositories:"); //$NON-NLS-1$
		for (Repository candidate : candidates) {
			message.append("\n  ").append(candidate.getDirectory()); //$NON-NLS-1$
			for (RemoteConfig remote : remoteConfigs(candidate)) {
				message.append("\n    ").append(remote.getName()).append(" -> ") //$NON-NLS-1$ //$NON-NLS-2$
						.append(String.join(", ", urls(remote))); //$NON-NLS-1$
			}
		}
		return message.toString();
	}

	private static String providerType() {
		return preference(PRPreferences.PULLREQUEST_PROVIDER_TYPE);
	}

	private static String preference(String key) {
		try {
			return Activator.getDefault().getPreferenceStore().getString(key);
		} catch (Exception e) {
			// Plug-in not started, for instance in headless tests.
			return null;
		}
	}
}
