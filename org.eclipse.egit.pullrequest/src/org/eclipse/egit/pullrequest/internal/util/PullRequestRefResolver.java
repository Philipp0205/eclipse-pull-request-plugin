package org.eclipse.egit.pullrequest.internal.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.egit.core.op.FetchOperation;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

/**
 * Resolves Git references (base and head) for a pull request from a local
 * repository.
 * <p>
 * This class provides the capability to resolve PR branch refs into JGit
 * {@link RevCommit} objects, with optional fetching of missing PR refs from
 * the remote repository.
// */
public class PullRequestRefResolver {

	private final Repository repository;

	private final PullRequest pullRequest;

	private String remoteName;

	/**
	 * Creates a new ref resolver for the given repository and pull request.
	 *
	 * @param repository
	 *            the local Git repository
	 * @param pullRequest
	 *            the pull request
	 */
	public PullRequestRefResolver(Repository repository,
			PullRequest pullRequest) {
		this.repository = repository;
		this.pullRequest = pullRequest;
	}

	/**
	 * Resolves the base and head refs for the pull request into commit
	 * objects.
	 * <p>
	 * If the refs are not found locally and {@code fetchIfMissing} is true,
	 * attempts to fetch them from the remote.
	 *
	 * @param fetchIfMissing
	 *            whether to fetch missing refs from remote
	 * @param monitor
	 *            progress monitor, may be null
	 * @return the resolved refs, or null if resolution failed
	 * @throws IOException
	 *             if ref resolution or fetch fails
	 */
	public ResolvedRefs resolve(boolean fetchIfMissing,
			IProgressMonitor monitor) throws IOException {
		SubMonitor progress = SubMonitor.convert(monitor, 100);

		String baseRefName = getBaseRefName();
		String headRefName = getHeadRefName();

		ObjectId baseId = repository.resolve(baseRefName);
		ObjectId headId = repository.resolve(headRefName);

		// If refs are missing and fetch is requested, try fetching
		if ((baseId == null || headId == null) && fetchIfMissing) {
			progress.subTask("Fetching pull request refs..."); //$NON-NLS-1$
			fetchPullRequestRefs(progress.split(50));
			baseId = repository.resolve(baseRefName);
			headId = repository.resolve(headRefName);
		}

		if (baseId == null || headId == null) {
			Activator.logError("Failed to resolve refs: base=" //$NON-NLS-1$
					+ baseRefName + ", head=" + headRefName, null); //$NON-NLS-1$
			return null;
		}

		try (RevWalk walk = new RevWalk(repository)) {
			RevCommit baseCommit = walk.parseCommit(baseId);
			RevCommit headCommit = walk.parseCommit(headId);
			progress.done();
			return new ResolvedRefs(baseCommit, headCommit, baseRefName,
					headRefName);
		}
	}

	/**
	 * Gets the base ref name (target branch) for the pull request.
	 *
	 * @return the base ref name in the form
	 *         {@code refs/remotes/<remote>/<branch>}
	 */
	private String getBaseRefName() {
		String targetBranch = pullRequest.getToRef().getDisplayId();
		return Constants.R_REMOTES + getRemoteName() + "/" //$NON-NLS-1$
				+ targetBranch;
	}

	/**
	 * Determines the remote that points at the pull request's target
	 * repository.
	 * <p>
	 * Clones do not necessarily name that remote {@code origin}, for instance
	 * when the repository was cloned from a mirror or added as a second
	 * remote, so the remote URLs are matched against the pull request's
	 * repository.
	 *
	 * @return the remote name, {@code origin} if no remote matches
	 */
	private String getRemoteName() {
		if (remoteName == null) {
			String matched = RepositoryResolver.findRemoteName(repository,
					pullRequest.getToRef() != null
							? pullRequest.getToRef().getRepository()
							: null);
			remoteName = matched != null ? matched
					: Constants.DEFAULT_REMOTE_NAME;
		}
		return remoteName;
	}

	/**
	 * Gets the head ref name (source branch) for the pull request.
	 * <p>
	 * For GitHub PRs, uses {@code refs/pull/<id>/head}. For Bitbucket PRs,
	 * uses {@code refs/pull-requests/<id>/from}.
	 *
	 * @return the head ref name
	 */
	private String getHeadRefName() {
		String providerType = Activator.getDefault().getPreferenceStore()
				.getString(PRPreferences.PULLREQUEST_PROVIDER_TYPE);

		long prId = pullRequest.getId();

		if ("GITHUB".equals(providerType)) { //$NON-NLS-1$
			return "refs/pull/" + prId + "/head"; //$NON-NLS-1$ //$NON-NLS-2$
		} else if ("BITBUCKET".equals(providerType)) { //$NON-NLS-1$
			return "refs/pull-requests/" + prId + "/from"; //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			// Fallback: try source branch ref
			String sourceBranch = pullRequest.getFromRef().getDisplayId();
			return Constants.R_REMOTES + getRemoteName() + "/" //$NON-NLS-1$
					+ sourceBranch;
		}
	}

	/**
	 * Fetches the pull request refs from the remote repository.
	 *
	 * @param monitor
	 *            progress monitor
	 * @throws IOException
	 *             if fetch fails
	 */
	private void fetchPullRequestRefs(IProgressMonitor monitor)
			throws IOException {
		try {
			String providerType = Activator.getDefault().getPreferenceStore()
					.getString(PRPreferences.PULLREQUEST_PROVIDER_TYPE);

			long prId = pullRequest.getId();

			List<RefSpec> refSpecs = new ArrayList<>();
			if ("GITHUB".equals(providerType)) { //$NON-NLS-1$
				// GitHub: fetch +refs/pull/<id>/head:refs/pull/<id>/head
				refSpecs.add(new RefSpec(
						"+refs/pull/" + prId + "/head:refs/pull/" + prId //$NON-NLS-1$ //$NON-NLS-2$
								+ "/head")); //$NON-NLS-1$
			} else if ("BITBUCKET".equals(providerType)) { //$NON-NLS-1$
				// Bitbucket: fetch
				// +refs/pull-requests/<id>/from:refs/pull-requests/<id>/from
				refSpecs.add(new RefSpec(
						"+refs/pull-requests/" + prId //$NON-NLS-1$
								+ "/from:refs/pull-requests/" + prId //$NON-NLS-1$
								+ "/from")); //$NON-NLS-1$
			} else {
				return;
			}

			String targetBranch = pullRequest.getToRef().getDisplayId();
			if (targetBranch != null && !targetBranch.isEmpty()) {
				// The target branch may never have been fetched into this
				// clone, so update its remote-tracking ref as well.
				refSpecs.add(new RefSpec("+" + Constants.R_HEADS //$NON-NLS-1$
						+ targetBranch + ":" + Constants.R_REMOTES //$NON-NLS-1$
						+ getRemoteName() + "/" + targetBranch)); //$NON-NLS-1$
			}

			RemoteConfig remoteConfig = null;
			for (RemoteConfig remote : RemoteConfig
					.getAllRemoteConfigs(repository.getConfig())) {
				if (getRemoteName().equals(remote.getName())) {
					remoteConfig = remote;
					break;
				}
			}

			if (remoteConfig == null || remoteConfig.getURIs().isEmpty()) {
				Activator.logError("No remote '" + getRemoteName() //$NON-NLS-1$
						+ "' found in repository " //$NON-NLS-1$
						+ repository.getDirectory(), null);
				return;
			}

		URIish uri = remoteConfig.getURIs().get(0);
		FetchOperation fetchOp = new FetchOperation(repository, uri,
				refSpecs, 30, false);
		fetchOp.run(monitor);
		} catch (Exception e) {
			throw new IOException("Failed to fetch PR refs", e); //$NON-NLS-1$
		}
	}

	/**
	 * Result of resolving pull request refs.
	 */
	public static class ResolvedRefs {

		private final RevCommit baseCommit;

		private final RevCommit headCommit;

		private final String baseRefName;

		private final String headRefName;

		/**
		 * Creates a new resolved refs result.
		 *
		 * @param baseCommit
		 *            the base (target) commit
		 * @param headCommit
		 *            the head (source) commit
		 * @param baseRefName
		 *            the base ref name
		 * @param headRefName
		 *            the head ref name
		 */
		public ResolvedRefs(RevCommit baseCommit, RevCommit headCommit,
				String baseRefName, String headRefName) {
			this.baseCommit = baseCommit;
			this.headCommit = headCommit;
			this.baseRefName = baseRefName;
			this.headRefName = headRefName;
		}

		/**
		 * @return the base (target) commit
		 */
		public RevCommit getBaseCommit() {
			return baseCommit;
		}

		/**
		 * @return the head (source) commit
		 */
		public RevCommit getHeadCommit() {
			return headCommit;
		}

		/**
		 * @return the base ref name
		 */
		public String getBaseRefName() {
			return baseRefName;
		}

		/**
		 * @return the head ref name
		 */
		public String getHeadRefName() {
			return headRefName;
		}
	}
}
