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
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
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

		// Register as selection provider for view communication
		getSite().setSelectionProvider(commitsViewer);
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
