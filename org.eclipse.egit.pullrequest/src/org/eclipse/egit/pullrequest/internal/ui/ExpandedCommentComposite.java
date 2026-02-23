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

import java.text.SimpleDateFormat;
import java.util.List;

import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

/**
 * A real SWT {@link Composite} that displays a full pull request comment
 * thread inline in the diff viewer. Created on demand when the user clicks a
 * comment icon in the {@link CommentRulerColumn}, and disposed when the user
 * collapses it or clicks a different comment.
 *
 * <p>
 * Shows the root comment and all replies with author, timestamp, and full
 * body text using native SWT word wrap. Provides Reply, Resolve, and
 * Collapse action links at the bottom.
 * </p>
 *
 * <p>
 * Only one {@code ExpandedCommentComposite} is visible at a time per viewer
 * side. The owning {@link CommentOverlayInstaller} manages creation
 * and disposal.
 * </p>
 */
public class ExpandedCommentComposite extends Composite {

	/**
	 * Callback interface for actions triggered from the expanded comment.
	 */
	public interface CommentActionHandler {

		/**
		 * Called when the user clicks the Reply link.
		 *
		 * @param comment
		 *            the root comment being replied to
		 */
		void onReply(PullRequestComment comment);

	/**
	 * Called when the user clicks the Resolve/Reopen link.
	 * Only shown for Bitbucket provider.
	 *
	 * @param comment
	 *            the root comment being resolved/reopened
	 */
	void onResolve(PullRequestComment comment);

	/**
	 * Called when the user clicks the Delete link.
	 *
	 * @param comment
	 *            the comment being deleted
	 */
	void onDelete(PullRequestComment comment);

	/**
	 * Called when the user clicks the Edit link.
	 *
	 * @param comment
	 *            the comment being edited
	 */
	void onEdit(PullRequestComment comment);

	/**
	 * Called when the user clicks the Collapse link.
	 *
	 * @param line
	 *            the line number to collapse
	 */
	void onCollapse(int line);

	/**
	 * Called when the user selects a comment (clicks on its
	 * header).
	 *
	 * @param comment
	 *            the selected comment
	 */
	void onSelect(PullRequestComment comment);
	}

	private static final SimpleDateFormat DATE_FORMAT =
			new SimpleDateFormat("yyyy-MM-dd HH:mm"); //$NON-NLS-1$

	// Modern color palette - light blue theme for normal comments
	private static final RGB COMMENT_BG_RGB = new RGB(248, 250, 252);

	private static final RGB COMMENT_HEADER_BG_RGB = new RGB(241, 245, 249);

	private static final RGB RESOLVED_BG_RGB = new RGB(240, 253, 244);

	private static final RGB RESOLVED_HEADER_BG_RGB = new RGB(220, 252, 231);

	private static final RGB AUTHOR_RGB = new RGB(15, 23, 42);

	private static final RGB TIMESTAMP_RGB = new RGB(100, 116, 139);

	private static final RGB BODY_TEXT_RGB = new RGB(51, 65, 85);

	private static final RGB SEPARATOR_RGB = new RGB(226, 232, 240);

	private static final RGB BORDER_RGB = new RGB(203, 213, 225);

	private static final RGB LINK_RGB = new RGB(37, 99, 235);

	private static final RGB LINK_HOVER_RGB = new RGB(29, 78, 216);

	private static final RGB AVATAR_BG_RGB = new RGB(59, 130, 246);

	private static final RGB RESOLVED_BADGE_BG_RGB = new RGB(34, 197, 94);

	private static final RGB RESOLVED_BADGE_FG_RGB = new RGB(255, 255, 255);

	private static final int AVATAR_SIZE = 28;

	private static final int BORDER_RADIUS = 8;

	private final int line;

	private Color bgColor;

	private Color headerBgColor;

	private Color resolvedBgColor;

	private Color resolvedHeaderBgColor;

	private Color authorColor;

	private Color timestampColor;

	private Color bodyTextColor;

	private Color separatorColor;

	private Color borderColor;

	private Color linkColor;

	private Color linkHoverColor;

	private Color avatarBgColor;

	private Color resolvedBadgeBgColor;

	private Color resolvedBadgeFgColor;

	private Font boldFont;

	private Font smallFont;

	private String currentUsername;

	private boolean showResolve;

	/**
	 * Creates a new expanded comment composite showing the given thread.
	 *
	 * @param parent
	 *            the parent composite (typically the StyledText's parent)
	 * @param style
	 *            the SWT style bits
	 * @param oneLine
	 *            the 1-based line number this thread is anchored to
	 * @param comments
	 *            the root-level comments on this line (each may have
	 *            replies)
	 * @param handler
	 *            the action handler for Reply/Resolve/Collapse, or
	 *            {@code null}
	 * @param currentUsername
	 *            the current user's username, or {@code null}
	 * @param providerType
	 *            the pull request provider type, or {@code null}
	 */
	public ExpandedCommentComposite(Composite parent, int style,
			int oneLine, List<PullRequestComment> comments,
			CommentActionHandler handler, String currentUsername,
			PullRequestProviderType providerType) {
		super(parent, style | SWT.DOUBLE_BUFFERED);
		this.line = oneLine;
		this.currentUsername = currentUsername;
		this.showResolve = (providerType == PullRequestProviderType.BITBUCKET);

		ensureColors();
		ensureFonts();

		boolean resolved = true;
		for (PullRequestComment c : comments) {
			if (!"RESOLVED".equals(c.getState())) { //$NON-NLS-1$
				resolved = false;
				break;
			}
		}
		final boolean allResolved = resolved;

		setBackground(allResolved ? resolvedBgColor : bgColor);

		// Add custom border painting for rounded corners effect
		addPaintListener(e -> paintCustomBorder(e.gc, allResolved));

		GridLayoutFactory.fillDefaults().margins(1, 1).spacing(0, 0)
				.applyTo(this);

		// Inner container with padding (to account for border)
		Composite innerContainer = new Composite(this, SWT.NONE);
		innerContainer.setBackground(getBackground());
		GridLayoutFactory.fillDefaults().margins(10, 8).spacing(0, 0)
				.applyTo(innerContainer);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(innerContainer);

		// Render each root comment and its replies
		boolean first = true;
		for (PullRequestComment rootComment : comments) {
			if (!first) {
				addThreadSeparator(innerContainer);
			}
			first = false;

			renderComment(innerContainer, rootComment, handler, allResolved,
					true);

			List<PullRequestComment> replies = rootComment.getReplies();
			if (replies != null) {
				for (PullRequestComment reply : replies) {
					addReplySeparator(innerContainer);
					renderReply(innerContainer, reply, handler, allResolved);
				}
			}
		}

		// Action links at the bottom
		addActionBar(innerContainer, comments, handler);
	}

	/**
	 * Returns the 1-based line number this composite is anchored to.
	 *
	 * @return the line number
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Fills the background and paints a rounded border around the
	 * composite. The explicit background fill is necessary because
	 * the parent {@code StyledText} widget paints its own white
	 * background underneath child controls.
	 *
	 * @param gc
	 *            the graphics context
	 * @param resolved
	 *            whether the thread is resolved
	 */
	private void paintCustomBorder(GC gc, boolean resolved) {
		Rectangle bounds = getClientArea();
		gc.setAntialias(SWT.ON);

		// Fill the background explicitly to prevent the parent
		// StyledText's white background from bleeding through
		gc.setBackground(getBackground());
		gc.fillRoundRectangle(0, 0, bounds.width, bounds.height,
				BORDER_RADIUS, BORDER_RADIUS);

		gc.setForeground(borderColor);
		gc.setLineWidth(1);
		gc.drawRoundRectangle(0, 0, bounds.width - 1,
				bounds.height - 1, BORDER_RADIUS, BORDER_RADIUS);
	}

	private void renderComment(Composite parent,
			PullRequestComment comment, CommentActionHandler handler,
			boolean isResolved, boolean isRootComment) {
		Composite commentArea = new Composite(parent, SWT.NONE);
		commentArea.setBackground(parent.getBackground());
		GridLayoutFactory.fillDefaults().spacing(0, 4)
				.applyTo(commentArea);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(commentArea);

	// Header row with avatar, author, timestamp, badge, and delete link
	Composite headerRow = new Composite(commentArea, SWT.NONE);
	Color headerBg = isResolved ? resolvedHeaderBgColor : headerBgColor;
	headerRow.setBackground(headerBg);
	GridLayoutFactory.fillDefaults().numColumns(6).spacing(8, 0)
			.margins(8, 6).applyTo(headerRow);
	GridDataFactory.fillDefaults().grab(true, false)
			.applyTo(headerRow);

	// Avatar with initials
	String author = comment.getAuthorDisplayName();
	if (author == null || author.isEmpty()) {
		author = comment.getAuthorName();
	}
	if (author == null) {
		author = "Unknown"; //$NON-NLS-1$
	}

	Canvas avatarCanvas = createAvatarCanvas(headerRow, author);
	GridDataFactory.fillDefaults()
			.hint(AVATAR_SIZE, AVATAR_SIZE)
			.applyTo(avatarCanvas);

	// Author name (bold)
	Label authorLabel = new Label(headerRow, SWT.NONE);
	authorLabel.setText(author);
	authorLabel.setFont(boldFont);
	authorLabel.setForeground(authorColor);
	authorLabel.setBackground(headerBg);
	GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.CENTER)
			.applyTo(authorLabel);

	// Timestamp
	String timestamp = ""; //$NON-NLS-1$
	if (comment.getCreatedDate() != null) {
		synchronized (DATE_FORMAT) {
			timestamp = DATE_FORMAT.format(comment.getCreatedDate());
		}
	}

	Label tsLabel = new Label(headerRow, SWT.NONE);
	tsLabel.setText(timestamp);
	tsLabel.setFont(smallFont);
	tsLabel.setForeground(timestampColor);
	tsLabel.setBackground(headerBg);
	GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.CENTER)
			.grab(true, false).applyTo(tsLabel);

	// Resolved badge (only for root comments that are resolved)
	if (isRootComment && "RESOLVED".equals(comment.getState())) { //$NON-NLS-1$
		Label badge = new Label(headerRow, SWT.NONE);
		badge.setText(" ✓ Resolved "); //$NON-NLS-1$
		badge.setFont(smallFont);
		badge.setForeground(resolvedBadgeFgColor);
		badge.setBackground(resolvedBadgeBgColor);
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER)
				.applyTo(badge);
	} else {
		// Empty placeholder to maintain layout
		Label placeholder = new Label(headerRow, SWT.NONE);
		placeholder.setBackground(headerBg);
		GridDataFactory.fillDefaults().applyTo(placeholder);
	}

	// Edit link (only if user can edit this comment)
	if (handler != null && canEdit(comment)) {
		Link editLink = new Link(headerRow, SWT.NONE);
		editLink.setText("<a>Edit</a>"); //$NON-NLS-1$
		editLink.setBackground(headerBg);
		editLink.setFont(smallFont);
		editLink.setForeground(linkColor);
		editLink.addListener(SWT.MouseEnter,
				e -> editLink.setForeground(linkHoverColor));
		editLink.addListener(SWT.MouseExit,
				e -> editLink.setForeground(linkColor));
		editLink.addListener(SWT.Selection,
				e -> handler.onEdit(comment));
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER)
				.applyTo(editLink);
	} else {
		// Empty placeholder to maintain layout
		Label placeholder = new Label(headerRow, SWT.NONE);
		placeholder.setBackground(headerBg);
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER)
				.applyTo(placeholder);
	}

	// Delete link (only if user can delete this comment)
	if (handler != null && canDelete(comment)) {
		Link deleteLink = new Link(headerRow, SWT.NONE);
		deleteLink.setText("<a>Delete</a>"); //$NON-NLS-1$
		deleteLink.setBackground(headerBg);
		deleteLink.setFont(smallFont);
		deleteLink.setForeground(linkColor);
		deleteLink.addListener(SWT.MouseEnter,
				e -> deleteLink.setForeground(linkHoverColor));
		deleteLink.addListener(SWT.MouseExit,
				e -> deleteLink.setForeground(linkColor));
		deleteLink.addListener(SWT.Selection,
				e -> handler.onDelete(comment));
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER)
				.applyTo(deleteLink);
	} else {
		// Empty placeholder to maintain layout
		Label placeholder = new Label(headerRow, SWT.NONE);
		placeholder.setBackground(headerBg);
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER)
				.applyTo(placeholder);
	}

	// Make header clickable
	if (handler != null) {
		for (Control ctrl : new Control[] { headerRow, avatarCanvas,
				authorLabel, tsLabel }) {
			ctrl.setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
			ctrl.addListener(SWT.MouseDown, e -> handler.onSelect(comment));
		}
	}

	// Body text with improved styling
	String bodyText = comment.getText();
	if (bodyText != null && !bodyText.isEmpty()) {
		Composite bodyContainer = new Composite(commentArea, SWT.NONE);
		bodyContainer.setBackground(parent.getBackground());
		GridLayoutFactory.fillDefaults().margins(8, 6)
				.applyTo(bodyContainer);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(bodyContainer);

		Label bodyLabel = new Label(bodyContainer, SWT.WRAP);
		bodyLabel.setText(bodyText);
		bodyLabel.setForeground(bodyTextColor);
		bodyLabel.setBackground(parent.getBackground());
		GridDataFactory.fillDefaults().grab(true, false)
				.hint(400, SWT.DEFAULT).applyTo(bodyLabel);

		if (handler != null) {
			bodyContainer.setCursor(
					getDisplay().getSystemCursor(SWT.CURSOR_HAND));
			bodyContainer.addListener(SWT.MouseDown,
					e -> handler.onSelect(comment));
			bodyLabel.setCursor(
					getDisplay().getSystemCursor(SWT.CURSOR_HAND));
			bodyLabel.addListener(SWT.MouseDown,
					e -> handler.onSelect(comment));
		}
	}
	}

	/**
	 * Creates an avatar canvas showing the user's initials.
	 *
	 * @param parent
	 *            the parent composite
	 * @param authorName
	 *            the author's display name
	 * @return the canvas widget
	 */
	private Canvas createAvatarCanvas(Composite parent, String authorName) {
		Canvas canvas = new Canvas(parent, SWT.DOUBLE_BUFFERED);
		canvas.setBackground(parent.getBackground());

		String initials = getInitials(authorName);

		canvas.addPaintListener(e -> {
			GC gc = e.gc;
			gc.setAntialias(SWT.ON);

			// Draw circular background
			gc.setBackground(avatarBgColor);
			gc.fillOval(0, 0, AVATAR_SIZE - 1, AVATAR_SIZE - 1);

			// Draw initials
			gc.setForeground(
					getDisplay().getSystemColor(SWT.COLOR_WHITE));
			Font avatarFont = smallFont;
			gc.setFont(avatarFont);

			org.eclipse.swt.graphics.Point textExtent = gc
					.textExtent(initials);
			int x = (AVATAR_SIZE - textExtent.x) / 2;
			int y = (AVATAR_SIZE - textExtent.y) / 2;
			gc.drawText(initials, x, y, true);
		});

		return canvas;
	}

	/**
	 * Extracts initials from a name (up to 2 characters).
	 *
	 * @param name
	 *            the full name
	 * @return the initials (1-2 uppercase letters)
	 */
	private String getInitials(String name) {
		if (name == null || name.isEmpty()) {
			return "?"; //$NON-NLS-1$
		}

		String[] parts = name.trim().split("\\s+"); //$NON-NLS-1$
		if (parts.length >= 2) {
			return (String.valueOf(parts[0].charAt(0))
					+ parts[parts.length - 1].charAt(0)).toUpperCase();
		} else if (parts.length == 1 && parts[0].length() >= 1) {
			return String.valueOf(parts[0].charAt(0)).toUpperCase();
		}
		return "?"; //$NON-NLS-1$
	}

	/**
	 * Renders a reply comment indented under its parent.
	 *
	 * @param parent
	 *            the parent composite
	 * @param reply
	 *            the reply comment
	 * @param handler
	 *            the action handler, or {@code null}
	 * @param isResolved
	 *            whether the thread is resolved
	 */
	private void renderReply(Composite parent, PullRequestComment reply,
			CommentActionHandler handler, boolean isResolved) {
		Composite indent = new Composite(parent, SWT.NONE);
		indent.setBackground(parent.getBackground());
		GridLayoutFactory.fillDefaults().margins(16, 0)
				.applyTo(indent);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(indent);

		renderComment(indent, reply, handler, isResolved, false);
	}

	/**
	 * Adds a visual separator between thread roots.
	 *
	 * @param parent
	 *            the parent composite
	 */
	private void addThreadSeparator(Composite parent) {
		Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		sep.setForeground(separatorColor);
		GridDataFactory.fillDefaults().grab(true, false)
				.hint(SWT.DEFAULT, 1)
				.indent(0, 8).applyTo(sep);
		GridData gd = (GridData) sep.getLayoutData();
		gd.verticalIndent = 8;
	}

	/**
	 * Adds a lighter separator between a comment and its reply.
	 *
	 * @param parent
	 *            the parent composite
	 */
	private void addReplySeparator(Composite parent) {
		Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		sep.setForeground(separatorColor);
		GridDataFactory.fillDefaults().grab(true, false)
				.hint(SWT.DEFAULT, 1)
				.indent(16, 6).applyTo(sep);
	}

	/**
	 * Checks if the current user can delete the given comment.
	 * A comment can be deleted if:
	 * <ul>
	 * <li>The current user is the comment author, OR</li>
	 * <li>The comment is authored by a bot (author name contains
	 * "[bot]"), OR</li>
	 * <li>The comment is authored by Copilot (author name is
	 * "Copilot")</li>
	 * </ul>
	 *
	 * @param comment
	 *            the comment to check
	 * @return {@code true} if the comment can be deleted
	 */
	private boolean canDelete(PullRequestComment comment) {
		if (currentUsername == null || comment == null) {
			return false;
		}
		String authorName = comment.getAuthorName();
		if (authorName == null) {
			return false;
		}
		// Debug logging
		boolean isOwn = currentUsername.equals(authorName);
		boolean isBot = authorName.contains("[bot]"); //$NON-NLS-1$
		boolean isCopilot = "Copilot".equals(authorName); //$NON-NLS-1$
		boolean canDelete = isOwn || isBot || isCopilot;

		Activator.logInfo(String.format(
				"[canDelete] currentUser='%s', author='%s', isOwn=%s, isBot=%s, isCopilot=%s, canDelete=%s", //$NON-NLS-1$
				currentUsername, authorName, isOwn, isBot, isCopilot,
				canDelete));

		// Allow deletion of own comments, bot comments, or Copilot comments
		return canDelete;
	}

	/**
	 * Checks if the current user can edit the given comment.
	 * A comment can be edited only if the current user is the comment
	 * author (not bot or Copilot comments).
	 *
	 * @param comment
	 *            the comment to check
	 * @return {@code true} if the comment can be edited
	 */
	private boolean canEdit(PullRequestComment comment) {
		if (currentUsername == null || comment == null) {
			return false;
		}
		String authorName = comment.getAuthorName();
		if (authorName == null) {
			return false;
		}
		// Allow editing only own comments (not bot or Copilot comments)
		return currentUsername.equals(authorName);
	}

	/**
	 * Adds the action bar with Reply, Resolve (Bitbucket only), and
	 * Collapse links.
	 *
	 * @param parent
	 *            the parent composite
	 * @param comments
	 *            the root-level comments
	 * @param handler
	 *            the action handler, or {@code null}
	 */
	private void addActionBar(Composite parent,
			List<PullRequestComment> comments,
			CommentActionHandler handler) {
		// Small separator before action bar
		Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		sep.setForeground(separatorColor);
		GridDataFactory.fillDefaults().grab(true, false)
				.hint(SWT.DEFAULT, 1)
				.indent(0, 6).applyTo(sep);

	// Dynamically determine column count: 2 for GitHub, 3 for Bitbucket
	int columnCount = showResolve ? 3 : 2;
	Composite actionBar = new Composite(parent, SWT.NONE);
	actionBar.setBackground(parent.getBackground());
	GridLayoutFactory.fillDefaults().numColumns(columnCount).spacing(16, 0)
			.margins(8, 6).applyTo(actionBar);
	GridDataFactory.fillDefaults().grab(true, false)
			.applyTo(actionBar);

	// Reply link with hover effect
	Link replyLink = createStyledLink(actionBar, "Reply"); //$NON-NLS-1$
	if (handler != null && !comments.isEmpty()) {
		PullRequestComment rootComment = comments.get(0);
		replyLink.addListener(SWT.Selection,
				e -> handler.onReply(rootComment));
	}

		// Resolve/Reopen link (Bitbucket only)
		if (showResolve) {
			boolean allResolved = true;
			for (PullRequestComment c : comments) {
				if (!"RESOLVED".equals(c.getState())) { //$NON-NLS-1$
					allResolved = false;
					break;
				}
			}

			String resolveText = allResolved
					? "Reopen" : "Resolve"; //$NON-NLS-1$ //$NON-NLS-2$
			Link resolveLink = createStyledLink(actionBar,
					resolveText);
			if (handler != null && !comments.isEmpty()) {
				PullRequestComment rootComment = comments.get(0);
				resolveLink.addListener(SWT.Selection,
						e -> handler.onResolve(rootComment));
			}
		}

		// Collapse link
		Link collapseLink = createStyledLink(actionBar, "Collapse"); //$NON-NLS-1$
		if (handler != null) {
			collapseLink.addListener(SWT.Selection,
					e -> handler.onCollapse(line));
		}
	}

	/**
	 * Creates a styled link with hover effects.
	 *
	 * @param parent
	 *            the parent composite
	 * @param text
	 *            the link text
	 * @return the created link
	 */
	private Link createStyledLink(Composite parent, String text) {
		Link link = new Link(parent, SWT.NONE);
		link.setText("<a>" + text + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$
		link.setBackground(parent.getBackground());
		link.setFont(smallFont);

		// Add hover effect
		link.addListener(SWT.MouseEnter, e -> link.setForeground(linkHoverColor));
		link.addListener(SWT.MouseExit, e -> link.setForeground(linkColor));

		return link;
	}

	// ---- Resource management ----------------------------------------------

	/**
	 * Creates the color resources needed for rendering.
	 */
	private void ensureColors() {
		bgColor = new Color(getDisplay(), COMMENT_BG_RGB);
		headerBgColor = new Color(getDisplay(), COMMENT_HEADER_BG_RGB);
		resolvedBgColor = new Color(getDisplay(), RESOLVED_BG_RGB);
		resolvedHeaderBgColor = new Color(getDisplay(), RESOLVED_HEADER_BG_RGB);
		authorColor = new Color(getDisplay(), AUTHOR_RGB);
		timestampColor = new Color(getDisplay(), TIMESTAMP_RGB);
		bodyTextColor = new Color(getDisplay(), BODY_TEXT_RGB);
		separatorColor = new Color(getDisplay(), SEPARATOR_RGB);
		borderColor = new Color(getDisplay(), BORDER_RGB);
		linkColor = new Color(getDisplay(), LINK_RGB);
		linkHoverColor = new Color(getDisplay(), LINK_HOVER_RGB);
		avatarBgColor = new Color(getDisplay(), AVATAR_BG_RGB);
		resolvedBadgeBgColor = new Color(getDisplay(), RESOLVED_BADGE_BG_RGB);
		resolvedBadgeFgColor = new Color(getDisplay(), RESOLVED_BADGE_FG_RGB);
	}

	/**
	 * Creates the font resources needed for rendering.
	 */
	private void ensureFonts() {
		// Bold font for author names
		Font defaultFont = JFaceResources.getDefaultFont();
		FontData[] fontData = defaultFont.getFontData();
		for (FontData fd : fontData) {
			fd.setStyle(SWT.BOLD);
		}
		boldFont = new Font(getDisplay(), fontData);

		// Smaller font for timestamps and links
		fontData = defaultFont.getFontData();
		for (FontData fd : fontData) {
			fd.setHeight(fd.getHeight() - 1);
		}
		smallFont = new Font(getDisplay(), fontData);
	}

	@Override
	public void dispose() {
		// Dispose custom fonts
		if (boldFont != null && !boldFont.isDisposed()) {
			boldFont.dispose();
			boldFont = null;
		}
		if (smallFont != null && !smallFont.isDisposed()) {
			smallFont.dispose();
			smallFont = null;
		}

		// On modern SWT, Color objects do not require explicit disposal
		bgColor = null;
		headerBgColor = null;
		resolvedBgColor = null;
		resolvedHeaderBgColor = null;
		authorColor = null;
		timestampColor = null;
		bodyTextColor = null;
		separatorColor = null;
		borderColor = null;
		linkColor = null;
		linkHoverColor = null;
		avatarBgColor = null;
		resolvedBadgeBgColor = null;
		resolvedBadgeFgColor = null;

		super.dispose();
	}
}
