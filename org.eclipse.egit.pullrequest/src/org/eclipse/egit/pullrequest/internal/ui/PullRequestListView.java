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
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.ui.internal.PreferenceBasedDateFormatter;
import org.eclipse.egit.pullrequest.internal.ui.TreeColumnPatternFilter;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.egit.pullrequest.internal.ui.DropDownMenuAction;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.ViewPart;

/**
 * View for displaying a list of pull requests
 */
public class PullRequestListView extends ViewPart {

	public static final String VIEW_ID = "org.eclipse.egit.pullrequest.PullRequestListView"; //$NON-NLS-1$

	private FormToolkit toolkit;

	private Form form;

	private TreeViewer pullRequestViewer;

	private PreferenceBasedDateFormatter dateFormatter;

	private ResourceManager imageCache;

	private List<PullRequest> pullRequests = new ArrayList<>();

	private Action refreshAction;

	private Action authorFilterAction;

	private DropDownMenuAction stateFilterAction;

	// Server-side filter settings
	private String currentAuthorFilter = null;

	private String currentStateFilter = null;

	private boolean configWarningShown = false;

	@Override
	public void createPartControl(Composite parent) {
		dateFormatter = PreferenceBasedDateFormatter.create();
		GridLayoutFactory.fillDefaults().applyTo(parent);

		toolkit = new FormToolkit(parent.getDisplay());
		parent.addDisposeListener(e -> toolkit.dispose());

		form = toolkit.createForm(parent);
		form.setText("Pull Requests"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, true).applyTo(form);
		toolkit.decorateFormHeading(form);
		GridLayoutFactory.fillDefaults().applyTo(form.getBody());

		// Create composite for tree
		Composite tableComposite = toolkit.createComposite(form.getBody());
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tableComposite);
		GridLayoutFactory.fillDefaults().applyTo(tableComposite);

		final TreeColumnLayout treeColumnLayout = new TreeColumnLayout();

		// TODO refactor the construction of the filtered tree to own method.  
		FilteredTree filteredTree = new FilteredTree(tableComposite,
				SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
				new TreeColumnPatternFilter(), true, true) {

			@Override
			protected void createControl(Composite composite, int treeStyle) {
				super.createControl(composite, treeStyle);
				treeComposite.setLayout(treeColumnLayout);
			}
		};

		toolkit.adapt(filteredTree);
		pullRequestViewer = filteredTree.getViewer();
		pullRequestViewer.getTree().setHeaderVisible(true);
		pullRequestViewer.getTree().setLinesVisible(true);
		pullRequestViewer.getTree().setData(FormToolkit.KEY_DRAW_BORDER,
				FormToolkit.TREE_BORDER);

		setupColumns(treeColumnLayout);

		pullRequestViewer.setContentProvider(new ITreeContentProvider() {
			@Override
			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof List) {
					return ((List<?>) inputElement).toArray();
				}
				return new Object[0];
			}

			@Override
			public Object[] getChildren(Object parentElement) {
				return null;
			}

			@Override
			public Object getParent(Object element) {
				return null;
			}

			@Override
			public boolean hasChildren(Object element) {
				return false;
			}
		});

		pullRequestViewer.setInput(pullRequests);

		// TODO refactor selection mechanism to own method
		// Register as selection provider for view communication
		getSite().setSelectionProvider(pullRequestViewer);

		// Add double-click listener to load PR
		pullRequestViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event
						.getSelection();
				if (!selection.isEmpty()) {
					Object element = selection.getFirstElement();
					if (element instanceof PullRequest) {
						loadPullRequest((PullRequest) element);
					}
				}
			}
		});

		// Load PR on Enter key
		pullRequestViewer.getTree().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
					IStructuredSelection selection = (IStructuredSelection) pullRequestViewer
							.getSelection();
					if (!selection.isEmpty()) {
						Object element = selection.getFirstElement();
						if (element instanceof PullRequest) {
							loadPullRequest((PullRequest) element);
						}
					}
				}
			}
		});

		createActions();
		createFilterActions();
		contributeToActionBars();

		// Initialize default filters
		currentStateFilter = null; // Default to OPEN (handled in createStateFilterItem)

		// Try to set author from preferences
		String username = Activator.getDefault().getPreferenceStore()
				.getString(PRPreferences.BITBUCKET_USERNAME);
		if (username != null && !username.isEmpty()) {
			currentAuthorFilter = username;
		}

		// Automatically refresh pull requests when view opens
		refreshPullRequests();
	}

	private void setupColumns(TreeColumnLayout layout) {
		// ID Column
		TreeViewerColumn idColumn = createColumn(layout, "ID", 10, SWT.LEFT); //$NON-NLS-1$
		idColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequest) {
					return String.valueOf(((PullRequest) element).getId());
				}
				return ""; //$NON-NLS-1$
			}
		});

		// Title Column
		TreeViewerColumn titleColumn = createColumn(layout, "Title", 40, //$NON-NLS-1$
				SWT.LEFT);
		titleColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequest) {
					return ((PullRequest) element).getTitle();
				}
				return ""; //$NON-NLS-1$
			}

			@Override
			public Image getImage(Object element) {
				if (element instanceof PullRequest) {
					PullRequest pr = (PullRequest) element;
					if ("OPEN".equals(pr.getState())) { //$NON-NLS-1$
						return UIIcons.getImage(getImageCache(),
								UIIcons.BRANCH);
					} else if ("MERGED".equals(pr.getState())) { //$NON-NLS-1$
						return UIIcons.getImage(getImageCache(), UIIcons.MERGE);
					} else if ("DECLINED".equals(pr.getState())) { //$NON-NLS-1$
						return UIIcons.getImage(getImageCache(), UIIcons.RESET);
					}
				}
				return null;
			}
		});

		// Author Column
		TreeViewerColumn authorColumn = createColumn(layout, "Author", 20, //$NON-NLS-1$
				SWT.LEFT);
		authorColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequest) {
					PullRequest pr = (PullRequest) element;
					if (pr.getAuthor() != null
							&& pr.getAuthor().getUser() != null) {
						return pr.getAuthor().getUser().getDisplayName();
					}
				}
				return ""; //$NON-NLS-1$
			}
		});

		// State Column
		TreeViewerColumn stateColumn = createColumn(layout, "State", 10, //$NON-NLS-1$
				SWT.LEFT);
		stateColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequest) {
					return ((PullRequest) element).getState();
				}
				return ""; //$NON-NLS-1$
			}
		});

		// Comments Column
		TreeViewerColumn commentsColumn = createColumn(layout, "Comments", 10, //$NON-NLS-1$
				SWT.LEFT);
		commentsColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequest) {
					int count = ((PullRequest) element).getCommentCount();
					return count > 0 ? String.valueOf(count) : ""; //$NON-NLS-1$
				}
				return ""; //$NON-NLS-1$
			}
		});

		// Updated Column
		TreeViewerColumn updatedColumn = createColumn(layout, "Updated", 20, //$NON-NLS-1$
				SWT.LEFT);
		updatedColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequest) {
					PullRequest pr = (PullRequest) element;
					if (pr.getUpdatedDate() != null) {
						return dateFormatter.formatDate(pr.getUpdatedDate());
					}
				}
				return ""; //$NON-NLS-1$
			}
		});
	}

	private TreeViewerColumn createColumn(TreeColumnLayout layout, String text,
			int weight, int style) {
		TreeViewerColumn column = new TreeViewerColumn(pullRequestViewer,
				style);
		column.getColumn().setText(text);
		layout.setColumnData(column.getColumn(), new ColumnWeightData(weight));
		return column;
	}

	private void createActions() {
		refreshAction = new Action("Refresh") { //$NON-NLS-1$
			@Override
			public void run() {
				refreshPullRequests();
			}
		};
		refreshAction.setImageDescriptor(UIIcons.ELCL16_REFRESH);
		refreshAction.setToolTipText("Refresh pull requests"); //$NON-NLS-1$
	}

	private void createFilterActions() {
		// Author filter action
		authorFilterAction = new Action("Filter by Author...", IAction.AS_PUSH_BUTTON) { //$NON-NLS-1$
			@Override
			public void run() {
				InputDialog dialog = new InputDialog(
					getSite().getShell(),
					"Filter by Author", //$NON-NLS-1$
					"Enter author username (leave empty for all authors):", //$NON-NLS-1$
					currentAuthorFilter != null ? currentAuthorFilter : "", //$NON-NLS-1$
					null);

				if (dialog.open() == Window.OK) {
					String newAuthor = dialog.getValue().trim();
					currentAuthorFilter = newAuthor.isEmpty() ? null : newAuthor;
					refreshPullRequests();
				}
			}
		};
		authorFilterAction.setToolTipText("Filter pull requests by author"); //$NON-NLS-1$

		// State filter dropdown action
		stateFilterAction = new DropDownMenuAction("State Filter") { //$NON-NLS-1$
			@Override
			protected Collection<IContributionItem> getActions() {
				List<IContributionItem> items = new ArrayList<>();
				items.add(createStateFilterItem("OPEN")); //$NON-NLS-1$
				items.add(createStateFilterItem("MERGED")); //$NON-NLS-1$
				items.add(createStateFilterItem("DECLINED")); //$NON-NLS-1$
				items.add(createStateFilterItem("ALL")); //$NON-NLS-1$
				return items;
			}
		};
		stateFilterAction.setToolTipText("Filter pull requests by state"); //$NON-NLS-1$
		stateFilterAction.setImageDescriptor(UIIcons.ELCL16_FILTER);
	}

	private ActionContributionItem createStateFilterItem(final String state) {
		Action action = new Action(state, IAction.AS_RADIO_BUTTON) {
			@Override
			public void run() {
				if (isChecked()) {
					currentStateFilter = "ALL".equals(state) ? null : state; //$NON-NLS-1$
					refreshPullRequests();
				}
			}
		};
		// Set OPEN as default checked
		if ("OPEN".equals(state) && currentStateFilter == null) { //$NON-NLS-1$
			action.setChecked(true);
		} else if (state.equals(currentStateFilter)) {
			action.setChecked(true);
		}
		return new ActionContributionItem(action);
	}

	private void contributeToActionBars() {
		IActionBars actionBars = getViewSite().getActionBars();
		IToolBarManager toolBarManager = actionBars.getToolBarManager();
		toolBarManager.add(refreshAction);

		// Add filter actions to view menu (dropdown in top-right)
		IMenuManager menuManager = actionBars.getMenuManager();
		menuManager.add(authorFilterAction);
		menuManager.add(stateFilterAction);
	}

	private void refreshPullRequests() {
		// Create client from factory
		IPullRequestClient client = PullRequestClientFactory.createClient();

		if (client == null) {
			form.setText("Pull Requests - Not configured"); //$NON-NLS-1$
			pullRequests.clear();
			pullRequestViewer.refresh();

			// Show message once per view session
			if (!configWarningShown) {
				configWarningShown = true;
				Display.getDefault().asyncExec(() -> {
					if (!pullRequestViewer.getControl().isDisposed()) {
						MessageDialog.openInformation(
								pullRequestViewer.getControl().getShell(),
								"Pull Request Configuration Required", //$NON-NLS-1$
								"Pull request provider is not configured.\n\n" //$NON-NLS-1$
								+ "Please configure your pull request provider in:\n" //$NON-NLS-1$
								+ "Window > Preferences > Team > Pull Requests"); //$NON-NLS-1$
					}
				});
			}
			return;
		}

		// Reset warning flag when successfully configured
		configWarningShown = false;

		Job job = new Job("Fetching pull requests") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("Fetching pull requests", //$NON-NLS-1$
						IProgressMonitor.UNKNOWN);

				try {
					// Fetch PRs with server-side filters only
					List<PullRequest> fetchedPRs = client.getPullRequests(
							currentStateFilter, currentAuthorFilter, null, 100, 0);

					Display.getDefault().asyncExec(() -> {
						if (!pullRequestViewer.getControl().isDisposed()) {
							pullRequests.clear();
							pullRequests.addAll(fetchedPRs);
							pullRequestViewer.setInput(pullRequests);
							pullRequestViewer.refresh();

							// Update form title with filter info
							updateFormTitle();
						}
					});

					return Status.OK_STATUS;
				} catch (IOException e) {
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							"Failed to fetch pull requests", e); //$NON-NLS-1$
				} finally {
					monitor.done();
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/**
	 * Updates the form title with the current PR count and active filters
	 */
	private void updateFormTitle() {
		List<String> filters = new ArrayList<>();

		if (currentAuthorFilter != null && !currentAuthorFilter.isEmpty()) {
			filters.add("Author: " + currentAuthorFilter); //$NON-NLS-1$
		}

		String state = currentStateFilter != null ? currentStateFilter : "OPEN"; //$NON-NLS-1$
		filters.add("State: " + state); //$NON-NLS-1$

		String filterDisplay = filters.isEmpty() ? "" : " - " + String.join(", ", filters); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		form.setText(MessageFormat.format("Pull Requests ({0}){1}", //$NON-NLS-1$
				Integer.valueOf(pullRequests.size()), filterDisplay));
	}

	@Override
	public void setFocus() {
		pullRequestViewer.getControl().setFocus();
	}

	/**
	 * Loads a pull request by opening the PullRequestChangedFilesView and
	 * triggering it to fetch and display the PR's changed files and comments.
	 *
	 * @param pr
	 *            the pull request to load
	 */
	private void loadPullRequest(PullRequest pr) {
		try {
			IWorkbenchPage page = getSite().getWorkbenchWindow()
					.getActivePage();
			IWorkbenchPart part = page
					.showView(PullRequestChangedFilesView.VIEW_ID);

			if (part instanceof PullRequestChangedFilesView) {
				((PullRequestChangedFilesView) part).loadPullRequest(pr);
			}
		} catch (PartInitException e) {
			Activator.logError("Failed to open PullRequestChangedFilesView", //$NON-NLS-1$
					e);
		}
	}

	@Override
	public void dispose() {
		if (stateFilterAction != null) {
			stateFilterAction.dispose();
		}
		if (imageCache != null) {
			imageCache.dispose();
		}
		if (toolkit != null) {
			toolkit.dispose();
		}
		super.dispose();
	}

	private ResourceManager getImageCache() {
		if (imageCache == null) {
			imageCache = new LocalResourceManager(
					JFaceResources.getResources());
		}
		return imageCache;
	}
}
