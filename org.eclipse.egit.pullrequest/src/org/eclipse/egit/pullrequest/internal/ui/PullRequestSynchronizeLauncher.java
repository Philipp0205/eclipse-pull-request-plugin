package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.resources.mapping.RemoteResourceMappingContext;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.core.synchronize.GitResourceVariantTreeSubscriber;
import org.eclipse.egit.core.synchronize.GitSubscriberMergeContext;
import org.eclipse.egit.core.synchronize.GitSubscriberResourceMappingContext;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;
import org.eclipse.egit.pullrequest.internal.util.PullRequestRefResolver;
import org.eclipse.egit.pullrequest.internal.util.PullRequestRefResolver.ResolvedRefs;
import org.eclipse.egit.pullrequest.internal.util.RepositoryResolver;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.internal.synchronize.GitModelSynchronizeParticipant;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.subscribers.SubscriberScopeManager;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipant;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Launches Eclipse's Synchronize view for pull request comparisons using
 * EGit's GitModelSynchronize infrastructure.
 * <p>
 * This class requires that the pull request's repository is cloned locally and
 * that the necessary Git refs are available (or can be fetched).
 */
@SuppressWarnings("restriction")
public class PullRequestSynchronizeLauncher {

	/**
	 * Launches the Synchronize view for a pull request, comparing the base
	 * branch with the head branch.
	 *
	 * @param pr
	 *            the pull request
	 */
	public static void launchForPullRequest(PullRequest pr) {
		// Set the active PR in context for comment overlay
		PullRequestContext context = PullRequestContext.getInstance();
		IPullRequestClient client = PullRequestClientFactory.createClient();
		
		if (client != null) {
			try {
				client.setActivePullRequest(pr);
				List<PullRequestComment> comments = client
						.getPullRequestComments(pr.getId());
				context.setActivePullRequest(pr, client);
				context.setComments(comments);
			} catch (IOException e) {
				Activator.logWarning(
						"Failed to fetch comments for PR, continuing without: " //$NON-NLS-1$
								+ e.getMessage());
				context.setActivePullRequest(pr);
			}
		} else {
			context.setActivePullRequest(pr);
		}

		Repository repo = RepositoryResolver.resolve(pr);
		if (repo == null) {
			showRepositoryNotFoundDialog(pr,
					() -> launchForPullRequest(pr));
			return;
		}

		try {
			ResolvedRefs refs = resolveRefsWithProgress(repo, pr);
			if (refs == null) {
				return; // Resolution failed, error already logged
			}

			launchSynchronize(repo, refs.getBaseCommit(),
					refs.getHeadCommit());
		} catch (Exception e) {
			Activator.logError("Failed to launch synchronize view for PR", //$NON-NLS-1$
					e);
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_ErrorMessage + ": " //$NON-NLS-1$
							+ e.getMessage());
		}
	}

	/**
	 * Launches the Synchronize view for a single commit, comparing it with its
	 * parent.
	 *
	 * @param pr
	 *            the pull request
	 * @param commit
	 *            the commit to compare
	 */
	public static void launchForCommit(PullRequest pr,
			PullRequestCommit commit) {
		// Set the active PR in context for comment overlay
		PullRequestContext context = PullRequestContext.getInstance();
		IPullRequestClient client = PullRequestClientFactory.createClient();
		
		if (client != null) {
			try {
				client.setActivePullRequest(pr);
				List<PullRequestComment> comments = client
						.getPullRequestComments(pr.getId());
				context.setActivePullRequest(pr, client);
				context.setComments(comments);
			} catch (IOException e) {
				Activator.logWarning(
						"Failed to fetch comments for PR, continuing without: " //$NON-NLS-1$
								+ e.getMessage());
				context.setActivePullRequest(pr);
			}
		} else {
			context.setActivePullRequest(pr);
		}

		Repository repo = RepositoryResolver.resolve(pr);
		if (repo == null) {
			showRepositoryNotFoundDialog(pr,
					() -> launchForCommit(pr, commit));
			return;
		}

		try {
			RevCommit headCommit = resolveCommit(repo, commit.getId());
			if (headCommit == null) {
				Activator.logError(
						"Commit not found: " + commit.getId(), null); //$NON-NLS-1$
				return;
			}

			// Compare with first parent (or empty tree if no parent)
			RevCommit baseCommit = headCommit.getParentCount() > 0
					? headCommit.getParent(0) : null;

			launchSynchronize(repo, baseCommit, headCommit);
		} catch (Exception e) {
			Activator.logError("Failed to launch synchronize view for commit", //$NON-NLS-1$
					e);
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_ErrorMessage + ": " //$NON-NLS-1$
							+ e.getMessage());
		}
	}

	/**
	 * Launches the Synchronize view for a commit range.
	 *
	 * @param pr
	 *            the pull request
	 * @param baseCommit
	 *            the base commit
	 * @param headCommit
	 *            the head commit
	 */
	public static void launchForCommitRange(PullRequest pr,
			PullRequestCommit baseCommit, PullRequestCommit headCommit) {
		// Set the active PR in context for comment overlay
		PullRequestContext context = PullRequestContext.getInstance();
		IPullRequestClient client = PullRequestClientFactory.createClient();
		
		if (client != null) {
			try {
				client.setActivePullRequest(pr);
				List<PullRequestComment> comments = client
						.getPullRequestComments(pr.getId());
				context.setActivePullRequest(pr, client);
				context.setComments(comments);
			} catch (IOException e) {
				Activator.logWarning(
						"Failed to fetch comments for PR, continuing without: " //$NON-NLS-1$
								+ e.getMessage());
				context.setActivePullRequest(pr);
			}
		} else {
			context.setActivePullRequest(pr);
		}

		Repository repo = RepositoryResolver.resolve(pr);
		if (repo == null) {
			showRepositoryNotFoundDialog(pr,
					() -> launchForCommitRange(pr, baseCommit, headCommit));
			return;
		}

		try {
			RevCommit base = resolveCommit(repo, baseCommit.getId());
			RevCommit head = resolveCommit(repo, headCommit.getId());

			if (base == null || head == null) {
				Activator.logError(
						"Commits not found: base=" + baseCommit.getId() //$NON-NLS-1$
								+ ", head=" + headCommit.getId(), //$NON-NLS-1$
						null);
				return;
			}

			launchSynchronize(repo, base, head);
		} catch (Exception e) {
			Activator.logError(
					"Failed to launch synchronize view for commit range", //$NON-NLS-1$
					e);
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_ErrorMessage + ": " //$NON-NLS-1$
							+ e.getMessage());
		}
	}

	/**
	 * Resolves refs with a progress dialog and optional fetching.
	 *
	 * @param repo
	 *            the repository
	 * @param pr
	 *            the pull request
	 * @return the resolved refs, or null if resolution failed
	 */
	private static ResolvedRefs resolveRefsWithProgress(Repository repo,
			PullRequest pr) {
		final ResolvedRefs[] result = new ResolvedRefs[1];

		try {
			new ProgressMonitorDialog(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell())
									.run(true, true,
											new IRunnableWithProgress() {
												@Override
												public void run(
														IProgressMonitor monitor)
														throws InvocationTargetException,
														InterruptedException {
													try {
														PullRequestRefResolver resolver = new PullRequestRefResolver(
																repo, pr);
														result[0] = resolver
																.resolve(true,
																		monitor);
													} catch (IOException e) {
														throw new InvocationTargetException(
																e);
													}
												}
											});
		} catch (InvocationTargetException e) {
			Activator.logError("Failed to resolve PR refs", e.getCause()); //$NON-NLS-1$
			MessageDialog.openError(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_ErrorTitle,
					PRText.PullRequestSynchronizeLauncher_FetchErrorMessage
							+ ": " //$NON-NLS-1$
							+ e.getCause().getMessage());
			return null;
		} catch (InterruptedException e) {
			// User cancelled
			return null;
		}

		return result[0];
	}

	/**
	 * Resolves a commit by its SHA.
	 *
	 * @param repo
	 *            the repository
	 * @param commitId
	 *            the commit SHA
	 * @return the commit, or null if not found
	 */
	private static RevCommit resolveCommit(Repository repo, String commitId) {
		try {
			return repo.parseCommit(repo.resolve(commitId));
		} catch (Exception e) {
			Activator.logError("Failed to resolve commit: " + commitId, e); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Launches EGit's Synchronize view for the given repository and commit
	 * range.
	 * <p>
	 * This method replicates the logic from
	 * {@code GitModelSynchronize.launch()} but adds a custom action
	 * contributor to override the default open action with one that opens
	 * our {@link PullRequestCompareEditorInput} with inline comment overlays.
	 * </p>
	 *
	 * @param repo
	 *            the repository
	 * @param baseCommit
	 *            the base commit (may be null for initial commits)
	 * @param headCommit
	 *            the head commit
	 * @throws IOException
	 *             if an error occurs
	 */
	private static void launchSynchronize(Repository repo,
			RevCommit baseCommit, RevCommit headCommit) throws IOException {
		// Get all projects mapped to this repository
		Set<IProject> projects = new LinkedHashSet<>();
		IProject[] workspaceProjects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();

		for (IProject project : workspaceProjects) {
			Repository projectRepo = ResourceUtil.getRepository(project);
			if (projectRepo != null
					&& projectRepo.getDirectory()
							.equals(repo.getDirectory())) {
				projects.add(project);
			}
		}

		if (projects.isEmpty()) {
			Activator.logWarning(
					"No workspace projects found for repository: " //$NON-NLS-1$
							+ repo.getDirectory());
			MessageDialog.openWarning(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getShell(),
					PRText.PullRequestSynchronizeLauncher_WarningTitle,
					PRText.PullRequestSynchronizeLauncher_NoProjectsMessage);
			return;
		}

		// Build IResource[] from projects
		IResource[] resources = projects.toArray(new IResource[0]);

		// Create GitSynchronizeData
		// Source = base (destination/target), Destination = head (source)
		String srcRev = baseCommit != null ? baseCommit.getName()
				: "^tree"; //$NON-NLS-1$
		String dstRev = headCommit.getName();

		GitSynchronizeData data = new GitSynchronizeData(repo, srcRev, dstRev,
				false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// Convert resources to resource mappings
		ResourceMapping[] mappings = getGitResourceMappings(resources);

		// Launch using our custom logic (replicated from GitModelSynchronize)
		launchWithCustomParticipant(dataSet, mappings);
	}

	/**
	 * Converts resources to resource mappings.
	 * <p>
	 * Based on {@code GitModelSynchronize.getGitResourceMappings()}.
	 * </p>
	 *
	 * @param elements
	 *            the resources
	 * @return the resource mappings
	 */
	private static ResourceMapping[] getGitResourceMappings(
			IResource[] elements) {
		List<ResourceMapping> gitMappings = new ArrayList<>();

		for (IResource element : elements) {
			ResourceMapping mapping = Adapters.adapt(element,
					ResourceMapping.class);
			if (mapping != null && isMappedToGitProvider(mapping)) {
				gitMappings.add(mapping);
			}
		}

		return gitMappings.toArray(new ResourceMapping[0]);
	}

	/**
	 * Checks if a resource mapping is mapped to a Git provider.
	 * <p>
	 * Based on {@code GitModelSynchronize.isMappedToGitProvider()}.
	 * </p>
	 *
	 * @param element
	 *            the resource mapping
	 * @return true if mapped to Git
	 */
	private static boolean isMappedToGitProvider(ResourceMapping element) {
		IProject[] projects = element.getProjects();
		for (IProject project : projects) {
			if (ResourceUtil.isSharedWithGit(project)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Launches the synchronize view with a custom participant that includes
	 * our action contributor.
	 * <p>
	 * This method replicates the logic from
	 * {@code GitModelSynchronize.fireSynchronizeAction()} but creates the
	 * participant ourselves so we can call
	 * {@code configuration.addActionContribution()} before it's displayed.
	 * </p>
	 *
	 * @param gsdSet
	 *            the synchronize data set
	 * @param mappings
	 *            the resource mappings
	 */
	private static void launchWithCustomParticipant(
			final GitSynchronizeDataSet gsdSet,
			final ResourceMapping[] mappings) {
		final GitResourceVariantTreeSubscriber subscriber = 
				new GitResourceVariantTreeSubscriber(gsdSet);

		IWorkbenchWindow window = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow();
		final IWorkbenchPart activePart = window != null
				? window.getActivePage().getActivePart()
				: null;

		Job syncJob = new WorkspaceJob(
				"Fetching Git data for pull request synchronization") { //$NON-NLS-1$

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				subscriber.init(monitor);
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object family) {
				if (JobFamilies.SYNCHRONIZE_READ_DATA.equals(family)) {
					return true;
				}
				return super.belongsTo(family);
			}
		};

		syncJob.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(IJobChangeEvent event) {
				RemoteResourceMappingContext remoteContext = 
						new GitSubscriberResourceMappingContext(subscriber,
								gsdSet);
				SubscriberScopeManager manager = new SubscriberScopeManager(
						subscriber.getName(), mappings, subscriber,
						remoteContext, true);
				GitSubscriberMergeContext context = 
						new GitSubscriberMergeContext(subscriber, manager,
								gsdSet);

				// Create the participant - this is where we can inject our
				// action contributor
				final GitModelSynchronizeParticipant participant = 
						new GitModelSynchronizeParticipant(context) {
					@Override
					protected void initializeConfiguration(
							org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration configuration) {
						super.initializeConfiguration(configuration);

						// Add our custom action contributor to override the
						// open action
						configuration.addActionContribution(
								new PullRequestActionContributor());
					}
				};

				TeamUI.getSynchronizeManager().addSynchronizeParticipants(
						new ISynchronizeParticipant[] { participant });

				participant.run(activePart);
			}
		});

		syncJob.setUser(true);
		syncJob.schedule();
	}

	/**
	 * Shows an error dialog explaining that the repository is not cloned
	 * locally.
	 */
	private static void showRepositoryNotFoundDialog(PullRequest pullRequest,
			Runnable completion) {
		Display.getDefault().asyncExec(() -> {
			org.eclipse.swt.widgets.Shell shell = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getShell();
			String description = RepositoryResolver
					.describeRepository(pullRequest);
			if (description == null || description.isEmpty()) {
				description = PRText.PullRequestSynchronizeLauncher_UnknownRepository;
			}
			MessageDialog dialog = new MessageDialog(shell,
					PRText.PullRequestSynchronizeLauncher_RepoNotFoundTitle,
					null,
					MessageFormat.format(
							PRText.PullRequestSynchronizeLauncher_RepoNotFoundMessage,
							description),
					MessageDialog.INFORMATION,
					new String[] { PRText.CloneProject_Button,
							PRText.CloneProject_Cancel },
					0);
			if (dialog.open() == 0) {
				new ClonePullRequestRepositoryJob(pullRequest, shell,
						completion).schedule();
			}
		});
	}
}
