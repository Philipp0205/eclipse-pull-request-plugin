package org.eclipse.egit.pullrequest.internal.ui;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IResourceProvider;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;

/**
 * Opens a compare editor with pull request comment overlay when files are
 * double-clicked in the Synchronize view.
 * <p>
 * This action is registered as the {@code P_OPEN_ACTION} for the pull request
 * synchronize participant, overriding Eclipse's default compare editor to
 * inject inline comment overlays.
 */
@SuppressWarnings("restriction")
public class PullRequestOpenInCompareAction extends Action {

	private final Action defaultOpenAction;

	private final ISynchronizePageConfiguration configuration;

	/**
	 * Creates a new open-in-compare action.
	 *
	 * @param configuration     the synchronize page configuration
	 * @param defaultOpenAction the default Eclipse open action (fallback)
	 */
	public PullRequestOpenInCompareAction(ISynchronizePageConfiguration configuration, Action defaultOpenAction) {
		this.configuration = configuration;
		this.defaultOpenAction = defaultOpenAction;
	}

	@Override
	public void run() {
		ISelection selection = configuration.getSite().getSelectionProvider().getSelection();
		if (!(selection instanceof IStructuredSelection)) {
			return;
		}

		IStructuredSelection sel = (IStructuredSelection) selection;
		if (sel.isEmpty()) {
			return;
		}

		// Get the first selected element
		Object element = sel.getFirstElement();

		// Try to resolve to a file. The Synchronize view uses
		// GitModelBlob elements (which implement IResourceProvider)
		// rather than plain IResource objects in the Git ChangeSet
		// model.
		IFile file = resolveFile(element);
		if (file == null) {
			// Not a file - fall back to default action
			if (defaultOpenAction != null) {
				defaultOpenAction.run();
			}
			return;
		}

		// Open our custom compare editor with comments
		openPullRequestCompareEditor(file);
	}

	private IFile resolveFile(Object element) {
		IResource resource = null;

		if (element instanceof IResource) {
			resource = (IResource) element;
		} else if (element instanceof IResourceProvider) {
			resource = ((IResourceProvider) element).getResource();
		} else {
			resource = Adapters.adapt(element, IResource.class);
		}

		if (resource instanceof IFile) {
			return (IFile) resource;
		}
		return null;
	}

	private void openPullRequestCompareEditor(IFile file) {
		PullRequestContext context = PullRequestContext.getInstance();
		PullRequest activePR = context.getActivePullRequest();

		if (activePR == null) {
			// No active PR - fall back to default
			if (defaultOpenAction != null) {
				defaultOpenAction.run();
			}
			return;
		}

		// Get repository-relative path
		String repoRelativePath = getRepositoryRelativePath(file);
		if (repoRelativePath == null) {
			if (defaultOpenAction != null) {
				defaultOpenAction.run();
			}
			return;
		}

		// Get the client from context
		IPullRequestClient client = context.getClient();
		if (client == null) {
			Activator.logWarning("No PR client available for compare editor"); //$NON-NLS-1$
			if (defaultOpenAction != null) {
				defaultOpenAction.run();
			}
			return;
		}

		// Create a PullRequestChangedFile model
		PullRequestChangedFile changedFile = createChangedFileFromResource(file, repoRelativePath);

		// Filter comments for this file
		List<PullRequestComment> allComments = context.getComments();
		List<PullRequestComment> fileComments = allComments.stream()
				.filter(comment -> repoRelativePath.equals(comment.getPath())).collect(Collectors.toList());

		// Open the compare editor on the UI thread
		Display.getDefault().asyncExec(() -> {
			PullRequestCompareEditorInput input = new PullRequestCompareEditorInput(client, activePR, changedFile);
			input.setComments(fileComments);
			CompareUI.openCompareEditor(input, true);
		});
	}

	private String getRepositoryRelativePath(IFile file) {
		RepositoryMapping mapping = RepositoryMapping.getMapping(file);
		if (mapping == null) {
			return null;
		}

		Repository repository = mapping.getRepository();
		if (repository == null) {
			return null;
		}

		IPath workTreePath = new org.eclipse.core.runtime.Path(repository.getWorkTree().getAbsolutePath());
		IPath filePath = file.getLocation();
		if (filePath == null) {
			return null;
		}

		if (workTreePath.isPrefixOf(filePath)) {
			IPath relativePath = filePath.makeRelativeTo(workTreePath);
			return relativePath.toPortableString();
		}

		return null;
	}

	private PullRequestChangedFile createChangedFileFromResource(IFile file, String repoRelativePath) {
		PullRequestChangedFile changedFile = new PullRequestChangedFile(repoRelativePath, file.getName(),
				PullRequestChangedFile.ChangeType.MODIFIED, null);

		// Set the repository
		RepositoryMapping mapping = RepositoryMapping.getMapping(file);
		if (mapping != null) {
			changedFile.setRepository(mapping.getRepository());
		}

		return changedFile;
	}
}
