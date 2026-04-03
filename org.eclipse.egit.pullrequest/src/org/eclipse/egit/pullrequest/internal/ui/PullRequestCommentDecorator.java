package org.eclipse.egit.pullrequest.internal.ui;

import org.eclipse.compare.IResourceProvider;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jgit.lib.Repository;

/**
 * Decorates files in the workspace with pull request comment counts.
 * <p>
 */
public class PullRequestCommentDecorator extends LabelProvider
		implements ILightweightLabelDecorator {

	@Override
	public void decorate(Object element, IDecoration decoration) {
		// Only decorate when a PR is active
		PullRequestContext context = PullRequestContext.getInstance();
		PullRequest activePR = context.getActivePullRequest();
		if (activePR == null) {
			return;
		}

		// Resolve the element to an IResource. The Synchronize view
		// uses GitModelBlob elements (which implement
		// IResourceProvider) rather than plain IResource objects.
		IResource resource = resolveResource(element);
		if (resource == null|| resource.getType() != IResource.FILE) {
			return;
		}

		// Get the repository-relative path for this file
		String repoRelativePath = getRepositoryRelativePath(
				resource);
		if (repoRelativePath == null) {
			return;
		}

		// Count comments for this file
		int commentCount = context.getCommentCountForFile(repoRelativePath);
		if (commentCount > 0) {
			String commentOrComments = commentCount == 1 ? "comment" : "comments"; //$NON-NLS-1$ //$NON-NLS-2$
			decoration.addSuffix(" [" + commentCount + " " + commentOrComments + "]"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private IResource resolveResource(Object element) {
		if (element instanceof IResource) {
			return (IResource) element;
		}
		if (element instanceof IResourceProvider) {
			return ((IResourceProvider) element).getResource();
		}
		return Adapters.adapt(element, IResource.class);
	}

	/**
	 * Gets the repository-relative path for a workspace resource.
	 *
	 * @param resource
	 *            the workspace resource
	 * @return the repository-relative path, or null if not in a
	 *         Git repo
	 */
	private String getRepositoryRelativePath(IResource resource) {
		IProject project = resource.getProject();
		if (project == null || !project.isAccessible()) {
			return null;
		}

		RepositoryMapping mapping = RepositoryMapping
				.getMapping(resource);
		if (mapping == null) {
			return null;
		}

		Repository repository = mapping.getRepository();
		if (repository == null) {
			return null;
		}

		IPath workTreePath = new org.eclipse.core.runtime.Path(
				repository.getWorkTree().getAbsolutePath());
		IPath resourcePath = resource.getLocation();
		if (resourcePath == null) {
			return null;
		}

		// Make the path relative to the repository work tree
		if (workTreePath.isPrefixOf(resourcePath)) {
			IPath relativePath = resourcePath
					.makeRelativeTo(workTreePath);
			return relativePath.toPortableString();
		}

		return null;
	}
}
