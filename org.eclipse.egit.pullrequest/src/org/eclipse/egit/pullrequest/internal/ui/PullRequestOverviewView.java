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
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.IPullRequestClient;
import org.eclipse.egit.pullrequest.internal.client.PullRequestClientFactory;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.ui.internal.PreferenceBasedDateFormatter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.ViewPart;

/**
 * View that displays a pull request overview using native SWT widgets.
 * <p>
 * Shows title, state, draft indicator, author, branches, labels,
 * dates, comment count, description, and action buttons.
 */
public class PullRequestOverviewView extends ViewPart {

	/**
	 * The view ID.
	 */
	public static final String VIEW_ID =
			"org.eclipse.egit.pullrequest" //$NON-NLS-1$
					+ ".PullRequestOverviewView"; //$NON-NLS-1$

	private FormToolkit toolkit;

	private ScrolledComposite scrolledComposite;

	private Composite contentComposite;

	private PullRequest currentPullRequest;

	private PreferenceBasedDateFormatter dateFormatter;

	private Font titleFont;

	private Font sectionFont;

	private StyledText descriptionWidget;

	@Override
	public void createPartControl(Composite parent) {
		dateFormatter = PreferenceBasedDateFormatter.create();
		toolkit = new FormToolkit(parent.getDisplay());
		parent.addDisposeListener(e -> {
			toolkit.dispose();
			disposeFonts();
		});

		GridLayoutFactory.fillDefaults().applyTo(parent);

		scrolledComposite = new ScrolledComposite(parent,
				SWT.V_SCROLL | SWT.H_SCROLL);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(scrolledComposite);
		toolkit.adapt(scrolledComposite);

		scrolledComposite.addListener(SWT.Resize, e -> {
			if (contentComposite != null
					&& !contentComposite.isDisposed()) {
				scrolledComposite.setMinSize(
						contentComposite.computeSize(
								scrolledComposite
										.getClientArea()
										.width,
								SWT.DEFAULT));
			}
		});

		showPlaceholder();
	}

	/**
	 * Loads a pull request into the overview view.
	 *
	 * @param pr
	 *            the pull request to display, or {@code null} to
	 *            show the placeholder
	 */
	public void loadPullRequest(PullRequest pr) {
		currentPullRequest = pr;

		if (scrolledComposite == null
				|| scrolledComposite.isDisposed()) {
			return;
		}

		if (pr != null) {
			renderPullRequest(pr);
			setPartName(MessageFormat.format(
					PRText.OverviewView_TitleFormat,
					Long.valueOf(pr.getId())));
		} else {
			showPlaceholder();
			setPartName(PRText.OverviewView_DefaultTitle);
		}
	}

	@Override
	public void setFocus() {
		if (scrolledComposite != null
				&& !scrolledComposite.isDisposed()) {
			scrolledComposite.setFocus();
		}
	}

	@Override
	public void dispose() {
		disposeFonts();
		super.dispose();
	}

	private void disposeFonts() {
		if (titleFont != null && !titleFont.isDisposed()) {
			titleFont.dispose();
			titleFont = null;
		}
		if (sectionFont != null && !sectionFont.isDisposed()) {
			sectionFont.dispose();
			sectionFont = null;
		}
	}

	private void showPlaceholder() {
		disposeContent();
		contentComposite = toolkit.createComposite(
				scrolledComposite);
		GridLayoutFactory.fillDefaults().margins(16, 16)
				.applyTo(contentComposite);

		Label placeholder = toolkit.createLabel(contentComposite,
				PRText.OverviewView_NoPullRequestSelected,
				SWT.WRAP);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(placeholder);

		scrolledComposite.setContent(contentComposite);
		updateScrolledSize();
	}

	private void renderPullRequest(PullRequest pr) {
		disposeContent();
		contentComposite = toolkit.createComposite(
				scrolledComposite);
		GridLayoutFactory.fillDefaults().numColumns(2)
				.margins(16, 16).spacing(8, 4)
				.applyTo(contentComposite);

		renderHeader(pr);
		renderMetadata(pr);
		renderDescription(pr);
		renderActions();

		scrolledComposite.setContent(contentComposite);
		updateScrolledSize();
	}

	private void renderHeader(PullRequest pr) {
		// Title spans both columns
		Label titleLabel = toolkit.createLabel(contentComposite,
				pr.getTitle(), SWT.WRAP);
		titleLabel.setFont(getTitleFont());
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(titleLabel);

		// Badges row spanning both columns
		Composite badgeRow = toolkit.createComposite(
				contentComposite);
		GridLayoutFactory.fillDefaults().numColumns(4)
				.spacing(8, 0).applyTo(badgeRow);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(badgeRow);

		// State badge
		String state = pr.getState() != null
				? pr.getState()
				: PRText.OverviewView_Unknown;
		Label stateBadge = toolkit.createLabel(badgeRow, state,
				SWT.NONE);
		stateBadge.setFont(
				JFaceResources.getFontRegistry().getBold(
						JFaceResources.DEFAULT_FONT));
		applyStateForeground(stateBadge, state);

		// Draft badge
		if (pr.isDraft()) {
			Label draftBadge = toolkit.createLabel(badgeRow,
					PRText.OverviewView_Draft, SWT.NONE);
			draftBadge.setForeground(
					Display.getCurrent().getSystemColor(
							SWT.COLOR_DARK_GRAY));
			draftBadge.setFont(
					JFaceResources.getFontRegistry().getBold(
							JFaceResources.DEFAULT_FONT));
		}

		// PR ID
		String prId = "#" + pr.getId(); //$NON-NLS-1$
		Label idLabel = toolkit.createLabel(badgeRow, prId,
				SWT.NONE);
		idLabel.setForeground(
				Display.getCurrent().getSystemColor(
						SWT.COLOR_DARK_GRAY));

		// Separator spanning both columns
		Label separator = toolkit.createSeparator(
				contentComposite, SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(separator);
	}

	private void renderMetadata(PullRequest pr) {
		// Author
		if (pr.getAuthor() != null
				&& pr.getAuthor().getUser() != null) {
			String authorName = pr.getAuthor().getUser()
					.getDisplayName();
			if (authorName == null) {
				authorName = pr.getAuthor().getUser().getName();
			}
			createMetaRow(PRText.OverviewView_Author,
					authorName);
		}

		// Reviewers
		if (pr.getReviewers() != null
				&& !pr.getReviewers().isEmpty()) {
			String reviewersText = formatReviewers(
					pr.getReviewers());
			createMetaRow(PRText.OverviewView_Reviewers,
					reviewersText);
		} else {
			createMetaRow(PRText.OverviewView_Reviewers,
					PRText.OverviewView_NoReviewers);
		}

		// Branches
		if (pr.getFromRef() != null
				&& pr.getToRef() != null) {
			String branches = pr.getFromRef().getDisplayId()
					+ " " + PRText.OverviewView_BranchArrow //$NON-NLS-1$
					+ " " + pr.getToRef().getDisplayId(); //$NON-NLS-1$
			createMetaRow(PRText.OverviewView_Branches,
					branches);
		}

		// Labels
		if (pr.getLabels() != null
				&& pr.getLabels().length > 0) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < pr.getLabels().length; i++) {
				if (i > 0) {
					sb.append(", "); //$NON-NLS-1$
				}
				sb.append(pr.getLabels()[i]);
			}
			createMetaRow(PRText.OverviewView_Labels,
					sb.toString());
		}

		// Created date
		if (pr.getCreatedDate() != null) {
			createMetaRow(PRText.OverviewView_Created,
					dateFormatter.formatDate(
							pr.getCreatedDate()));
		}

		// Updated date
		if (pr.getUpdatedDate() != null) {
			createMetaRow(PRText.OverviewView_Updated,
					dateFormatter.formatDate(
							pr.getUpdatedDate()));
		}

		// Comment count
		createMetaRow(PRText.OverviewView_Comments,
				String.valueOf(pr.getCommentCount()));

		// Separator
		Label separator = toolkit.createSeparator(
				contentComposite, SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(separator);
	}

	private void renderDescription(PullRequest pr) {
		// Section label spanning both columns
		Label sectionLabel = toolkit.createLabel(
				contentComposite,
				PRText.OverviewView_Description, SWT.NONE);
		sectionLabel.setFont(getSectionFont());
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(sectionLabel);

		String descText = pr.getDescription();
		if (descText == null) {
			descText = ""; //$NON-NLS-1$
		}

		// Always create editable StyledText widget
		descriptionWidget = new StyledText(contentComposite,
				SWT.MULTI | SWT.WRAP | SWT.BORDER);
		descriptionWidget.setText(descText);
		toolkit.adapt(descriptionWidget, true, false);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1)
				.hint(SWT.DEFAULT, 100)
				.applyTo(descriptionWidget);

		// Separator
		Label separator = toolkit.createSeparator(
				contentComposite, SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(separator);
	}

	private void renderActions() {
		Composite buttonRow = toolkit.createComposite(
				contentComposite);
		GridLayoutFactory.fillDefaults().numColumns(5)
				.spacing(8, 0).applyTo(buttonRow);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(buttonRow);

		Button saveDescBtn = toolkit.createButton(buttonRow,
				PRText.OverviewView_SaveDescription, SWT.PUSH);
		saveDescBtn.addListener(SWT.Selection,
				e -> saveDescription());

		Button manageReviewersBtn = toolkit.createButton(buttonRow,
				PRText.ManageReviewers_ActionLabel.replace("&", ""), //$NON-NLS-1$ //$NON-NLS-2$
				SWT.PUSH);
		manageReviewersBtn.addListener(SWT.Selection,
				e -> manageReviewers());

		Button openBrowserBtn = toolkit.createButton(buttonRow,
				PRText.OverviewView_OpenInBrowser, SWT.PUSH);
		openBrowserBtn.addListener(SWT.Selection,
				e -> openInExternalBrowser());

		Button viewFilesBtn = toolkit.createButton(buttonRow,
				PRText.OverviewView_ViewChangedFiles, SWT.PUSH);
		viewFilesBtn.addListener(SWT.Selection,
				e -> activateChangedFilesView());

		Button checkoutBtn = toolkit.createButton(buttonRow,
				PRText.CheckoutBranch_ActionLabel, SWT.PUSH);
		checkoutBtn.addListener(SWT.Selection,
				e -> checkoutSourceBranch());
	}

	private void createMetaRow(String label, String value) {
		Label metaLabel = toolkit.createLabel(contentComposite,
				label, SWT.NONE);
		metaLabel.setFont(
				JFaceResources.getFontRegistry().getBold(
						JFaceResources.DEFAULT_FONT));
		GridDataFactory.fillDefaults()
				.hint(100, SWT.DEFAULT)
				.applyTo(metaLabel);

		Label metaValue = toolkit.createLabel(contentComposite,
				value, SWT.WRAP);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(metaValue);
	}

	private void disposeContent() {
		if (contentComposite != null
				&& !contentComposite.isDisposed()) {
			contentComposite.dispose();
			contentComposite = null;
		}
	}

	private void updateScrolledSize() {
		scrolledComposite.setMinSize(
				contentComposite.computeSize(
						scrolledComposite.getClientArea().width,
						SWT.DEFAULT));
		contentComposite.layout(true, true);
	}

	private Font getTitleFont() {
		if (titleFont == null || titleFont.isDisposed()) {
			Font defaultFont = JFaceResources.getDefaultFont();
			FontData[] fontData = defaultFont.getFontData();
			for (FontData fd : fontData) {
				fd.setHeight(fd.getHeight() + 4);
				fd.setStyle(SWT.BOLD);
			}
			titleFont = new Font(
					Display.getCurrent(), fontData);
		}
		return titleFont;
	}

	private Font getSectionFont() {
		if (sectionFont == null || sectionFont.isDisposed()) {
			Font defaultFont = JFaceResources.getDefaultFont();
			FontData[] fontData = defaultFont.getFontData();
			for (FontData fd : fontData) {
				fd.setHeight(fd.getHeight() + 1);
				fd.setStyle(SWT.BOLD);
			}
			sectionFont = new Font(
					Display.getCurrent(), fontData);
		}
		return sectionFont;
	}

	private void applyStateForeground(Label label,
			String state) {
		Display display = Display.getCurrent();
		if ("OPEN".equals(state)) { //$NON-NLS-1$
			label.setForeground(
					display.getSystemColor(
							SWT.COLOR_DARK_GREEN));
		} else if ("MERGED".equals(state)) { //$NON-NLS-1$
			label.setForeground(
					display.getSystemColor(
							SWT.COLOR_DARK_MAGENTA));
		} else if ("DECLINED".equals(state) //$NON-NLS-1$
				|| "CLOSED".equals(state)) { //$NON-NLS-1$
			label.setForeground(
					display.getSystemColor(
							SWT.COLOR_DARK_RED));
		} else {
			label.setForeground(
					display.getSystemColor(
							SWT.COLOR_DARK_GRAY));
		}
	}

	private void openInExternalBrowser() {
		if (currentPullRequest == null) {
			return;
		}

		String urlString = null;
		if (currentPullRequest.getLinks() != null
				&& currentPullRequest.getLinks()
						.getSelf() != null
				&& currentPullRequest.getLinks()
						.getSelf().length > 0) {
			urlString = currentPullRequest.getLinks()
					.getSelf()[0].getHref();
		}

		if (urlString == null) {
			Activator.logWarning(
					"No URL found for pull request #" //$NON-NLS-1$
							+ currentPullRequest.getId());
			return;
		}

		try {
			URL url = new URL(urlString);
			IWorkbenchBrowserSupport browserSupport =
					PlatformUI.getWorkbench()
							.getBrowserSupport();
			IWebBrowser externalBrowser =
					browserSupport.getExternalBrowser();
			externalBrowser.openURL(url);
		} catch (MalformedURLException e) {
			Activator.logError(
					"Invalid pull request URL: " //$NON-NLS-1$
							+ urlString, e);
		} catch (PartInitException e) {
			Activator.logError(
					"Failed to open external browser", //$NON-NLS-1$
					e);
		}
	}

	private void activateChangedFilesView() {
		try {
			IWorkbenchPage page = getSite()
					.getWorkbenchWindow().getActivePage();
			IWorkbenchPart part = page.showView(
					PullRequestChangedFilesView.VIEW_ID);
			page.activate(part);
		} catch (PartInitException e) {
			Activator.logError(
					"Failed to activate changed files view", //$NON-NLS-1$
					e);
		}
	}

	private void saveDescription() {
		if (currentPullRequest == null || descriptionWidget == null
				|| descriptionWidget.isDisposed()) {
			return;
		}

		String newDescription = descriptionWidget.getText();

		Job job = new Job(PRText.OverviewView_SaveDescriptionJobName) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client = createClient();
					if (client == null) {
						return new Status(IStatus.ERROR,
								Activator.PLUGIN_ID,
								"Pull request provider not configured"); //$NON-NLS-1$
					}

					PullRequest updatedPr = client
							.updatePullRequestDescription(
									currentPullRequest.getId(),
									currentPullRequest.getVersion(),
									newDescription);

					// Update local PR object with new description and
					// version
					Display.getDefault().asyncExec(() -> {
						currentPullRequest
								.setDescription(newDescription);
						currentPullRequest.setVersion(
								updatedPr.getVersion());
					});

					Activator.logInfo(
							PRText.OverviewView_SaveDescriptionSuccess);
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							PRText.OverviewView_SaveDescriptionError,
							e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							PRText.OverviewView_SaveDescriptionError
									+ ": " + e.getMessage(), //$NON-NLS-1$
							e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private IPullRequestClient createClient() {
		return PullRequestClientFactory.createClient();
	}

	private void checkoutSourceBranch() {
		if (currentPullRequest == null) {
			return;
		}

		CheckoutPullRequestBranchJob job = new CheckoutPullRequestBranchJob(
				currentPullRequest,
				getSite().getShell());
		job.schedule();
	}

	private void manageReviewers() {
		if (currentPullRequest == null) {
			return;
		}

		IPullRequestClient client = createClient();
		if (client == null) {
			MessageFormat.format(
					"Pull request provider not configured", //$NON-NLS-1$
					new Object[0]);
			return;
		}

		ManageReviewersAction action = new ManageReviewersAction(
				getSite().getShell());
		action.setPullRequest(currentPullRequest);
		action.setRefreshCallback(() -> refreshView());
		action.run();
	}

	private void refreshView() {
		if (currentPullRequest == null) {
			return;
		}

		// Refresh the current pull request data and re-render
		Job job = new Job("Refreshing pull request") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IPullRequestClient client = createClient();
					if (client == null) {
						return Status.OK_STATUS;
					}

					PullRequest updatedPr = client
							.getPullRequest(currentPullRequest.getId());

					Display.getDefault().asyncExec(() -> {
						if (!scrolledComposite.isDisposed()) {
							loadPullRequest(updatedPr);
						}
					});

					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							"Failed to refresh pull request", e); //$NON-NLS-1$
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							"Failed to refresh pull request", e); //$NON-NLS-1$
				}
			}
		};
		job.setSystem(true);
		job.schedule();
	}

	private String formatReviewers(
			java.util.List<PullRequest.PullRequestParticipant> reviewers) {
		if (reviewers == null || reviewers.isEmpty()) {
			return PRText.OverviewView_NoReviewers;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < reviewers.size(); i++) {
			if (i > 0) {
				sb.append(", "); //$NON-NLS-1$
			}
			PullRequest.PullRequestParticipant reviewer =
					reviewers.get(i);
			String displayName = reviewer.getUser()
					.getDisplayName();
			if (displayName == null) {
				displayName = reviewer.getUser().getName();
			}
			sb.append(displayName);
			if (reviewer.isApproved()) {
				sb.append(PRText.OverviewView_ApprovedSuffix);
			}
		}
		return sb.toString();
	}
}
