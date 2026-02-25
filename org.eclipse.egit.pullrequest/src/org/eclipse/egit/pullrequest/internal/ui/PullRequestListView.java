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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.ViewPart;

/**
 * View for displaying a list of pull requests
 */
public class PullRequestListView extends ViewPart {

	public static final String VIEW_ID = "org.eclipse.egit.pullrequest.PullRequestListView"; //$NON-NLS-1$

	private FormToolkit toolkit;
	// Styled container with the title "Pull Requests (count) and filters"
	private Form form;

	// Filters
	private Action refreshAction;
	private Action checkoutBranchAction;
	private Action authorFilterAction;
	private DropDownMenuAction stateFilterAction;
	private String currentAuthorFilter = null;
	private String currentStateFilter = null;

	// List of pull requests
	private TableViewer pullRequestViewer;
	private List<PullRequest> pullRequests = new ArrayList<>();

	private SimpleDateFormat dateFormatter;

	private ResourceManager imageCache;
	private boolean configWarningShown = false;

	@Override
	public void createPartControl(Composite parent) {
		dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm"); //$NON-NLS-1$
		GridLayoutFactory.fillDefaults().applyTo(parent);

		setupForm(parent);

		// Create composite for table
		Composite tableComposite = toolkit.createComposite(form.getBody());
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tableComposite);
		final TableColumnLayout tableColumnLayout = new TableColumnLayout();

		createFilteredTable(tableComposite, tableColumnLayout);
		setupColumns(tableColumnLayout);

		pullRequestViewer.setContentProvider(ArrayContentProvider.getInstance());
		pullRequestViewer.setInput(pullRequests);

		// setup actions
		setupSelectionHandling();
		setupContextMenu();
		createRefreshActions();
		createFilterActions();
		contributeToActionBars();

		// Initialize default filters
		currentStateFilter = null; // Default to OPEN (handled in createStateFilterItem)
		initializeAuthorFilterFromPreferences();

		// Automatically refresh pull requests when view opens
		refreshPullRequests();
	}

	private void setupForm(Composite parent) {
		toolkit = new FormToolkit(parent.getDisplay());
		parent.addDisposeListener(e -> toolkit.dispose());

		form = toolkit.createForm(parent);
		form.setText("Pull Requests"); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, true).applyTo(form);
		toolkit.decorateFormHeading(form);
		GridLayoutFactory.fillDefaults().applyTo(form.getBody());
	}

	private void initializeAuthorFilterFromPreferences() {
		// Try to set author from preferences
		String username = Activator.getDefault().getPreferenceStore().getString(PRPreferences.BITBUCKET_USERNAME);
		if (username != null && !username.isEmpty()) {
			currentAuthorFilter = username;
		}
	}

	private void setupColumns(TableColumnLayout layout) {
		// ID Column
		TableViewerColumn idColumn = createColumn(layout, "ID", 10, SWT.LEFT); //$NON-NLS-1$
		idColumn.setLabelProvider(new PullRequestLabelProvider() {
			@Override
			protected String getTextForPullRequest(PullRequest pr) {
				return String.valueOf(pr.getId());
			}
		});

		// Title Column
		TableViewerColumn titleColumn = createColumn(layout, "Title", 40, //$NON-NLS-1$
				SWT.LEFT);
		titleColumn.setLabelProvider(new PullRequestLabelProvider() {
			@Override
			protected String getTextForPullRequest(PullRequest pr) {
				return pr.getTitle();
			}

			@Override
			public Image getImage(Object element) {
				if (element instanceof PullRequest) {
					PullRequest pr = (PullRequest) element;
					if ("OPEN".equals(pr.getState())) { //$NON-NLS-1$
						return UIIcons.getImage(getImageCache(), UIIcons.BRANCH);
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
		TableViewerColumn authorColumn = createColumn(layout, "Author", 20, //$NON-NLS-1$
				SWT.LEFT);
		authorColumn.setLabelProvider(new PullRequestLabelProvider() {
			@Override
			protected String getTextForPullRequest(PullRequest pr) {
				if (pr.getAuthor() != null && pr.getAuthor().getUser() != null) {
					return pr.getAuthor().getUser().getDisplayName();
				}
				return ""; //$NON-NLS-1$
			}
		});

		// State Column
		TableViewerColumn stateColumn = createColumn(layout, "State", 10, //$NON-NLS-1$
				SWT.LEFT);
		stateColumn.setLabelProvider(new PullRequestLabelProvider() {
			@Override
			protected String getTextForPullRequest(PullRequest pr) {
				return pr.getState();
			}
		});

		// Comments Column
		TableViewerColumn commentsColumn = createColumn(layout, "Comments", 10, //$NON-NLS-1$
				SWT.LEFT);
		commentsColumn.setLabelProvider(new PullRequestLabelProvider() {
			@Override
			protected String getTextForPullRequest(PullRequest pr) {
				int count = pr.getCommentCount();
				return count > 0 ? String.valueOf(count) : ""; //$NON-NLS-1$
			}
		});

		// Reviewers Column
		TableViewerColumn reviewersColumn = createColumn(layout, "Reviewers", //$NON-NLS-1$
				15, SWT.LEFT);
		reviewersColumn.setLabelProvider(new PullRequestLabelProvider() {
			@Override
			protected String getTextForPullRequest(PullRequest pr) {
				List<PullRequest.PullRequestParticipant> reviewers = pr
						.getReviewers();
				if (reviewers == null || reviewers.isEmpty()) {
					return ""; //$NON-NLS-1$
				}
				return formatReviewers(reviewers);
			}

			@Override
			public String getToolTipText(Object element) {
				if (element instanceof PullRequest) {
					PullRequest pr = (PullRequest) element;
					List<PullRequest.PullRequestParticipant> reviewers = pr
							.getReviewers();
					if (reviewers == null || reviewers.isEmpty()) {
						return null;
					}
					return formatReviewersTooltip(reviewers);
				}
				return null;
			}
		});

		// Updated Column
		TableViewerColumn updatedColumn = createColumn(layout, "Updated", 20, //$NON-NLS-1$
				SWT.LEFT);
		updatedColumn.setLabelProvider(new PullRequestLabelProvider() {
			@Override
			protected String getTextForPullRequest(PullRequest pr) {
				if (pr.getUpdatedDate() != null) {
					return dateFormatter.format(pr.getUpdatedDate());
				}
				return ""; //$NON-NLS-1$
			}
		});
	}

	/**
	 * Base label provider for pull request columns that handles the common
	 * type checking and delegates to a template method.
	 */
	private abstract static class PullRequestLabelProvider extends ColumnLabelProvider {
		@Override
		public final String getText(Object element) {
			if (element instanceof PullRequest) {
				return getTextForPullRequest((PullRequest) element);
			}
			return ""; //$NON-NLS-1$
		}

		/**
		 * Returns the text for a pull request element.
		 *
		 * @param pr the pull request
		 * @return the text to display
		 */
		protected abstract String getTextForPullRequest(PullRequest pr);
	}

	private TableViewerColumn createColumn(TableColumnLayout layout, String text, int weight, int style) {
		TableViewerColumn column = new TableViewerColumn(pullRequestViewer, style);
		column.getColumn().setText(text);
		layout.setColumnData(column.getColumn(), new ColumnWeightData(weight));
		return column;
	}

	private void createFilteredTable(Composite parent, TableColumnLayout tableColumnLayout) {
		// TableColumnLayout must be the sole layout on a composite whose only
		// child is the table control
		parent.setLayout(tableColumnLayout);

		// Create the table viewer
		pullRequestViewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		pullRequestViewer.getTable().setHeaderVisible(true);
		pullRequestViewer.getTable().setLinesVisible(true);
		pullRequestViewer.getTable().setData(FormToolkit.KEY_DRAW_BORDER, FormToolkit.TREE_BORDER);
		toolkit.adapt(pullRequestViewer.getTable());
	}

	/*
	 *  The user can load pull requests either with a double-click or by selecting a PR and pressing Enter.
	 */
	private void setupSelectionHandling() {
		getSite().setSelectionProvider(pullRequestViewer);

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

		pullRequestViewer.getTable().addKeyListener(new KeyAdapter() {
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
				} else if (e.keyCode == SWT.F5) {
					refreshPullRequests();
				}
			}
		});
	}

	/**
	 * Sets up the context menu for the pull request list.
	 */
	private void setupContextMenu() {
		MenuManager menuManager = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});

		Menu menu = menuManager.createContextMenu(
				pullRequestViewer.getControl());
		pullRequestViewer.getControl().setMenu(menu);
	}

	/**
	 * Fills the context menu with actions based on the current selection.
	 *
	 * @param manager
	 *            the menu manager to fill
	 */
	private void fillContextMenu(IMenuManager manager) {
		IStructuredSelection selection = (IStructuredSelection) pullRequestViewer
				.getSelection();
		if (selection.isEmpty()) {
			return;
		}

		Object element = selection.getFirstElement();
		if (!(element instanceof PullRequest)) {
			return;
		}

		PullRequest pr = (PullRequest) element;

		// Open pull request action
		manager.add(new Action(PRText.PullRequestListView_OpenPullRequest) {
			@Override
			public void run() {
				loadPullRequest(pr);
			}
		});

		manager.add(new Separator());

		// Manage Reviewers action
		manager.add(new Action(PRText.ReviewerManagement_MenuLabel) {
			@Override
			public void run() {
				ManageReviewersAction action = new ManageReviewersAction(
						getSite().getShell());
				action.setPullRequest(pr);
				action.setRefreshCallback(() -> refreshPullRequests());
				action.run();
			}
		});

		// Add Myself as Reviewer action
		manager.add(new Action(PRText.AddMyselfAsReviewer_MenuLabel) {
			@Override
			public void run() {
				AddMyselfAsReviewerAction action = new AddMyselfAsReviewerAction(
						getSite().getShell());
				action.setPullRequest(pr);
				action.setRefreshCallback(() -> refreshPullRequests());
				action.run();
			}
		});

		manager.add(new Separator());

		// Checkout Branch action
		if (checkoutBranchAction != null) {
			manager.add(checkoutBranchAction);
		}
	}

	private void createRefreshActions() {
		refreshAction = new Action("Refresh") { //$NON-NLS-1$
			@Override
			public void run() {
				refreshPullRequests();
			}
		};
		refreshAction.setImageDescriptor(UIIcons.ELCL16_REFRESH);
		refreshAction.setToolTipText("Refresh pull requests"); //$NON-NLS-1$

		checkoutBranchAction = new Action(PRText.CheckoutBranch_ActionLabel) {
			@Override
			public void run() {
				IStructuredSelection selection = (IStructuredSelection) pullRequestViewer
						.getSelection();
				if (!selection.isEmpty()) {
					PullRequest pr = (PullRequest) selection
							.getFirstElement();
					CheckoutPullRequestBranchJob job = new CheckoutPullRequestBranchJob(
							pr, getSite().getShell());
					job.schedule();
				}
			}
		};
		checkoutBranchAction
				.setToolTipText(PRText.CheckoutBranch_ActionTooltip);
		checkoutBranchAction.setEnabled(false); // Initially disabled

		// Update enabled state based on selection
		pullRequestViewer.addSelectionChangedListener(event -> {
			boolean hasSelection = !event.getSelection().isEmpty();
			checkoutBranchAction.setEnabled(hasSelection);
		});
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
		toolBarManager.add(checkoutBranchAction);

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
							pullRequests = new ArrayList<>(fetchedPRs);
							pullRequestViewer.setInput(pullRequests);

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
	 * the PR Overview editor.
	 *
	 * @param pr
	 *            the pull request to load
	 */
	private void loadPullRequest(PullRequest pr) {
		try {
			IWorkbenchPage page = getSite().getWorkbenchWindow()
					.getActivePage();

			// Open overview editor
			PullRequestOverviewEditorInput input =
					new PullRequestOverviewEditorInput(pr);
			page.openEditor(input, PullRequestOverviewView.EDITOR_ID);

		// Update changed files view
		IWorkbenchPart part = page
				.showView(PullRequestChangedFilesView.VIEW_ID);

		if (part instanceof PullRequestChangedFilesView) {
			((PullRequestChangedFilesView) part).loadPullRequest(pr);
		}

		// Update commits view
		IWorkbenchPart commitsPart = page
				.showView(PullRequestCommitsView.VIEW_ID);

		if (commitsPart instanceof PullRequestCommitsView) {
			((PullRequestCommitsView) commitsPart).loadPullRequest(pr);
		}
	} catch (PartInitException e) {
		Activator.logError("Failed to open pull request views", //$NON-NLS-1$
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

	/**
	 * Formats a list of reviewers for display in the table column. Shows up to
	 * 2 reviewer names, then "+N more" if there are additional reviewers.
	 * Approved reviewers are indicated with a checkmark.
	 *
	 * @param reviewers
	 *            the list of reviewers
	 * @return formatted string for column display
	 */
	private String formatReviewers(
			List<PullRequest.PullRequestParticipant> reviewers) {
		if (reviewers == null || reviewers.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		StringBuilder result = new StringBuilder();
		int displayCount = Math.min(2, reviewers.size());

		for (int i = 0; i < displayCount; i++) {
			if (i > 0) {
				result.append(", "); //$NON-NLS-1$
			}
			PullRequest.PullRequestParticipant reviewer = reviewers.get(i);
			if (reviewer.getUser() != null) {
				String displayName = reviewer.getUser().getDisplayName();
				if (displayName == null || displayName.isEmpty()) {
					displayName = reviewer.getUser().getName();
				}
				result.append(displayName);
				if (reviewer.isApproved()) {
					result.append(" \u2713"); // Unicode checkmark //$NON-NLS-1$
				}
			}
		}

		if (reviewers.size() > displayCount) {
			result.append(" +"); //$NON-NLS-1$
			result.append(reviewers.size() - displayCount);
			result.append(" more"); //$NON-NLS-1$
		}

		return result.toString();
	}

	/**
	 * Formats a list of reviewers for display in a tooltip. Shows all
	 * reviewers with their approval status.
	 *
	 * @param reviewers
	 *            the list of reviewers
	 * @return formatted string for tooltip display
	 */
	private String formatReviewersTooltip(
			List<PullRequest.PullRequestParticipant> reviewers) {
		if (reviewers == null || reviewers.isEmpty()) {
			return null;
		}

		StringBuilder tooltip = new StringBuilder();
		tooltip.append("Reviewers:\n"); //$NON-NLS-1$

		for (PullRequest.PullRequestParticipant reviewer : reviewers) {
			tooltip.append("  "); //$NON-NLS-1$
			if (reviewer.getUser() != null) {
				String displayName = reviewer.getUser().getDisplayName();
				if (displayName == null || displayName.isEmpty()) {
					displayName = reviewer.getUser().getName();
				}
				tooltip.append(displayName);

				if (reviewer.isApproved()) {
					tooltip.append(" (Approved)"); //$NON-NLS-1$
				} else {
					tooltip.append(" (Pending)"); //$NON-NLS-1$
				}
			}
			tooltip.append("\n"); //$NON-NLS-1$
		}

		return tooltip.toString();
	}
}
