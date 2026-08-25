package org.eclipse.egit.pullrequest.internal.util;

import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

/**
 * Utility class for resolving JGit repositories from pull request metadata
 */
public class RepositoryResolver {

	private RepositoryResolver() {
		// Utility class, no instances
	}

	/**
	 * Resolves the local Git repository for a pull request by matching remote
	 * URLs against the pull request's target repository.
	 *
	 * @param pr
	 *            the pull request
	 * @return the matching repository, or null if not found
	 */
	public static Repository resolve(PullRequest pr) {
		String providerType = Activator.getDefault().getPreferenceStore()
				.getString(PRPreferences.PULLREQUEST_PROVIDER_TYPE);

		String serverUrl = null;
		String pathFragment = null;

		if ("BITBUCKET".equals(providerType)) { //$NON-NLS-1$
			serverUrl = Activator.getDefault().getPreferenceStore()
					.getString(PRPreferences.BITBUCKET_SERVER_URL);
			String projectKey = pr.getToRef().getRepository().getProject()
					.getKey();
			String repoSlug = pr.getToRef().getRepository().getSlug();
			// Build expected path fragment: /scm/{projectKey}/{repoSlug}
			pathFragment = "/scm/" + projectKey.toLowerCase() + "/" //$NON-NLS-1$ //$NON-NLS-2$
					+ repoSlug.toLowerCase();
		} else if ("GITHUB".equals(providerType)) { //$NON-NLS-1$
			serverUrl = "github.com"; //$NON-NLS-1$
			PullRequest.Repository target = pr.getToRef().getRepository();
			String owner = target.getProject().getKey();
			String repo = target.getSlug();
			// Build expected path fragment: /{owner}/{repo}
			pathFragment = "/" + owner.toLowerCase() + "/" //$NON-NLS-1$ //$NON-NLS-2$
					+ repo.toLowerCase();
		}

		if (serverUrl == null || pathFragment == null) {
			return null;
		}

		// Search all repositories in the workspace
		for (Repository repo : RepositoryCache.INSTANCE.getAllRepositories()) {
			try {
				for (RemoteConfig remote : RemoteConfig
						.getAllRemoteConfigs(repo.getConfig())) {
					for (URIish uri : remote.getURIs()) {
						String uriStr = uri.toString().toLowerCase();
						// Check if URI contains the server URL and path
						// fragment
						if (uriStr.contains(
								serverUrl.toLowerCase().replaceAll("https?://", //$NON-NLS-1$
										"")) //$NON-NLS-1$
								&& uriStr.contains(pathFragment)) {
							return repo;
						}
					}
				}
			} catch (Exception e) {
				// Skip repos with config issues
			}
		}
		return null;
	}
}
