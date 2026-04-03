package org.eclipse.egit.pullrequest.internal.ui;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.PullRequestCommit;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

/**
 * View for displaying commits in a pull request
 */
public class PullRequestCommitsView extends ViewPart {

	/**
	 * View ID
	 */
	public static final String VIEW_ID = "org.eclipse.egit.pullrequest.PullRequestCommitsView"; //$NON-NLS-1$

	private TableViewer commitsViewer;

	private PullRequest selectedPullRequest;

	private List<PullRequestCommit> commits = new ArrayList<>();

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

	@Override
	public void createPartControl(Composite parent) {
		GridLayoutFactory.fillDefaults().applyTo(parent);

		setContentDescription(PRText.CommitsView_Title);

		TableColumnLayout tableColumnLayout = new TableColumnLayout();
		Composite layoutComposite = new Composite(parent, SWT.NONE);
		layoutComposite.setLayout(tableColumnLayout);
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(layoutComposite);

		commitsViewer = new TableViewer(layoutComposite,
				SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		commitsViewer.getTable().setHeaderVisible(true);
		commitsViewer.getTable().setLinesVisible(true);

		setupColumns(tableColumnLayout);

		commitsViewer.setContentProvider(new IStructuredContentProvider() {
			@Override
			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof List) {
					return ((List<?>) inputElement).toArray();
				}
				return new Object[0];
			}
		});

		commitsViewer.setInput(commits);

		// Add double-click listener to view single commit
		commitsViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				handleDoubleClick(event);
			}
		});

		// Create context menu
		createContextMenu();

		// Register as selection provider for view communication
		getSite().setSelectionProvider(commitsViewer);
	}

	/**
	 * Creates the context menu for the commits viewer
	 */
	private void createContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});

		Menu menu = menuMgr.createContextMenu(commitsViewer.getControl());
		commitsViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, commitsViewer);
	}

	/**
	 * Fills the context menu with actions based on current selection
	 *
	 * @param manager
	 *            the menu manager
	 */
	private void fillContextMenu(IMenuManager manager) {
		IStructuredSelection selection = (IStructuredSelection) commitsViewer
				.getSelection();

		if (selection.isEmpty()) {
			return;
		}

		if (selection.size() == 1) {
			// Single commit selected - show "Review Commit" action
			Action reviewCommitAction = new Action(
					"Review Commit Changes") { //$NON-NLS-1$
				@Override
				public void run() {
					reviewSingleCommit((PullRequestCommit) selection
							.getFirstElement());
				}
			};
			manager.add(reviewCommitAction);
		} else if (selection.size() > 1) {
			// Multiple commits selected - show "Review Selected Range" action
			Action reviewRangeAction = new Action(
					"Review Selected Range") { //$NON-NLS-1$
				@Override
				public void run() {
					reviewCommitRange(selection);
				}
			};
			manager.add(reviewRangeAction);
		}

		// Add separator for extensibility
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void setupColumns(TableColumnLayout layout) {
		// SHA Column
		TableViewerColumn shaColumn = createColumn(layout,
				PRText.CommitsView_ColumnSHA, 15, SWT.LEFT);
		shaColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequestCommit) {
					return ((PullRequestCommit) element).getShortId();
				}
				return ""; //$NON-NLS-1$
			}
		});

		// Message Column
		TableViewerColumn messageColumn = createColumn(layout,
				PRText.CommitsView_ColumnMessage, 50, SWT.LEFT);
		messageColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequestCommit) {
					return ((PullRequestCommit) element).getFirstLine();
				}
				return ""; //$NON-NLS-1$
			}
		});

		// Author Column
		TableViewerColumn authorColumn = createColumn(layout,
				PRText.CommitsView_ColumnAuthor, 20, SWT.LEFT);
		authorColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequestCommit) {
					PullRequestCommit commit = (PullRequestCommit) element;
					String author = commit.getAuthorName();
					return author != null ? author : ""; //$NON-NLS-1$
				}
				return ""; //$NON-NLS-1$
			}
		});

		// Date Column
		TableViewerColumn dateColumn = createColumn(layout,
				PRText.CommitsView_ColumnDate, 15, SWT.LEFT);
		dateColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequestCommit) {
					PullRequestCommit commit = (PullRequestCommit) element;
					long authorDate = commit.getAuthorDate();
					if (authorDate > 0) {
						return DATE_FORMAT.format(new Date(authorDate));
					}
				}
				return ""; //$NON-NLS-1$
			}
		});
	}

	private TableViewerColumn createColumn(TableColumnLayout layout,
			String text, int weight, int style) {
		TableViewerColumn column = new TableViewerColumn(commitsViewer, style);
		column.getColumn().setText(text);
		layout.setColumnData(column.getColumn(), new ColumnWeightData(weight));
		return column;
	}

	/**
	 * Loads commits for the specified pull request
	 *
	 * @param pr
	 *            the pull request to load commits for
	 */
	public void loadPullRequest(PullRequest pr) {
		selectedPullRequest = pr;

		// Fetch commits in a background job
		Job job = new Job(PRText.CommitsView_JobFetchingCommits) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask(PRText.CommitsView_JobFetchingCommits,
						IProgressMonitor.UNKNOWN);

				try {
					IPullRequestClient client = PullRequestClientFactory
							.createClient();
					if (client == null) {
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
								PRText.CommitsView_ErrorProviderNotConfigured);
					}

					// Fetch commits
					final List<PullRequestCommit> fetchedCommits = client
							.getPullRequestCommits(pr.getId());

					Display.getDefault().asyncExec(() -> {
						if (!commitsViewer.getControl().isDisposed()) {
							commits.clear();
							commits.addAll(fetchedCommits);
							commitsViewer.setInput(commits);
							commitsViewer.refresh();
							updateContentDescription();
						}
					});

					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							PRText.CommitsView_ErrorFetchingCommits, e);
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							PRText.CommitsView_ErrorFetchingCommits + ": " //$NON-NLS-1$
									+ e.getMessage(),
							e);
				} finally {
					monitor.done();
				}
			}
		};

		job.setUser(true);
		job.schedule();
	}

	private void updateContentDescription() {
		if (selectedPullRequest != null) {
			setContentDescription(PRText.CommitsView_Title + " - #" //$NON-NLS-1$
					+ selectedPullRequest.getId() + ": " //$NON-NLS-1$
					+ selectedPullRequest.getTitle());
		} else {
			setContentDescription(PRText.CommitsView_Title);
		}
	}

	/**
	 * Handles double-click events on commits. Only works for single commit
	 * selection. For multiple commits, use the context menu.
	 *
	 * @param event
	 *            the double-click event
	 */
	private void handleDoubleClick(DoubleClickEvent event) {
		IStructuredSelection selection = (IStructuredSelection) event
				.getSelection();
		if (selection.isEmpty() || selection.size() != 1) {
			return;
		}

		PullRequestCommit commit = (PullRequestCommit) selection
				.getFirstElement();
		reviewSingleCommit(commit);
	}

	/**
	 * Reviews a single commit by showing its changes in the Changed Files View
	 *
	 * @param commit
	 *            the commit to review
	 */
	private void reviewSingleCommit(PullRequestCommit commit) {
		if (selectedPullRequest == null || commit == null) {
			return;
		}

		try {
			IWorkbenchPage page = getSite().getWorkbenchWindow()
					.getActivePage();
			if (page == null) {
				return;
			}

			// Launch synchronize view for this commit
			PullRequestSynchronizeLauncher.launchForCommit(
					selectedPullRequest, commit);
		} catch (Exception e) {
			Activator.logError("Failed to launch synchronize view", e); //$NON-NLS-1$
		}
	}

	/**
	 * Reviews a range of commits by showing combined changes in the Changed
	 * Files View
	 *
	 * @param selection
	 *            the selection containing multiple commits
	 */
	private void reviewCommitRange(IStructuredSelection selection) {
		if (selectedPullRequest == null || selection.isEmpty()
				|| selection.size() < 2) {
			return;
		}

		// Find the earliest and latest commits in the selection
		List<PullRequestCommit> selectedCommits = new ArrayList<>();
		for (Object item : selection.toList()) {
			if (item instanceof PullRequestCommit) {
				selectedCommits.add((PullRequestCommit) item);
			}
		}

		if (selectedCommits.size() < 2) {
			return;
		}

		// Find indices in the commits list to determine range
		int earliestIndex = Integer.MAX_VALUE;
		int latestIndex = -1;
		PullRequestCommit earliestCommit = null;
		PullRequestCommit latestCommit = null;

		for (PullRequestCommit selectedCommit : selectedCommits) {
			int index = commits.indexOf(selectedCommit);
			if (index >= 0) {
				if (index < earliestIndex) {
					earliestIndex = index;
					earliestCommit = selectedCommit;
				}
				if (index > latestIndex) {
					latestIndex = index;
					latestCommit = selectedCommit;
				}
			}
		}

		if (earliestCommit == null || latestCommit == null) {
			return;
		}

		try {
			IWorkbenchPage page = getSite().getWorkbenchWindow()
					.getActivePage();
			if (page == null) {
				return;
			}

		// Launch synchronize view for commit range
		PullRequestSynchronizeLauncher.launchForCommitRange(
				selectedPullRequest, earliestCommit, latestCommit);
	} catch (Exception e) {
		Activator.logError("Failed to launch synchronize view", e); //$NON-NLS-1$
	}
	}

	@Override
	public void setFocus() {
		if (commitsViewer != null && !commitsViewer.getControl().isDisposed()) {
			commitsViewer.getControl().setFocus();
		}
	}

	@Override
	public void dispose() {
		super.dispose();
	}
}
