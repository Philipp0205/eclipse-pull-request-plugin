/*******************************************************************************
 * Copyright (C) 2026, Eclipse EGit contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.compare.CompareUI;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.pullrequest.internal.model.ChangedFile;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.ActionUtils;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.ui.internal.commit.DiffViewer;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;

/**
 * View for displaying changed files in a pull request
 */
public class PullRequestChangedFilesView extends ViewPart {

	/**
	 * View ID
	 */
	public static final String VIEW_ID = "org.eclipse.egit.pullrequest.PullRequestChangedFilesView"; //$NON-NLS-1$

	private TreeViewer changedFilesViewer;

	private PullRequest selectedPullRequest;

	private List<PullRequestChangedFile> changedFiles = new ArrayList<>();

	private List<PullRequestComment> allComments = new ArrayList<>();

	private Repository gitRepository;

	private ISelectionListener prSelectionListener;

	@Override
	public void createPartControl(Composite parent) {
		GridLayoutFactory.fillDefaults().applyTo(parent);

		setContentDescription("Changed Files"); //$NON-NLS-1$

		TreeColumnLayout treeColumnLayout = new TreeColumnLayout();
		Composite layoutComposite = new Composite(parent, SWT.NONE);
		layoutComposite.setLayout(treeColumnLayout);
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(layoutComposite);

		changedFilesViewer = new TreeViewer(layoutComposite,
				SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		changedFilesViewer.getTree().setHeaderVisible(true);
		changedFilesViewer.getTree().setLinesVisible(true);

		setupColumns(treeColumnLayout);

		changedFilesViewer
				.setContentProvider(new PullRequestChangesContentProvider());
		changedFilesViewer.setInput(changedFiles);

		// Register as selection provider for view communication
		getSite().setSelectionProvider(changedFilesViewer);

		// Open compare editor on double-click
		changedFilesViewer
				.addDoubleClickListener(event -> {
					IStructuredSelection selection = (IStructuredSelection) event
							.getSelection();
					if (!selection.isEmpty()) {
						Object element = selection.getFirstElement();
						if (element instanceof PullRequestChangedFile) {
							openCompareEditor(
									(PullRequestChangedFile) element);
						}
					}
				});

		// Open compare editor on Enter key
		changedFilesViewer.getTree().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
					IStructuredSelection selection = (IStructuredSelection) changedFilesViewer
							.getSelection();
					if (!selection.isEmpty()) {
						Object element = selection.getFirstElement();
						if (element instanceof PullRequestChangedFile) {
							openCompareEditor(
									(PullRequestChangedFile) element);
						}
					}
				}
			}
		});

		// Create context menu for changed files
		createChangedFilesPopupMenu(changedFilesViewer);

		// NOTE: Selection listener removed to prevent loading PRs on single click.
		// PRs are now loaded explicitly via double-click or Enter key in
		// PullRequestListView, which calls loadPullRequest() directly.
		// Listen for PR selection from PullRequestListView
		// prSelectionListener = new ISelectionListener() {
		// 	@Override
		// 	public void selectionChanged(IWorkbenchPart part,
		// 			ISelection selection) {
		// 		if (selection instanceof IStructuredSelection) {
		// 			Object first = ((IStructuredSelection) selection)
		// 					.getFirstElement();
		// 			if (first instanceof PullRequest) {
		// 				onPRSelected((PullRequest) first);
		// 			}
		// 		}
		// 	}
		// };
		// getSite().getWorkbenchWindow().getSelectionService()
		// 		.addSelectionListener(PullRequestListView.VIEW_ID,
		// 				prSelectionListener);
		createMarkAllUnreadAction();
	}

	/**
	 * Creates the "Mark All as Unread" action and adds it to the
	 * view toolbar.
	 */
	private void createMarkAllUnreadAction() {
		Action markAllUnreadAction = new Action(
				PRText.ChangedFilesView_MarkAllUnread) {
			@Override
			public void run() {
				for (PullRequestChangedFile file : changedFiles) {
					file.setRead(false);
				}
				changedFilesViewer.refresh();
			}
		};
		markAllUnreadAction.setToolTipText(PRText.ChangedFilesView_MarkAllUnread);
		getViewSite().getActionBars().getToolBarManager()
				.add(markAllUnreadAction);
	}

	private void setupColumns(TreeColumnLayout layout) {
		// File Column
		TreeViewerColumn fileColumn = createColumn(layout, "File", 60, //$NON-NLS-1$
				SWT.LEFT);
		fileColumn.setLabelProvider(new PullRequestChangesLabelProvider());

	// Change Type Column
	TreeViewerColumn changeColumn = createColumn(layout, "Change", 10, //$NON-NLS-1$
			SWT.LEFT);
	changeColumn.setLabelProvider(new ColumnLabelProvider() {
		@Override
		public String getText(Object element) {
			if (element instanceof PullRequestChangedFile) {
				return ((PullRequestChangedFile) element).getChangeType()
						.toString();
			}
			return ""; //$NON-NLS-1$
		}

		@Override
		public org.eclipse.swt.graphics.Color getBackground(Object element) {
			return null; // Use theme colors
		}

		@Override
		public org.eclipse.swt.graphics.Color getForeground(Object element) {
			return null; // Use theme colors
		}
	});

	// Comments Column
	TreeViewerColumn commentsColumn = createColumn(layout, "Comments", 10, //$NON-NLS-1$
			SWT.CENTER);
	commentsColumn.setLabelProvider(new ColumnLabelProvider() {
		@Override
		public String getText(Object element) {
			if (element instanceof PullRequestChangedFile) {
				PullRequestChangedFile file = (PullRequestChangedFile) element;
				int count = getCommentCountForFile(file.getPath(),
						file.getSrcPath());
				return count > 0 ? String.valueOf(count) : ""; //$NON-NLS-1$
			} else if (element instanceof PullRequestFolderEntry) {
				PullRequestFolderEntry folder = (PullRequestFolderEntry) element;
				int count = getCommentCountForFolder(folder);
				return count > 0 ? String.valueOf(count) : ""; //$NON-NLS-1$
			}
			return ""; //$NON-NLS-1$
		}

		@Override
		public org.eclipse.swt.graphics.Color getBackground(Object element) {
			return null; // Use theme colors
		}

		@Override
		public org.eclipse.swt.graphics.Color getForeground(Object element) {
			return null; // Use theme colors
		}
	});

	// Path Column
	TreeViewerColumn pathColumn = createColumn(layout, "Path", 30, //$NON-NLS-1$
			SWT.LEFT);
	pathColumn.setLabelProvider(new ColumnLabelProvider() {
		@Override
		public String getText(Object element) {
			if (element instanceof PullRequestChangedFile) {
				return ((PullRequestChangedFile) element).getPath();
			} else if (element instanceof PullRequestFolderEntry) {
				return ((PullRequestFolderEntry) element).getPath()
						.toString();
			}
			return ""; //$NON-NLS-1$
		}

		@Override
		public org.eclipse.swt.graphics.Color getBackground(Object element) {
			return null; // Use theme colors
		}

		@Override
		public org.eclipse.swt.graphics.Color getForeground(Object element) {
			return null; // Use theme colors
		}
	});
}

	private TreeViewerColumn createColumn(TreeColumnLayout layout, String text,
			int weight, int style) {
		TreeViewerColumn column = new TreeViewerColumn(changedFilesViewer,
				style);
		column.getColumn().setText(text);
		layout.setColumnData(column.getColumn(), new ColumnWeightData(weight));
		return column;
	}

	/**
	 * Attempts to resolve the local Git repository that matches the
	 * pull request.
	 *
	 * @param pr
	 *            the pull request
	 * @return the matching Git repository, or null if not found
	 */
	private Repository resolveGitRepository(PullRequest pr) {
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
			String owner = Activator.getDefault().getPreferenceStore()
					.getString(PRPreferences.GITHUB_OWNER);
			String repo = Activator.getDefault().getPreferenceStore()
					.getString(PRPreferences.GITHUB_REPO);
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
						// Check if URI contains the server URL and path fragment
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

	private void onPRSelected(PullRequest pr) {
		loadPullRequest(pr);
	}

	/**
	 * Loads a pull request by fetching its changed files and comments. This
	 * method is public to allow explicit loading from other views (e.g., when
	 * double-clicking or pressing Enter in PullRequestListView).
	 *
	 * @param pr
	 *            the pull request to load
	 */
	public void loadPullRequest(PullRequest pr) {
		selectedPullRequest = pr;

		// Resolve the Git repository for this PR
		final Repository resolvedRepo = resolveGitRepository(pr);

		// Fetch changed files in a background job
		Job job = new Job("Fetching changed files") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("Fetching changed files", //$NON-NLS-1$
						IProgressMonitor.UNKNOWN);

				try {
					IPullRequestClient client = PullRequestClientFactory.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
								"Pull request provider not configured"); //$NON-NLS-1$
					}

					// Fetch changed files
					List<ChangedFile> apiChangedFiles = client.getPullRequestChanges(pr.getId());

					final List<PullRequestChangedFile> uiChangedFiles = apiChangedFiles
							.stream()
							.map(PullRequestChangedFile::fromChangedFile)
							.collect(Collectors.toList());

					// Fetch pull request activities (including comments)
					final List<PullRequestComment> comments = new ArrayList<>();
					try {
						comments.addAll(client.getPullRequestComments(pr.getId()));
					} catch (Exception e) {
						Activator.logError("Failed to fetch PR comments", e); //$NON-NLS-1$
					}

					Display.getDefault().asyncExec(() -> {
						if (!changedFilesViewer.getControl().isDisposed()) {
							changedFiles.clear();
							changedFiles.addAll(uiChangedFiles);
							allComments.clear();
							allComments.addAll(comments);

							// Set the resolved repository on the view and all files
							gitRepository = resolvedRepo;
							if (gitRepository != null) {
								for (PullRequestChangedFile file : changedFiles) {
									file.setRepository(gitRepository);
								}
							}

							changedFilesViewer.setInput(changedFiles);
							changedFilesViewer.refresh();
							updateFormTitle();

							// Notify the comments view that comments are available
							IWorkbenchPart commentsPart = getSite()
									.getWorkbenchWindow().getActivePage()
									.findView(PullRequestCommentsView.VIEW_ID);
							if (commentsPart instanceof PullRequestCommentsView) {
								((PullRequestCommentsView) commentsPart)
										.onCommentsLoaded(allComments);
							}
						}
					});

					return Status.OK_STATUS;
				} catch (IOException e) {
					Display.getDefault().asyncExec(() -> {
						if (!changedFilesViewer.getControl().isDisposed()) {
							MessageDialog.openError(
									changedFilesViewer.getControl().getShell(),
									"Error", //$NON-NLS-1$
									"Failed to fetch changed files: " //$NON-NLS-1$
											+ e.getMessage());
						}
					});
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							"Failed to fetch changed files", e); //$NON-NLS-1$
				} finally {
					monitor.done();
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private void openCompareEditor(PullRequestChangedFile file) {
		openCompareEditor(file, () -> {
			// Mark file as read after editor opens
			file.setRead(true);
			Display.getDefault().asyncExec(() -> {
				if (!changedFilesViewer.getControl().isDisposed()) {
					changedFilesViewer.refresh(file);
				}
			});
		});
	}

	/**
	 * Opens a compare editor for the given file with an optional callback to
	 * run after the editor is opened.
	 *
	 * @param file
	 *            the changed file to open
	 * @param afterOpen
	 *            optional callback to run after the editor opens (may be null)
	 */
	public void openCompareEditor(PullRequestChangedFile file,
			Runnable afterOpen) {
		if (selectedPullRequest == null) {
			return;
		}

		// Open compare editor in a background job
		Job job = new Job("Opening file comparison") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					monitor.beginTask("Preparing comparison", //$NON-NLS-1$
							IProgressMonitor.UNKNOWN);

					IPullRequestClient client = PullRequestClientFactory
							.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
								"Pull request provider not configured"); //$NON-NLS-1$
					}

					// Create compare editor input
					final PullRequestCompareEditorInput input = new PullRequestCompareEditorInput(
							client, selectedPullRequest, file);

					// Filter comments for this specific file
					List<PullRequestComment> fileComments = allComments.stream()
							.filter(comment -> {
								if (comment.getPath() == null) {
									return false;
								}
								String commentPath = comment.getPath();
								String filePath = file.getPath();
								String srcPath = file.getSrcPath();
								return commentPath.equals(filePath) || (srcPath != null
										&& commentPath.equals(srcPath));
							}).collect(Collectors.toList());

					// Set comments on the compare input
					input.setComments(fileComments);

					// Open compare editor in UI thread
					Display.getDefault().asyncExec(() -> {
						try {
							CompareUI.openCompareEditor(input, true);
						} catch (Exception e) {
							Activator.logError(
									"Failed to open compare editor", //$NON-NLS-1$
									e);
						}
						// Execute callback after editor opens
						if (afterOpen != null) {
							afterOpen.run();
						}
					});

					return Status.OK_STATUS;
				} catch (Exception e) {
					Activator.logError(
							"Error opening compare editor", e); //$NON-NLS-1$
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							"Failed to open comparison", e); //$NON-NLS-1$
				} finally {
					monitor.done();
				}
			}
		};
		job.setUser(false);
		job.schedule();
	}

	private void updateFormTitle() {
		if (selectedPullRequest != null) {
			setContentDescription(MessageFormat.format(
					"PR #{0}: {1}", //$NON-NLS-1$
					Long.valueOf(selectedPullRequest.getId()),
					selectedPullRequest.getTitle()));
		} else {
			setContentDescription(""); //$NON-NLS-1$
		}
	}

	private int getCommentCountForFile(String filePath, String srcPath) {
		if (allComments == null || allComments.isEmpty()) {
			return 0;
		}

		return (int) allComments.stream().filter(comment -> {
			if (comment.getPath() == null) {
				return false;
			}
			String commentPath = comment.getPath();
			return commentPath.equals(filePath)
					|| (srcPath != null && commentPath.equals(srcPath));
		}).mapToLong(comment -> {
			long count = 1;
			if (comment.getReplies() != null) {
				count += comment.getReplies().size();
			}
			return count;
		}).sum();
	}

	private int getCommentCountForFolder(PullRequestFolderEntry folder) {
		if (allComments == null || allComments.isEmpty()) {
			return 0;
		}

		String folderPath = folder.getPath().toString();

		return (int) allComments.stream().filter(comment -> {
			if (comment.getPath() == null) {
				return false;
			}
			return comment.getPath().startsWith(folderPath);
		}).mapToLong(comment -> {
			long count = 1;
			if (comment.getReplies() != null) {
				count += comment.getReplies().size();
			}
			return count;
		}).sum();
	}

	/**
	 * Get all comments for the currently selected PR
	 *
	 * @return list of comments
	 */
	public List<PullRequestComment> getAllComments() {
		return allComments;
	}

	/**
	 * Get the currently selected pull request
	 *
	 * @return the selected pull request, or null
	 */
	public PullRequest getSelectedPullRequest() {
		return selectedPullRequest;
	}

	/**
	 * Get the list of changed files for the current pull request
	 *
	 * @return list of changed files
	 */
	public List<PullRequestChangedFile> getChangedFiles() {
		return changedFiles;
	}

	@Override
	public void setFocus() {
		changedFilesViewer.getControl().setFocus();
	}

	@Override
	public void dispose() {
		// NOTE: prSelectionListener is no longer used (see createPartControl)
		// if (prSelectionListener != null) {
		// 	getSite().getWorkbenchWindow().getSelectionService()
		// 			.removeSelectionListener(PullRequestListView.VIEW_ID,
		// 					prSelectionListener);
		// }
		super.dispose();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter == IShowInSource.class) {
			return (T) (IShowInSource) () -> getShowInContext();
		}
		return super.getAdapter(adapter);
	}

	/**
	 * Creates a ShowInContext for the current selection.
	 *
	 * @return the ShowInContext, or null if no valid selection
	 */
	private ShowInContext getShowInContext() {
		if (changedFilesViewer == null
				|| changedFilesViewer.getControl().isDisposed()) {
			return null;
		}

		IStructuredSelection selection = changedFilesViewer
				.getStructuredSelection();
		if (selection.isEmpty()) {
			return null;
		}

		// Collect workspace resources from selection
		List<IResource> resources = new ArrayList<>();
		for (Object element : selection.toArray()) {
			IResource resource = null;
			if (element instanceof PullRequestChangedFile) {
				resource = ((PullRequestChangedFile) element).getWorkspaceFile();
			} else if (element instanceof PullRequestFolderEntry) {
				resource = ((PullRequestFolderEntry) element).getContainer();
			}
			if (resource != null) {
				resources.add(resource);
			}
		}

		if (resources.isEmpty()) {
			return null;
		}

		return new ShowInContext(null, new StructuredSelection(resources));
	}

	/**
	 * Creates a context menu for the changed files tree viewer.
	 * Follows the pattern from StagingView.createPopupMenu().
	 *
	 * @param treeViewer
	 *            the tree viewer to add the popup menu to
	 */
	private void createChangedFilesPopupMenu(final TreeViewer treeViewer) {
		final MenuManager menuMgr = new MenuManager();
		menuMgr.setRemoveAllWhenShown(true);
		Control control = treeViewer.getControl();
		control.setMenu(menuMgr.createContextMenu(control));
		menuMgr.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager manager) {
				control.setFocus();
				final IStructuredSelection selection = treeViewer
						.getStructuredSelection();
				if (selection.isEmpty()) {
					return;
				}

				// Collect selected files
				List<PullRequestChangedFile> selectedFiles = new ArrayList<>();
				List<PullRequestFolderEntry> selectedFolders = new ArrayList<>();
				boolean onlyFoldersSelected = true;

				for (Object element : selection.toArray()) {
					if (element instanceof PullRequestFolderEntry) {
						selectedFolders.add((PullRequestFolderEntry) element);
					} else if (element instanceof PullRequestChangedFile) {
						onlyFoldersSelected = false;
						selectedFiles.add((PullRequestChangedFile) element);
					}
				}

				// "Open in Workspace" action - only for files that exist in workspace or filesystem
				if (!onlyFoldersSelected && !selectedFiles.isEmpty()) {
					Action openInWorkspaceAction = new Action(
							PRText.CommitFileDiffViewer_OpenWorkingTreeVersionInEditorMenuLabel,
							UIIcons.GOTO_INPUT) {
						@Override
						public void run() {
							openInWorkspace(selectedFiles);
						}
					};
					// Enable for non-deleted files - actual file resolution happens on action execution
					boolean anyOpenable = selectedFiles.stream()
							.anyMatch(f -> f.getChangeType() != PullRequestChangedFile.ChangeType.DELETED);
					openInWorkspaceAction.setEnabled(anyOpenable);
					menuMgr.add(openInWorkspaceAction);
				}

				menuMgr.add(new Separator());

				// "Show In >" submenu
				menuMgr.add(createShowInMenu());

				// "Copy Path" action
				menuMgr.add(createCopyPathAction(treeViewer));
			}
		});
	}

	/**
	 * Creates the "Show In >" submenu.
	 *
	 * @return the show in menu contribution item
	 */
	private MenuManager createShowInMenu() {
		return UIUtils.createShowInMenu(getSite().getWorkbenchWindow());
	}

	/**
	 * Creates an action to copy the selected file/folder paths to the clipboard.
	 * Follows the pattern from StagingView.createSelectionPathCopyAction().
	 *
	 * @param viewer
	 *            the tree viewer
	 * @return the copy path action
	 */
	private IAction createCopyPathAction(final TreeViewer viewer) {
		IStructuredSelection selection = viewer.getStructuredSelection();
		String copyPathActionText = MessageFormat.format(
				PRText.StagingView_CopyPaths,
				Integer.valueOf(selection.size()));
		IAction copyAction = ActionUtils.createGlobalAction(ActionFactory.COPY,
				() -> copyPathToClipboard(viewer));
		copyAction.setText(copyPathActionText);
		return copyAction;
	}

	/**
	 * Copies the paths of the selected items to the clipboard.
	 *
	 * @param viewer
	 *            the tree viewer
	 */
	private void copyPathToClipboard(final TreeViewer viewer) {
		Clipboard cb = new Clipboard(viewer.getControl().getDisplay());
		try {
			TextTransfer t = TextTransfer.getInstance();
			String text = getPathsFromSelection(viewer.getStructuredSelection());
			if (text != null) {
				cb.setContents(new Object[] { text }, new Transfer[] { t });
			}
		} finally {
			cb.dispose();
		}
	}

	/**
	 * Gets the paths from the current selection as a string.
	 *
	 * @param selection
	 *            the current selection
	 * @return the paths as a string, or null if no valid paths
	 */
	private String getPathsFromSelection(IStructuredSelection selection) {
		Object[] selectionEntries = selection.toArray();
		if (selectionEntries.length <= 0) {
			return null;
		} else if (selectionEntries.length == 1) {
			return getPathFromElement(selectionEntries[0]);
		} else {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < selectionEntries.length; i++) {
				String text = getPathFromElement(selectionEntries[i]);
				if (text != null) {
					if (i < selectionEntries.length - 1) {
						sb.append(text).append(System.lineSeparator());
					} else {
						sb.append(text);
					}
				}
			}
			return sb.toString();
		}
	}

	/**
	 * Gets the path from a single element.
	 *
	 * @param element
	 *            the element (PullRequestChangedFile or PullRequestFolderEntry)
	 * @return the path string, or null
	 */
	private String getPathFromElement(Object element) {
		if (element instanceof PullRequestChangedFile) {
			PullRequestChangedFile file = (PullRequestChangedFile) element;
			// Return workspace location if available, otherwise repo-relative path
			IPath location = file.getLocation();
			if (location != null) {
				return location.toOSString();
			}
			return file.getPath();
		} else if (element instanceof PullRequestFolderEntry) {
			PullRequestFolderEntry folder = (PullRequestFolderEntry) element;
			// Return workspace location if available, otherwise folder path
			IPath location = folder.getLocation();
			if (location != null) {
				return location.toOSString();
			}
			return folder.getPath().toString();
		}
		return null;
	}

	/**
	 * Opens the selected changed files in the working tree editor.
	 * <p>
	 * This method attempts to open the workspace file using Eclipse's standard
	 * editor opening API. If the file is not in the workspace, it falls back
	 * to opening via external editor using the filesystem path.
	 * </p>
	 *
	 * @param files
	 *            the files to open
	 */
	private void openInWorkspace(List<PullRequestChangedFile> files) {
		Repository repo = gitRepository;
		if (repo == null) {
			return;
		}

		for (PullRequestChangedFile file : files) {
			// Skip deleted files
			if (file.getChangeType() == PullRequestChangedFile.ChangeType.DELETED) {
				continue;
			}

			// First try to open as workspace file
			IFile workspaceFile = file.getWorkspaceFile();
			if (workspaceFile != null && workspaceFile.exists()) {
				try {
					org.eclipse.ui.ide.IDE.openEditor(
							getSite().getPage(), workspaceFile);
				} catch (org.eclipse.ui.PartInitException e) {
					Activator.logError(MessageFormat.format(
							"Failed to open workspace file: {0}", //$NON-NLS-1$
							workspaceFile.getFullPath()), e);
				}
			} else {
				// Fallback: open file from filesystem
				String relativePath = file.getPath();
				java.io.File fsFile = new org.eclipse.core.runtime.Path(
						repo.getWorkTree().getAbsolutePath())
						.append(relativePath).toFile();
				if (fsFile.exists()) {
					DiffViewer.openFileInEditor(fsFile, -1);
				} else {
					Activator.logWarning(MessageFormat.format(
							"File does not exist: {0}", //$NON-NLS-1$
							fsFile.getAbsolutePath()));
				}
			}
		}
	}
}
