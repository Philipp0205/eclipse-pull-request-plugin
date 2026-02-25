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
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.EditorPart;

/**
 * Editor that displays a pull request overview using native SWT widgets.
 * <p>
 * Shows title, state, draft indicator, author, branches, labels,
 * dates, comment count, description, and action buttons.
 */
public class PullRequestOverviewView extends EditorPart {

	/**
	 * The editor ID.
	 */
	public static final String EDITOR_ID =
			"org.eclipse.egit.pullrequest" //$NON-NLS-1$
					+ ".PullRequestOverviewEditor"; //$NON-NLS-1$

	private FormToolkit toolkit;

	private ScrolledComposite scrolledComposite;

	private Composite contentComposite;

	private PullRequest currentPullRequest;

	private PreferenceBasedDateFormatter dateFormatter;

	private Font titleFont;

	private Font sectionFont;

	private StyledText descriptionWidget;

	private IPullRequestClient client;

	@Override
	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		if (!(input instanceof PullRequestOverviewEditorInput)) {
			throw new PartInitException(
					"Invalid input: must be PullRequestOverviewEditorInput"); //$NON-NLS-1$
		}
		setSite(site);
		setInput(input);

		PullRequestOverviewEditorInput prInput =
				(PullRequestOverviewEditorInput) input;
		currentPullRequest = prInput.getPullRequest();

		setPartName(input.getName());
	}

	@Override
	public void createPartControl(Composite parent) {
		dateFormatter = PreferenceBasedDateFormatter.create();
		client = createClient();
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

		// Load the pull request if input is provided
		if (currentPullRequest != null) {
			renderPullRequest(currentPullRequest);
			setPartName(MessageFormat.format(
					PRText.OverviewView_TitleFormat,
					Long.valueOf(currentPullRequest.getId())));
		} else {
			showPlaceholder();
			setPartName(PRText.OverviewView_DefaultTitle);
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		// Not implemented - overview is read-only except for description
		// which has its own save button
	}

	@Override
	public void doSaveAs() {
		// Not supported
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
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
				.margins(16, 16).spacing(16, 4)
				.applyTo(contentComposite);

		// Left content area
		Composite leftComposite = toolkit.createComposite(
				contentComposite);
		GridLayoutFactory.fillDefaults().numColumns(2)
				.spacing(8, 4).applyTo(leftComposite);
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(leftComposite);

		// Right sidebar for reviewers
		Composite rightSidebar = toolkit.createComposite(
				contentComposite);
		GridLayoutFactory.fillDefaults().numColumns(1)
				.spacing(0, 8).applyTo(rightSidebar);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING)
				.hint(180, SWT.DEFAULT)
				.applyTo(rightSidebar);

		// Render content in left composite
		renderHeader(pr, leftComposite);
		renderMetadata(pr, leftComposite);
		renderDescription(pr, leftComposite);
		renderActions(leftComposite);

		// Render reviewer sidebar on the right
		renderReviewerSidebar(pr, rightSidebar);

		scrolledComposite.setContent(contentComposite);
		updateScrolledSize();
	}

	private void renderHeader(PullRequest pr, Composite parent) {
		// Title spans both columns
		Label titleLabel = toolkit.createLabel(parent,
				pr.getTitle(), SWT.WRAP);
		titleLabel.setFont(getTitleFont());
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(titleLabel);

		// Badges row spanning both columns
		Composite badgeRow = toolkit.createComposite(
				parent);
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
				parent, SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(separator);
	}

	private void renderMetadata(PullRequest pr, Composite parent) {
		// Author
		if (pr.getAuthor() != null
				&& pr.getAuthor().getUser() != null) {
			String authorName = pr.getAuthor().getUser()
					.getDisplayName();
			if (authorName == null) {
				authorName = pr.getAuthor().getUser().getName();
			}
			createMetaRow(parent, PRText.OverviewView_Author,
					authorName);
		}

		// Reviewers
		if (pr.getReviewers() != null
				&& !pr.getReviewers().isEmpty()) {
			String reviewersText = formatReviewers(
					pr.getReviewers());
			createMetaRow(parent, PRText.OverviewView_Reviewers,
					reviewersText);
		} else {
			createMetaRow(parent, PRText.OverviewView_Reviewers,
					PRText.OverviewView_NoReviewers);
		}

		// Branches
		if (pr.getFromRef() != null
				&& pr.getToRef() != null) {
			String branches = pr.getFromRef().getDisplayId()
					+ " " + PRText.OverviewView_BranchArrow //$NON-NLS-1$
					+ " " + pr.getToRef().getDisplayId(); //$NON-NLS-1$
			createMetaRow(parent, PRText.OverviewView_Branches,
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
			createMetaRow(parent, PRText.OverviewView_Labels,
					sb.toString());
		}

		// Created date
		if (pr.getCreatedDate() != null) {
			createMetaRow(parent, PRText.OverviewView_Created,
					dateFormatter.formatDate(
							pr.getCreatedDate()));
		}

		// Updated date
		if (pr.getUpdatedDate() != null) {
			createMetaRow(parent, PRText.OverviewView_Updated,
					dateFormatter.formatDate(
							pr.getUpdatedDate()));
		}

		// Comment count
		createMetaRow(parent, PRText.OverviewView_Comments,
				String.valueOf(pr.getCommentCount()));

		// Separator
		Label separator = toolkit.createSeparator(
				parent, SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(separator);
	}

	private void renderDescription(PullRequest pr, Composite parent) {
		// Section label spanning both columns
		Label sectionLabel = toolkit.createLabel(
				parent,
				PRText.OverviewView_Description, SWT.NONE);
		sectionLabel.setFont(getSectionFont());
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(sectionLabel);

		String descText = pr.getDescription();
		if (descText == null) {
			descText = ""; //$NON-NLS-1$
		}

		// Always create editable StyledText widget
		descriptionWidget = new StyledText(parent,
				SWT.MULTI | SWT.WRAP | SWT.BORDER);
		descriptionWidget.setText(descText);
		toolkit.adapt(descriptionWidget, true, false);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1)
				.hint(SWT.DEFAULT, 100)
				.applyTo(descriptionWidget);

		// Separator
		Label separator = toolkit.createSeparator(
				parent, SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().grab(true, false)
				.span(2, 1).applyTo(separator);
	}

	private void renderActions(Composite parent) {
		Composite buttonRow = toolkit.createComposite(
				parent);
		GridLayoutFactory.fillDefaults().numColumns(8)
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

		Button addMyselfBtn = toolkit.createButton(buttonRow,
				PRText.OverviewView_AddMyselfAsReviewer, SWT.PUSH);
		addMyselfBtn.addListener(SWT.Selection,
				e -> addMyselfAsReviewer());

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

		if (client != null
				&& client.getCapabilities()
						.supportsReviewSubmission()) {
			Button approveBtn = toolkit.createButton(buttonRow,
					PRText.SubmitReview_ApproveAction, SWT.PUSH);
			approveBtn.setToolTipText(
					PRText.SubmitReview_ApproveTooltip);
			approveBtn.addListener(SWT.Selection,
					e -> submitReview("APPROVE")); //$NON-NLS-1$

			Button requestChangesBtn = toolkit.createButton(
					buttonRow,
					PRText.SubmitReview_RequestChangesAction,
					SWT.PUSH);
			requestChangesBtn.setToolTipText(
					PRText.SubmitReview_RequestChangesTooltip);
			requestChangesBtn.addListener(SWT.Selection,
					e -> submitReview("REQUEST_CHANGES")); //$NON-NLS-1$
		}
	}

	private void createMetaRow(Composite parent, String label,
			String value) {
		Label metaLabel = toolkit.createLabel(parent,
				label, SWT.NONE);
		metaLabel.setFont(
				JFaceResources.getFontRegistry().getBold(
						JFaceResources.DEFAULT_FONT));
		GridDataFactory.fillDefaults()
				.hint(100, SWT.DEFAULT)
				.applyTo(metaLabel);

		Label metaValue = toolkit.createLabel(parent,
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
							currentPullRequest = updatedPr;
							renderPullRequest(updatedPr);
							setPartName(MessageFormat.format(
									PRText.OverviewView_TitleFormat,
									Long.valueOf(updatedPr.getId())));
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

	private void renderReviewerSidebar(PullRequest pr,
			Composite parent) {
		// Section title
		Label sectionTitle = toolkit.createLabel(parent,
				PRText.OverviewView_ReviewersSectionTitle,
				SWT.NONE);
		sectionTitle.setFont(getSectionFont());
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(sectionTitle);

		// Show reviewers or "no reviewers" message
		if (pr.getReviewers() != null
				&& !pr.getReviewers().isEmpty()) {
			for (PullRequest.PullRequestParticipant reviewer :
					pr.getReviewers()) {
				createAvatarCircle(parent, reviewer);
			}
		} else {
			Label noReviewers = toolkit.createLabel(parent,
					PRText.OverviewView_NoReviewers,
					SWT.WRAP);
			GridDataFactory.fillDefaults().grab(true, false)
					.applyTo(noReviewers);
		}

		// Add separator
		Label separator = toolkit.createSeparator(parent,
				SWT.HORIZONTAL);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(separator);

		// Add "Add Myself" button
		Button addMyselfBtn = toolkit.createButton(parent,
				"+", SWT.PUSH); //$NON-NLS-1$
		addMyselfBtn.setToolTipText(
				PRText.OverviewView_AddReviewerTooltip);
		addMyselfBtn.addListener(SWT.Selection,
				e -> addMyselfAsReviewer());
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER)
				.applyTo(addMyselfBtn);
	}

	private void createAvatarCircle(Composite parent,
			PullRequest.PullRequestParticipant reviewer) {
		Composite avatarContainer = toolkit.createComposite(
				parent);
		GridLayoutFactory.fillDefaults().numColumns(1)
				.spacing(0, 2).applyTo(avatarContainer);
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER)
				.applyTo(avatarContainer);

		// Avatar canvas
		Canvas canvas = new Canvas(avatarContainer, SWT.NONE);
		int avatarSize = 40;
		GridDataFactory.fillDefaults()
				.hint(avatarSize, avatarSize)
				.applyTo(canvas);

		String displayName = reviewer.getUser().getDisplayName();
		if (displayName == null) {
			displayName = reviewer.getUser().getName();
		}
		String initials = getInitials(displayName);
		Color bgColor = getAvatarColor(displayName);
		boolean approved = reviewer.isApproved();

		canvas.addPaintListener(
				(PaintEvent e) -> paintAvatar(e, initials, bgColor,
						approved, avatarSize));

		// Name label below avatar
		Label nameLabel = toolkit.createLabel(avatarContainer,
				displayName, SWT.CENTER | SWT.WRAP);
		GridDataFactory.fillDefaults().grab(true, false)
				.align(SWT.CENTER, SWT.CENTER)
				.hint(avatarSize + 20, SWT.DEFAULT)
				.applyTo(nameLabel);
	}

	private void paintAvatar(PaintEvent e, String initials,
			Color bgColor, boolean approved, int size) {
		GC gc = e.gc;
		gc.setAntialias(SWT.ON);

		// Draw background circle
		gc.setBackground(bgColor);
		gc.fillOval(0, 0, size, size);

		// Draw approved indicator (green border)
		if (approved) {
			gc.setForeground(
					Display.getCurrent().getSystemColor(
							SWT.COLOR_DARK_GREEN));
			gc.setLineWidth(3);
			gc.drawOval(1, 1, size - 2, size - 2);
		}

		// Draw initials
		gc.setForeground(
				Display.getCurrent().getSystemColor(
						SWT.COLOR_WHITE));
		Point textExtent = gc.textExtent(initials);
		int x = (size - textExtent.x) / 2;
		int y = (size - textExtent.y) / 2;
		gc.drawText(initials, x, y, true);
	}

	private String getInitials(String name) {
		if (name == null || name.isEmpty()) {
			return "?"; //$NON-NLS-1$
		}

		String[] parts = name.trim().split("\\s+"); //$NON-NLS-1$
		if (parts.length == 0) {
			return "?"; //$NON-NLS-1$
		}

		if (parts.length == 1) {
			// Single name - take first 2 chars
			return parts[0].substring(0,
					Math.min(2, parts[0].length()))
					.toUpperCase();
		}

		// Multiple parts - take first char of first two parts
		return (parts[0].substring(0, 1)
				+ parts[1].substring(0, 1)).toUpperCase();
	}

	private Color getAvatarColor(String name) {
		// Color palette for avatars (avoiding green which is for
		// approved)
		RGB[] palette = new RGB[] {
				new RGB(52, 152, 219), // Blue
				new RGB(155, 89, 182), // Purple
				new RGB(230, 126, 34), // Orange
				new RGB(231, 76, 60), // Red
				new RGB(241, 196, 15), // Yellow
				new RGB(26, 188, 156), // Teal
				new RGB(149, 165, 166), // Gray
				new RGB(192, 57, 43) // Dark red
		};

		int hash = Math.abs(name.hashCode());
		int index = hash % palette.length;
		RGB rgb = palette[index];

		return new Color(Display.getCurrent(), rgb);
	}

	private void addMyselfAsReviewer() {
		if (currentPullRequest == null) {
			return;
		}

		AddMyselfAsReviewerAction action = new AddMyselfAsReviewerAction(
				getSite().getShell());
		action.setPullRequest(currentPullRequest);
		action.setRefreshCallback(() -> refreshView());
		action.run();
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

	private void submitReview(String event) {
		if (currentPullRequest == null || client == null) {
			return;
		}

		// For REQUEST_CHANGES, prompt for a body message
		String body = null;
		if ("REQUEST_CHANGES".equals(event)) { //$NON-NLS-1$
			MultiLineInputDialog dialog = new MultiLineInputDialog(
					getSite().getShell(),
					PRText.SubmitReview_DialogTitle,
					PRText.SubmitReview_DialogMessage,
					""); //$NON-NLS-1$
			if (dialog.open() != org.eclipse.jface.window.Window.OK) {
				return;
			}
			body = dialog.getValue();
		}

		final String reviewBody = body;
		Job job = new Job(PRText.SubmitReview_JobName) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					client.submitReview(
							currentPullRequest.getId(),
							event, reviewBody);
					Display.getDefault().asyncExec(
							() -> refreshView());
					return Status.OK_STATUS;
				} catch (IOException e) {
					Activator.logError(
							PRText.SubmitReview_Error, e);
					return new Status(IStatus.ERROR,
							Activator.PLUGIN_ID,
							PRText.SubmitReview_Error, e);
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}
}
