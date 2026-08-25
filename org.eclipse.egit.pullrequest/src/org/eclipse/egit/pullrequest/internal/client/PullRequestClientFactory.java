package org.eclipse.egit.pullrequest.internal.client;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.bitbucket.BitbucketClient;
import org.eclipse.egit.pullrequest.internal.github.GitHubClient;

/**
 * Factory for creating pull request client instances based on configured
 * provider type
 */
public class PullRequestClientFactory {

	/**
	 * Configuration holder for pull request clients
	 */
	public static class ClientConfig {
		/**
		 * Provider type
		 */
		public PullRequestProviderType providerType;

		/**
		 * Bitbucket server URL
		 */
		public String bitbucketServerUrl;

		/**
		 * Bitbucket project key
		 */
		public String bitbucketProjectKey;

		/**
		 * Bitbucket repository slug
		 */
		public String bitbucketRepoSlug;

		/**
		 * Bitbucket access token
		 */
		public String bitbucketAccessToken;

		/**
		 * GitHub owner (user or organization)
		 */
		public String githubOwner;

		/**
		 * GitHub repository name
		 */
		public String githubRepo;

		/**
		 * GitHub access token
		 */
		public String githubAccessToken;
	}

	/**
	 * Creates a pull request client based on current preferences
	 *
	 * @return the configured client, or null if not properly configured
	 */
	public static IPullRequestClient createClient() {
		ClientConfig config = loadConfig();
		if (config == null || config.providerType == null) {
			Activator.logWarning(
					"No pull request provider is configured."); //$NON-NLS-1$
			return null;
		}

		IPullRequestClient client = createClient(config);
		if (client == null) {
			Activator.logWarning(
					"The configuration for provider " + config.providerType //$NON-NLS-1$
							+ " is incomplete: " + describe(config)); //$NON-NLS-1$
		}
		return client;
	}

	/**
	 * Describes which configuration values are present, without revealing the
	 * access tokens.
	 *
	 * @param config
	 *            the configuration to describe
	 * @return the description
	 */
	private static String describe(ClientConfig config) {
		if (config.providerType == PullRequestProviderType.GITHUB) {
			return "owner=" + quote(config.githubOwner) + ", repository=" //$NON-NLS-1$ //$NON-NLS-2$
					+ quote(config.githubRepo) + ", token=" //$NON-NLS-1$
					+ (isBlank(config.githubAccessToken) ? "missing" : "set"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "server URL=" + quote(config.bitbucketServerUrl) //$NON-NLS-1$
				+ ", project key=" + quote(config.bitbucketProjectKey) //$NON-NLS-1$
				+ ", repository slug=" + quote(config.bitbucketRepoSlug) //$NON-NLS-1$
				+ ", token=" //$NON-NLS-1$
				+ (isBlank(config.bitbucketAccessToken) ? "missing" : "set"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String quote(String value) {
		return value == null ? "<null>" : '\'' + value + '\''; //$NON-NLS-1$
	}

	/**
	 * Creates a pull request client for the specified configuration
	 *
	 * @param config
	 *            the client configuration
	 * @return the configured client, or null if configuration is invalid
	 */
	public static IPullRequestClient createClient(ClientConfig config) {
		if (config == null || config.providerType == null) {
			return null;
		}

		switch (config.providerType) {
		case BITBUCKET:
			if (isBlank(config.bitbucketServerUrl)
					|| isBlank(config.bitbucketAccessToken)
					|| isBlank(config.bitbucketProjectKey)
					|| isBlank(config.bitbucketRepoSlug)) {
				return null;
			}
			return new BitbucketClient(config.bitbucketServerUrl,
					config.bitbucketProjectKey, config.bitbucketRepoSlug,
					config.bitbucketAccessToken);

		case GITHUB:
			if (isBlank(config.githubOwner) || isBlank(config.githubRepo)
					|| isBlank(config.githubAccessToken)) {
				return null;
			}
			return new GitHubClient(config.githubOwner, config.githubRepo,
					config.githubAccessToken);

		default:
			return null;
		}
	}

	/**
	 * Loads the client configuration from preferences
	 *
	 * @return the configuration, or null if not configured
	 */
	public static ClientConfig loadConfig() {
		// Read from pullrequest plugin preferences
		IEclipsePreferences prefs = InstanceScope.INSTANCE
				.getNode("org.eclipse.egit.pullrequest"); //$NON-NLS-1$

		ClientConfig config = new ClientConfig();

		String providerTypeStr = prefs.get(PRPreferences.PULLREQUEST_PROVIDER_TYPE, "BITBUCKET"); //$NON-NLS-1$
		try {
			config.providerType = PullRequestProviderType
					.valueOf(providerTypeStr);
		} catch (IllegalArgumentException e) {
			config.providerType = PullRequestProviderType.BITBUCKET;
		}

		config.bitbucketServerUrl = prefs.get(PRPreferences.BITBUCKET_SERVER_URL, ""); //$NON-NLS-1$
		config.bitbucketProjectKey = prefs.get(PRPreferences.BITBUCKET_PROJECT_KEY,
				""); //$NON-NLS-1$
		config.bitbucketRepoSlug = prefs.get(PRPreferences.BITBUCKET_REPO_SLUG, ""); //$NON-NLS-1$
		config.bitbucketAccessToken = prefs.get(PRPreferences.BITBUCKET_ACCESS_TOKEN,
				""); //$NON-NLS-1$

		config.githubOwner = prefs.get(PRPreferences.GITHUB_OWNER, ""); //$NON-NLS-1$
		config.githubRepo = prefs.get(PRPreferences.GITHUB_REPO, ""); //$NON-NLS-1$
		config.githubAccessToken = prefs.get(PRPreferences.GITHUB_ACCESS_TOKEN, ""); //$NON-NLS-1$

		return config;
	}

	private static boolean isBlank(String str) {
		return str == null || str.trim().isEmpty();
	}
}
