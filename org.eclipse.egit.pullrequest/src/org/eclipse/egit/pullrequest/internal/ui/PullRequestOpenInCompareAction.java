package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;

import org.eclipse.compare.IResourceProvider;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.model.DiffHunkParser;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelBlob;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;

/**
 * Adds pull request comment overlays after the stock Synchronize open action.
 * <p>
 * EGit remains responsible for creating and opening its compare input. This
 * action only resolves the selected path and binds comments to the resulting
 * text merge viewer.
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
	public PullRequestOpenInCompareAction(
			ISynchronizePageConfiguration configuration,
			Action defaultOpenAction) {
		this.configuration = configuration;
		this.defaultOpenAction = defaultOpenAction;
	}

	@Override
	public void run() {
		ISelection selection = configuration.getSite()
				.getSelectionProvider().getSelection();
		if (!(selection instanceof IStructuredSelection)) {
			return;
		}

		IStructuredSelection sel = (IStructuredSelection) selection;
		if (sel.isEmpty()) {
			return;
		}

		if (sel.size() != 1) {
			runDefaultAction();
			return;
		}

		Object element = sel.getFirstElement();
		PullRequestContext context = PullRequestContext.getInstance();
		PullRequest activePR = context.getActivePullRequest();
		String filePath = resolveRepositoryRelativePath(element, context);
		if (activePR == null || filePath == null) {
			runDefaultAction();
			return;
		}

		runDefaultAction();
		scheduleOverlay(filePath, context);
	}

	private String resolveRepositoryRelativePath(Object element,
			PullRequestContext context) {
		Repository repository = context.getRepository();
		if (element instanceof GitModelBlob && repository != null) {
			GitModelBlob blob = (GitModelBlob) element;
			if (blob.getLocation() != null) {
				return Repository.stripWorkDir(repository.getWorkTree(),
						blob.getLocation().toFile());
			}
		}

		IResource resource = null;

		if (element instanceof IResource) {
			resource = (IResource) element;
		} else if (element instanceof IResourceProvider) {
			resource = ((IResourceProvider) element).getResource();
		} else {
			resource = Adapters.adapt(element, IResource.class);
		}

		if (resource != null) {
			RepositoryMapping mapping = RepositoryMapping
					.getMapping(resource);
			if (mapping != null) {
				return mapping.getRepoRelativePath(resource);
			}
		}
		return null;
	}

	private void scheduleOverlay(String filePath,
			PullRequestContext context) {
		Job job = new Job("Calculating pull request diff lines") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				DiffHunkParser.DiffLines diffLines =
						DiffHunkParser.parse(null);
				try {
					Repository repository = context.getRepository();
					String base = context.getBaseRevision();
					String head = context.getHeadRevision();
					if (repository != null && base != null && head != null) {
						diffLines = LocalDiffLineCalculator.calculate(
								repository, base, head, filePath);
					}
				} catch (IOException e) {
					Activator.logWarning(
							"Failed to calculate local comment lines: " //$NON-NLS-1$
									+ e.getMessage());
				}
				CompareCommentOverlayBinder.bindAfterOpen(filePath,
						diffLines);
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
	}

	private void runDefaultAction() {
		if (defaultOpenAction != null) {
			defaultOpenAction.run();
		}
	}
}
