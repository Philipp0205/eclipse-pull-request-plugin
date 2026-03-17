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
package org.eclipse.egit.pullrequest.internal.ui.overview;

import java.text.MessageFormat;
import java.util.Date;

import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.model.TimelineEvent;
import org.eclipse.egit.pullrequest.internal.model.TimelineEventType;
import org.eclipse.egit.pullrequest.internal.ui.AvatarCanvas;
import org.eclipse.egit.pullrequest.internal.ui.UIUtils;
import org.eclipse.egit.ui.internal.PreferenceBasedDateFormatter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * A reusable SWT composite that renders a single timeline event in a pull
 * request activity feed.
 * <p>
 * Layout structure:
 * <pre>
 * +-----------------------------------------------------------+
 * | [Avatar] UserName  action description       2 hours ago   |
 * |          +-----------------------------------------------+ |
 * |          | Comment body / review body / commit message    | |
 * |          | (only shown when message is non-null)          | |
 * |          +-----------------------------------------------+ |
 * +-----------------------------------------------------------+
 * </pre>
 */
public class TimelineEventComposite extends Composite {

	private static final int AVATAR_SIZE = 24;

	private final FormToolkit toolkit;

	private final TimelineEvent event;

	private final PreferenceBasedDateFormatter dateFormatter;

	/**
	 * Creates a new timeline event composite.
	 *
	 * @param parent
	 *            the parent composite
	 * @param toolkit
	 *            the form toolkit for consistent Eclipse Forms styling
	 * @param event
	 *            the timeline event to display
	 * @param dateFormatter
	 *            the date formatter for timestamps
	 */
	public TimelineEventComposite(Composite parent, FormToolkit toolkit,
			TimelineEvent event, PreferenceBasedDateFormatter dateFormatter) {
		super(parent, SWT.NONE);
		this.toolkit = toolkit;
		this.event = event;
		this.dateFormatter = dateFormatter;

		toolkit.adapt(this);
		createContent();
	}

	private void createContent() {
		// Two-column layout: avatar on left, content on right
		GridLayoutFactory.fillDefaults().numColumns(2).margins(4, 4)
				.spacing(8, 0).applyTo(this);

		// Left column: Avatar
		createAvatar();

		// Right column: Header + optional message body
		Composite rightColumn = toolkit.createComposite(this);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(rightColumn);
		GridLayoutFactory.fillDefaults().margins(0, 0).applyTo(rightColumn);

		createHeader(rightColumn);

		// Show message body if present
		if (event.getMessage() != null && !event.getMessage().isEmpty()) {
			createMessageBody(rightColumn);
		}

		// Draw border around content events (those with message body)
		if (event.getMessage() != null && !event.getMessage().isEmpty()) {
			addPaintListener(e -> {
				GC gc = e.gc;
				gc.setAntialias(SWT.ON);
				Color borderColor = getBorderColor();
				gc.setForeground(borderColor);
				gc.drawRoundRectangle(0, 0, getSize().x - 1,
						getSize().y - 1, 6, 6);
			});
		}
	}

	private void createAvatar() {
		Composite avatarContainer = toolkit.createComposite(this);
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.BEGINNING)
				.hint(AVATAR_SIZE, AVATAR_SIZE).applyTo(avatarContainer);
		GridLayoutFactory.fillDefaults().margins(0, 0)
				.applyTo(avatarContainer);

		String displayName = event.getActorName();
		if (displayName == null || displayName.isEmpty()) {
			displayName = event.getActorUsername();
		}
		if (displayName == null) {
			displayName = "Unknown"; //$NON-NLS-1$
		}

		Color bgColor = AvatarCanvas.colorForName(displayName);
		String avatarUrl = event.getActorAvatarUrl();

		AvatarCanvas canvas = new AvatarCanvas(avatarContainer, AVATAR_SIZE,
				displayName, bgColor, avatarUrl);
		GridDataFactory.fillDefaults().hint(AVATAR_SIZE, AVATAR_SIZE)
				.applyTo(canvas);

		// Dispose the color when the canvas is disposed
		canvas.addDisposeListener(e -> bgColor.dispose());
	}

	private void createHeader(Composite parent) {
		Composite headerRow = toolkit.createComposite(parent);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(headerRow);
		GridLayoutFactory.fillDefaults().numColumns(3).margins(0, 0)
				.spacing(5, 0).applyTo(headerRow);

		// Username (bold)
		Label usernameLabel = toolkit.createLabel(headerRow, getActorDisplay(),
				SWT.NONE);
		usernameLabel.setFont(
				org.eclipse.jface.resource.JFaceResources.getFontRegistry()
						.getBold(org.eclipse.jface.resource.JFaceResources.DEFAULT_FONT));
		GridDataFactory.fillDefaults().applyTo(usernameLabel);

		// Action description (normal text)
		Label actionLabel = toolkit.createLabel(headerRow,
				getActionDescription(), SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(actionLabel);

		// Timestamp (gray, right-aligned)
		Label timestampLabel = toolkit.createLabel(headerRow,
				getRelativeTimestamp(), SWT.NONE);
		Color gray = getGrayColor();
		timestampLabel.setForeground(gray);
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER)
				.applyTo(timestampLabel);
	}

	private void createMessageBody(Composite parent) {
		StyledText messageWidget = new StyledText(parent,
				SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
		toolkit.adapt(messageWidget, false, false);
		messageWidget.setText(event.getMessage());
		messageWidget.setMargins(4, 4, 4, 4);
		messageWidget.setCaret(null); // Hide cursor

		// Style the background
		Display display = getDisplay();
		boolean dark = UIUtils.isDarkTheme(display);
		if (dark) {
			messageWidget.setBackground(
					display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		} else {
			messageWidget.setBackground(
					display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		}

		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(messageWidget);
	}

	private String getActorDisplay() {
		if (event.getActorName() != null && !event.getActorName().isEmpty()) {
			return event.getActorName();
		}
		if (event.getActorUsername() != null
				&& !event.getActorUsername().isEmpty()) {
			return event.getActorUsername();
		}
		return PRText.TimelineEvent_UnknownActor;
	}

	private String getActionDescription() {
		TimelineEventType type = event.getType();
		if (type == null) {
			return ""; //$NON-NLS-1$
		}

		switch (type) {
		case OPENED:
			return PRText.TimelineEvent_Opened;
		case CLOSED:
			return PRText.TimelineEvent_Closed;
		case MERGED:
			return PRText.TimelineEvent_Merged;
		case REOPENED:
			return PRText.TimelineEvent_Reopened;
		case COMMENTED:
			return PRText.TimelineEvent_Commented;
		case REVIEW_COMMENT:
			return getReviewCommentDescription();
		case COMMITTED:
			return getCommitDescription();
		case REVIEWED:
			return getReviewDescription();
		case DRAFT:
			return PRText.TimelineEvent_Draft;
		case READY_FOR_REVIEW:
			return PRText.TimelineEvent_ReadyForReview;
		default:
			return ""; //$NON-NLS-1$
		}
	}

	private String getCommitDescription() {
		// For commits, we could show "pushed 1 commit" or just "committed"
		// The message body will show the commit message
		String sha = event.getMetadata().get("sha"); //$NON-NLS-1$
		if (sha != null && sha.length() > 7) {
			// Show shortened SHA
			return MessageFormat.format(PRText.TimelineEvent_Committed,
					sha.substring(0, 7));
		}
		return PRText.TimelineEvent_Committed_Generic;
	}

	private String getReviewDescription() {
		String state = event.getMetadata().get("state"); //$NON-NLS-1$
		if (state == null) {
			return PRText.TimelineEvent_Reviewed_Generic;
		}

		// Handle GitHub and Bitbucket review states
		if ("APPROVED".equalsIgnoreCase(state) //$NON-NLS-1$
				|| "approved".equalsIgnoreCase(state)) { //$NON-NLS-1$
			return PRText.TimelineEvent_Reviewed_Approved;
		} else if ("CHANGES_REQUESTED".equalsIgnoreCase(state) //$NON-NLS-1$
				|| "REQUEST_CHANGES".equalsIgnoreCase(state) //$NON-NLS-1$
				|| "changes_requested".equalsIgnoreCase(state)) { //$NON-NLS-1$
			return PRText.TimelineEvent_Reviewed_ChangesRequested;
		} else if ("UNAPPROVED".equalsIgnoreCase(state)) { //$NON-NLS-1$
			return PRText.TimelineEvent_Reviewed_Unapproved;
		}

		return PRText.TimelineEvent_Reviewed_Generic;
	}

	/**
	 * Gets the action description for a review comment event, including the
	 * filename if available in metadata.
	 *
	 * @return the formatted action description
	 */
	private String getReviewCommentDescription() {
		String path = event.getMetadata().get("path"); //$NON-NLS-1$
		if (path != null && !path.isEmpty()) {
			// Extract just the filename from the full path
			int lastSlash = path.lastIndexOf('/');
			String filename = lastSlash >= 0 ? path.substring(lastSlash + 1)
					: path;
			return MessageFormat
					.format(PRText.TimelineEvent_ReviewCommentOnFile,
							filename);
		}
		return PRText.TimelineEvent_ReviewComment;
	}

	private String getRelativeTimestamp() {
		if (event.getCreatedDate() == null) {
			return ""; //$NON-NLS-1$
		}
		return dateFormatter.formatDate(event.getCreatedDate());
	}

	private Color getGrayColor() {
		Display display = getDisplay();
		if (UIUtils.isDarkTheme(display)) {
			return display.getSystemColor(SWT.COLOR_GRAY);
		}
		return display.getSystemColor(SWT.COLOR_DARK_GRAY);
	}

	/**
	 * Gets the border color for content event composites.
	 *
	 * @return the border color
	 */
	private Color getBorderColor() {
		Display display = getDisplay();
		return display
				.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
	}
}
