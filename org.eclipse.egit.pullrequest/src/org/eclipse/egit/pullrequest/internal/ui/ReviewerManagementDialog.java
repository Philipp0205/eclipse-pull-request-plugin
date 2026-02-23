/*******************************************************************************
 * Copyright (C) 2026, Philipp Hoenisch and others
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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog for managing reviewers on a pull request.
 * <p>
 * Allows adding and removing reviewers interactively.
 */
public class ReviewerManagementDialog extends Dialog {

	private final PullRequest pullRequest;

	private final IPullRequestClient client;

	private List<PullRequest.PullRequestParticipant> reviewers;

	private ListViewer reviewerListViewer;

	private Text usernameText;

	private Button addButton;

	private Button removeButton;

	private Runnable refreshCallback;

	/**
	 * Creates a new reviewer management dialog.
	 *
	 * @param parentShell
	 *            the parent shell
	 * @param pr
	 *            the pull request to manage reviewers for
	 * @param client
	 *            the client to use for API calls
	 */
	public ReviewerManagementDialog(Shell parentShell, PullRequest pr,
			IPullRequestClient client) {
		super(parentShell);
		this.pullRequest = pr;
		this.client = client;
		this.reviewers = new ArrayList<>(
				pr.getReviewers() != null ? pr.getReviewers()
						: new ArrayList<>());
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	/**
	 * Sets a callback to be invoked when reviewers are modified.
	 *
	 * @param callback
	 *            the callback to invoke
	 */
	public void setRefreshCallback(Runnable callback) {
		this.refreshCallback = callback;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(PRText.ReviewerDialog_Title);
		shell.setMinimumSize(450, 350);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		GridLayoutFactory.swtDefaults().numColumns(2).margins(10, 10)
				.applyTo(composite);

		// Message label spanning both columns
		Label messageLabel = new Label(composite, SWT.WRAP);
		messageLabel.setText(PRText.ReviewerDialog_Message);
		GridDataFactory.fillDefaults().grab(true, false).span(2, 1)
				.applyTo(messageLabel);

		// Current reviewers section
		Label reviewersLabel = new Label(composite, SWT.NONE);
		reviewersLabel.setText(PRText.ReviewerDialog_CurrentReviewers);
		GridDataFactory.fillDefaults().grab(true, false).span(2, 1)
				.applyTo(reviewersLabel);

		// Reviewer list
		reviewerListViewer = new ListViewer(composite,
				SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		reviewerListViewer.setContentProvider(
				ArrayContentProvider.getInstance());
		reviewerListViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PullRequest.PullRequestParticipant) {
					PullRequest.PullRequestParticipant p =
							(PullRequest.PullRequestParticipant) element;
					String name = p.getUser().getDisplayName();
					if (name == null) {
						name = p.getUser().getName();
					}
					if (p.isApproved()) {
						return name + PRText.OverviewView_ApprovedSuffix;
					}
					return name;
				}
				return super.getText(element);
			}
		});
		reviewerListViewer.setInput(reviewers);
		GridDataFactory.fillDefaults().grab(true, true).span(1, 2)
				.hint(300, 150).applyTo(reviewerListViewer.getControl());

		// Remove button
		removeButton = new Button(composite, SWT.PUSH);
		removeButton.setText(PRText.ReviewerDialog_RemoveButton);
		removeButton.setEnabled(false);
		removeButton.addListener(SWT.Selection, e -> removeSelectedReviewer());
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING)
				.applyTo(removeButton);

		reviewerListViewer.addSelectionChangedListener(e -> {
			removeButton.setEnabled(!e.getSelection().isEmpty());
		});

		// Spacer
		new Label(composite, SWT.NONE);

		// Add reviewer section
		Label addLabel = new Label(composite, SWT.NONE);
		addLabel.setText(PRText.ReviewerDialog_Username);
		GridDataFactory.fillDefaults().grab(true, false).span(2, 1)
				.applyTo(addLabel);

		usernameText = new Text(composite, SWT.BORDER | SWT.SINGLE);
		usernameText.setMessage(PRText.ReviewerDialog_UsernameHint);
		usernameText.addListener(SWT.Modify,
				e -> updateAddButtonState());
		usernameText.addListener(SWT.Traverse, e -> {
			if (e.detail == SWT.TRAVERSE_RETURN && addButton.isEnabled()) {
				addReviewer();
			}
		});
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(usernameText);

		addButton = new Button(composite, SWT.PUSH);
		addButton.setText(PRText.ReviewerDialog_AddButton);
		addButton.setEnabled(false);
		addButton.addListener(SWT.Selection, e -> addReviewer());
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER)
				.applyTo(addButton);

		return composite;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, Dialog.OK, PRText.ReviewerDialog_CloseButton,
				true);
	}

	private void updateAddButtonState() {
		String username = usernameText.getText().trim();
		addButton.setEnabled(!username.isEmpty());
	}

	private void addReviewer() {
		String username = usernameText.getText().trim();
		if (username.isEmpty()) {
			MessageDialog.openWarning(getShell(),
					PRText.ReviewerDialog_Title,
					PRText.ReviewerDialog_EmptyUsername);
			return;
		}

		// Check if already a reviewer
		for (PullRequest.PullRequestParticipant p : reviewers) {
			if (username.equals(p.getUser().getName())) {
				usernameText.setText(""); //$NON-NLS-1$
				usernameText.setFocus();
				return;
			}
		}

		Job job = new Job(PRText.ReviewerDialog_JobAddingReviewer) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					client.addReviewer(pullRequest.getId(), username);

					// Refresh the PR to get updated reviewer list
					PullRequest updatedPr = client
							.getPullRequest(pullRequest.getId());

					Display.getDefault().asyncExec(() -> {
						if (!reviewerListViewer.getControl()
								.isDisposed()) {
							reviewers = new ArrayList<>(
									updatedPr.getReviewers());
							pullRequest.setReviewers(
									updatedPr.getReviewers());
							reviewerListViewer.setInput(reviewers);
							usernameText.setText(""); //$NON-NLS-1$
							usernameText.setFocus();
							if (refreshCallback != null) {
								refreshCallback.run();
							}
						}
					});

					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							PRText.ReviewerDialog_ErrorAddingReviewer, e);
					Display.getDefault().asyncExec(() -> {
						if (!getShell().isDisposed()) {
							MessageDialog.openError(getShell(),
									PRText.ReviewerDialog_Title,
									PRText.ReviewerDialog_ErrorAddingReviewer
											+ ": " + e.getMessage()); //$NON-NLS-1$
						}
					});
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							PRText.ReviewerDialog_ErrorAddingReviewer, e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private void removeSelectedReviewer() {
		StructuredSelection selection =
				(StructuredSelection) reviewerListViewer.getSelection();
		if (selection.isEmpty()) {
			return;
		}

		PullRequest.PullRequestParticipant reviewer =
				(PullRequest.PullRequestParticipant) selection
						.getFirstElement();
		String username = reviewer.getUser().getName();

		Job job = new Job(PRText.ReviewerDialog_JobRemovingReviewer) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					client.removeReviewer(pullRequest.getId(), username);

					// Refresh the PR to get updated reviewer list
					PullRequest updatedPr = client
							.getPullRequest(pullRequest.getId());

					Display.getDefault().asyncExec(() -> {
						if (!reviewerListViewer.getControl()
								.isDisposed()) {
							reviewers = new ArrayList<>(
									updatedPr.getReviewers());
							pullRequest.setReviewers(
									updatedPr.getReviewers());
							reviewerListViewer.setInput(reviewers);
							if (refreshCallback != null) {
								refreshCallback.run();
							}
						}
					});

					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							PRText.ReviewerDialog_ErrorRemovingReviewer,
							e);
					Display.getDefault().asyncExec(() -> {
						if (!getShell().isDisposed()) {
							MessageDialog.openError(getShell(),
									PRText.ReviewerDialog_Title,
									PRText.ReviewerDialog_ErrorRemovingReviewer
											+ ": " + e.getMessage()); //$NON-NLS-1$
						}
					});
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							PRText.ReviewerDialog_ErrorRemovingReviewer,
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}
}
