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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Paints pull request comment threads directly into the vertical-indent
 * space of a {@link StyledText} widget using a {@link PaintListener}.

 * <p>
 * Clickable regions (Reply, Resolve, Edit, Delete, header) are
 * hit-tested against rectangles computed during the most recent
 * paint pass.
 * </p>
 */
public class CommentPaintRenderer
		implements PaintListener, MouseListener, MouseMoveListener {

	// ---- Layout constants ------------------------------------------------

	private static final int MARGIN_X = 10; // Horizontal margin from the left edge of the editor to the comment bubble
	private static final int PADDING_X = 10; // Internal horizontal padding inside the comment bubble
	private static final int PADDING_Y = 8; // Internal vertical padding inside the comment bubble
	private static final int AVATAR_SIZE = 24;
	private static final int HEADER_SPACING = 6; // Vertical spacing around the header
	private static final int SECTION_GAP = 6; // Vertical gap between different sections within a comment
	private static final int REPLY_INDENT = 16;
	private static final int BORDER_RADIUS = 8;
	private static final int SEPARATOR_HEIGHT = 1;
	private static final int ACTION_BAR_VPAD = 2; // Vertical padding for action bar (Reply/Resolve)

	// ---- Color blending factors for theme-aware colors -------------------

	// How much to lighten/darken base colors for different elements
	// Lower values = more contrast with base background
	private static final float BG_CONTRAST = 0.92f; // Comment background stands out from editor
	private static final float HEADER_FACTOR = 0.88f; // Header more distinct
	private static final float BORDER_FACTOR = 0.55f; // Border highly visible (increased from 0.70)
	private static final float SEPARATOR_FACTOR = 0.82f; // Separator visible but subtle

	private static final SimpleDateFormat DATE_FORMAT =
			new SimpleDateFormat("yyyy-MM-dd HH:mm"); //$NON-NLS-1$

	// ---- Per-line comment data -------------------------------------------

	/**
	 * Data for one comment thread anchored to a line.
	 */
	static final class ThreadData {
		final int lineIndex;
		final List<PullRequestComment> rootComments;
		final boolean allResolved;
		int cachedHeight; // last computed height in pixels

		ThreadData(int lineIndex, List<PullRequestComment> rootComments) {
			this.lineIndex = lineIndex;
			this.rootComments = rootComments;
			boolean resolved = true;
			for (PullRequestComment c : rootComments) {
				if (!"RESOLVED".equals(c.getState())) { //$NON-NLS-1$
					resolved = false;
					break;
				}
			}
			this.allResolved = resolved;
		}
	}

	// ---- Hit-test regions ------------------------------------------------

	private static final int HIT_REPLY = 1;
	private static final int HIT_RESOLVE = 2;
	private static final int HIT_EDIT = 3;
	private static final int HIT_DELETE = 4;
	private static final int HIT_SELECT = 5;

	/**
	 * A clickable region painted during the last paint pass.
	 */
	private static final class HitRegion {
		final Rectangle bounds;
		final int action;
		final PullRequestComment comment;

		HitRegion(Rectangle bounds, int action,
				PullRequestComment comment) {
			this.bounds = bounds;
			this.action = action;
			this.comment = comment;
		}
	}

	// ---- State -----------------------------------------------------------

	private final Map<Integer, ThreadData> threads = new HashMap<>();

	/**
	 * Hit regions computed during the most recent paint pass.
	 * Rebuilt on every {@link #paintControl} call so they are
	 * always in current widget-relative coordinates.
	 */
	private final List<HitRegion> hitRegions = new ArrayList<>();

	/** Cached avatar images keyed by avatar URL. */
	private final Map<String, Image> avatarImages = new HashMap<>();

	private CommentActionHandler actionHandler;
	private String currentUsername;
	private PullRequestProviderType providerType;

	private Font boldFont;
	private Font smallFont;

	// Lazily-created Color objects (modern SWT — no disposal needed)
	private Color bgColor;
	private Color headerBgColor;
	private Color resolvedBgColor;
	private Color resolvedHeaderBgColor;
	private Color authorColor;
	private Color timestampColor;
	private Color bodyColor;
	private Color separatorColor;
	private Color borderColor;
	private Color linkColor;
	private Color linkHoverColor;
	private Color avatarBgColor;
	private Color resolvedBadgeBgColor;
	private Color whiteColor;
	private Color highlightBorderColor;
	private Color actionBarBgColor;

	private boolean colorsInitialised;

	/** Currently hovered hit-region (for link hover color). */
	private HitRegion hoveredRegion;

	/** ID of the currently highlighted comment (for navigation from Comments View). */
	private long highlightedCommentId = -1;

	// ---- Public API ------------------------------------------------------

	/**
	 * Sets the action handler for comment interactions.
	 *
	 * @param handler the handler
	 */
	void setActionHandler(CommentActionHandler handler) {
		this.actionHandler = handler;
	}

	/**
	 * Sets the current user name (for edit/delete permission).
	 *
	 * @param username the current user name
	 */
	void setCurrentUsername(String username) {
		this.currentUsername = username;
	}

	/**
	 * Sets the provider type (controls Resolve link visibility).
	 *
	 * @param type the provider type
	 */
	void setProviderType(PullRequestProviderType type) {
		this.providerType = type;
	}

	/**
	 * Sets the highlighted comment for visual feedback when navigating
	 * from the Comments View.
	 *
	 * @param comment the comment to highlight, or {@code null} to clear
	 */
	void setHighlightedComment(PullRequestComment comment) {
		this.highlightedCommentId = (comment != null) ? comment.getId() : -1;
	}

	/**
	 * Clears the highlighted comment.
	 */
	void clearHighlight() {
		this.highlightedCommentId = -1;
	}

	/**
	 * Registers a comment thread for a given line.
	 *
	 * @param lineIndex    0-based line index
	 * @param rootComments the root comments on that line
	 */
	void addThread(int lineIndex,
			List<PullRequestComment> rootComments) {
		threads.put(Integer.valueOf(lineIndex),
				new ThreadData(lineIndex, rootComments));
	}

	/**
	 * Removes all registered threads.
	 */
	void clearThreads() {
		threads.clear();
		hitRegions.clear();
	}

	/**
	 * Returns the thread data for a line, or {@code null}.
	 *
	 * @param lineIndex 0-based
	 * @return thread data or null
	 */
	ThreadData getThread(int lineIndex) {
		return threads.get(Integer.valueOf(lineIndex));
	}

	/**
	 * Returns an unmodifiable view of all registered threads.
	 *
	 * @return map from 0-based line index to thread data
	 */
	Map<Integer, ThreadData> getThreads() {
		return Collections.unmodifiableMap(threads);
	}

	/**
	 * Computes the pixel height needed for a comment thread on the
	 * given line. The result is suitable for
	 * {@link StyledText#setLineVerticalIndent}.
	 *
	 * @param styledText the widget (used for width and GC)
	 * @param lineIndex  0-based line index
	 * @return height in pixels, or 0 if no thread on that line
	 */
	int computeThreadHeight(StyledText styledText, int lineIndex) {
		ThreadData td = threads.get(Integer.valueOf(lineIndex));
		if (td == null) {
			return 0;
		}
		int width = Math.max(styledText.getClientArea().width - 2 * MARGIN_X, 100);
		GC gc = new GC(styledText);
		try {
			ensureFonts(gc);
			int h = measureThread(gc, td, width);
			td.cachedHeight = h;
			return h;
		} finally {
			gc.dispose();
		}
	}

	// ---- PaintListener ---------------------------------------------------

	@Override
	public void paintControl(PaintEvent e) {
		StyledText st = (StyledText) e.widget;
		if (st.isDisposed() || threads.isEmpty()) {
			return;
		}

		ensureColors(st);
		ensureFonts(e.gc);

		// Rebuild hit regions on every paint
		hitRegions.clear();

		int clientWidth = st.getClientArea().width;
		int contentWidth = clientWidth - 2 * MARGIN_X;
		if (contentWidth < 50) {
			return;
		}

		// Only paint threads whose lines are in the visible range
		Rectangle clip = new Rectangle(e.x, e.y, e.width, e.height);

		for (ThreadData td : threads.values()) {
			int lineIndex = td.lineIndex;
			if (lineIndex < 0 || lineIndex >= st.getLineCount()) {
				continue;
			}

			int indent = st.getLineVerticalIndent(lineIndex);
			if (indent <= 0) {
				continue;
			}

			try {
				int lineOffset = st.getOffsetAtLine(lineIndex);
				Point loc = st.getLocationAtOffset(lineOffset);
				int bubbleY = loc.y - indent + 4;
				int bubbleX = MARGIN_X;

				// Quick clip test
				if (bubbleY + indent < clip.y || bubbleY > clip.y + clip.height) {
					continue;
				}

				paintThread(e.gc, td, bubbleX, bubbleY,
						contentWidth);
			} catch (IllegalArgumentException ex) {
				// Line not available — skip
			}
		}
	}

	// ---- MouseListener / MouseMoveListener --------------------------------

	@Override
	public void mouseDown(MouseEvent e) {
		if (e.button != 1 || actionHandler == null) {
			return;
		}
		for (HitRegion hr : hitRegions) {
			if (hr.bounds.contains(e.x, e.y)) {
				dispatchAction(hr);
				return;
			}
		}
	}

	@Override
	public void mouseUp(MouseEvent e) {
		// not used
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		// not used
	}

	@Override
	public void mouseMove(MouseEvent e) {
		StyledText st = (StyledText) e.widget;
		HitRegion found = null;
		for (HitRegion hr : hitRegions) {
			if (hr.bounds.contains(e.x, e.y)) {
				found = hr;
				break;
			}
		}
		if (found != hoveredRegion) {
			hoveredRegion = found;
			if (found != null) {
				st.setCursor(
						st.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
			} else {
				st.setCursor(null);
			}
			st.redraw();
		}
	}

	// ---- Painting --------------------------------------------------------

	private void paintThread(GC gc, ThreadData td, int x, int y, int width) {
		Color bg = td.allResolved ? resolvedBgColor : bgColor;

		// Outer bubble background + border
		int height = td.cachedHeight > 0
				? td.cachedHeight
				: measureThread(gc, td, width);

		gc.setAntialias(SWT.ON);
		gc.setBackground(bg);
		gc.fillRoundRectangle(x, y, width, height,
				BORDER_RADIUS, BORDER_RADIUS);
		gc.setForeground(borderColor);
		gc.setLineWidth(2);
		gc.drawRoundRectangle(x, y, width - 1, height - 1,
				BORDER_RADIUS, BORDER_RADIUS);
		gc.setLineWidth(1); // Reset for other drawing operations

		int curY = y + PADDING_Y;
		boolean firstRoot = true;

		for (PullRequestComment root : td.rootComments) {
			if (!firstRoot) {
				// Thread separator
				curY += SECTION_GAP;
				gc.setForeground(separatorColor);
				gc.drawLine(x + PADDING_X, curY,
						x + width - PADDING_X, curY);
				curY += SEPARATOR_HEIGHT + SECTION_GAP;
			}

			curY = paintComment(gc, root, td, x, curY, width,
					0, true, firstRoot);
			firstRoot = false;

			List<PullRequestComment> replies = root.getReplies();
			if (replies != null) {
				for (PullRequestComment reply : replies) {
					// Reply separator
//					curY += SECTION_GAP / 2;
//					gc.setForeground(separatorColor);
//					gc.drawLine(x + PADDING_X + REPLY_INDENT,
//							curY,
//							x + width - PADDING_X, curY);
//					curY += SEPARATOR_HEIGHT + SECTION_GAP / 2;

					curY = paintComment(gc, reply, td, x, curY,
							width, REPLY_INDENT, false, false);
				}
			}
		}

		// Action bar separator
//		curY += 4; // Reduced from SECTION_GAP
//		gc.setForeground(separatorColor);
//		gc.drawLine(x + PADDING_X, curY,
//				x + width - PADDING_X, curY);
//		curY += SEPARATOR_HEIGHT + ACTION_BAR_VPAD;

		curY = paintActionBar(gc, td, x, curY, width);
	}

	/**
	 * Paints a single comment (header + body) and returns the
	 * new Y position below it.
	 */
	private int paintComment(GC gc, PullRequestComment comment,
			ThreadData td, int boxX, int startY, int boxWidth,
			int indent, boolean isRoot, boolean isFirstInThread) {
		int x = boxX + PADDING_X + indent;
		int maxTextWidth = boxWidth - 2 * PADDING_X - indent;
		int curY = startY;

		// ---- Header background ----
		Color hdrBg = td.allResolved
				? resolvedHeaderBgColor : headerBgColor;
		int headerHeight = AVATAR_SIZE + 2 * HEADER_SPACING;
		gc.setBackground(hdrBg);
		// For the first root comment, extend header upward to eliminate gap at top of bubble
		int headerY = (isRoot && isFirstInThread) ? (startY - PADDING_Y) : curY;
		int actualHeaderHeight = (isRoot && isFirstInThread) ? (headerHeight + PADDING_Y) : headerHeight;
		gc.fillRectangle(boxX + 1, headerY,
				boxWidth - 2, actualHeaderHeight);

		// ---- Avatar ----
		int avatarX = x;
		int avatarY = curY + HEADER_SPACING;
		String author = comment.getAuthorDisplayName();
		if (author == null || author.isEmpty()) {
			author = comment.getAuthorName();
		}
		if (author == null) {
			author = "Unknown"; //$NON-NLS-1$
		}
		paintAvatar(gc, avatarX, avatarY, author,
				comment.getAuthorAvatarUrl());

		// ---- Author name (bold) ----
		gc.setFont(boldFont);
		gc.setForeground(authorColor);
		int textX = avatarX + AVATAR_SIZE + 6;
		int textY = avatarY
				+ (AVATAR_SIZE - gc.textExtent(author).y) / 2;
		gc.drawString(author, textX, textY, true);

		// ---- Timestamp ----
		String ts = ""; //$NON-NLS-1$
		if (comment.getCreatedDate() != null) {
			synchronized (DATE_FORMAT) {
				ts = DATE_FORMAT.format(comment.getCreatedDate());
			}
		}
		if (!ts.isEmpty()) {
			gc.setFont(smallFont);
			gc.setForeground(timestampColor);
			int afterAuthor = textX
					+ gc.textExtent(author).x + 8;
			gc.drawString(ts, afterAuthor, textY, true);
		}

		// ---- Resolved badge ----
		if (isRoot && "RESOLVED".equals(comment.getState())) { //$NON-NLS-1$
			gc.setFont(smallFont);
			String badge = "\u2713 Resolved"; //$NON-NLS-1$
			Point badgeExt = gc.textExtent(badge);
			int badgeX = boxX + boxWidth - PADDING_X
					- badgeExt.x - 8;
			int badgeY = textY;
			gc.setBackground(resolvedBadgeBgColor);
			gc.setForeground(whiteColor);
			gc.fillRoundRectangle(badgeX - 4, badgeY - 1,
					badgeExt.x + 8, badgeExt.y + 2, 4, 4);
			gc.drawString(badge, badgeX, badgeY, true);
		}

		// ---- Edit / Delete links in header ----
		gc.setFont(smallFont);
		int linkX = boxX + boxWidth - PADDING_X;
		if (canDelete(comment)) {
			String delText = "Delete"; //$NON-NLS-1$
			Point ext = gc.textExtent(delText);
			linkX -= ext.x;
			boolean hovered = isHovered(linkX, textY,
					ext.x, ext.y);
			gc.setForeground(hovered ? linkHoverColor : linkColor);
			gc.drawString(delText, linkX, textY, true);
			hitRegions.add(new HitRegion(
					new Rectangle(linkX, textY, ext.x, ext.y),
					HIT_DELETE, comment));
			linkX -= 10;
		}
		if (canEdit(comment)) {
			String editText = "Edit"; //$NON-NLS-1$
			Point ext = gc.textExtent(editText);
			linkX -= ext.x;
			boolean hovered = isHovered(linkX, textY,
					ext.x, ext.y);
			gc.setForeground(hovered ? linkHoverColor : linkColor);
			gc.drawString(editText, linkX, textY, true);
			hitRegions.add(new HitRegion(
					new Rectangle(linkX, textY, ext.x, ext.y),
					HIT_EDIT, comment));
		}

		// Register header as select region
		hitRegions.add(new HitRegion(
				new Rectangle(boxX, curY, boxWidth, headerHeight),
				HIT_SELECT, comment));

		curY += headerHeight;

		// ---- Body text ----
		String body = comment.getText();
		if (body != null && !body.isEmpty()) {
			curY += SECTION_GAP;
			gc.setFont(smallFont);
			gc.setForeground(bodyColor);
			curY = drawWrappedText(gc, body, x + 2, curY,
					maxTextWidth - 4);
			curY += SECTION_GAP;
		}

		// Draw highlight border if this comment is highlighted
		// (after body so it wraps the full comment)
		if (highlightedCommentId != -1 && comment.getId() == highlightedCommentId) {
			int commentStartY = (isRoot && isFirstInThread) ? (startY - PADDING_Y) : startY;
			int commentEndY = curY;
			gc.setForeground(highlightBorderColor);
			gc.setLineWidth(3);
			gc.drawRoundRectangle(boxX + 2, commentStartY, boxWidth - 4,
					commentEndY - commentStartY,
					BORDER_RADIUS, BORDER_RADIUS);
			gc.setLineWidth(1); // Reset
		}

		return curY;
	}

	/**
	 * Paints the action bar (Reply, Resolve) and returns the
	 * new Y below it.
	 */
	private int paintActionBar(GC gc, ThreadData td, int boxX, int startY, int boxWidth) {
		gc.setFont(smallFont);
		int x = boxX + PADDING_X;
		int curY = startY;

		PullRequestComment rootComment = td.rootComments.isEmpty()
				? null : td.rootComments.get(0);

		// Calculate action bar height
		int actionBarHeight = gc.textExtent("Reply").y + 2 * ACTION_BAR_VPAD; //$NON-NLS-1$

		// Paint action bar background
		gc.setBackground(actionBarBgColor);
		gc.fillRectangle(boxX + 1, curY, boxWidth - 2, actionBarHeight);

		// Add top padding
		curY += ACTION_BAR_VPAD;

		// Reply link
		if (rootComment != null) {
			String replyText = "Reply"; //$NON-NLS-1$
			Point ext = gc.textExtent(replyText);
			boolean hovered = isHovered(x, curY, ext.x, ext.y);
			gc.setForeground(hovered ? linkHoverColor : linkColor);
			gc.drawString(replyText, x, curY, true);
			hitRegions.add(new HitRegion(
					new Rectangle(x, curY, ext.x, ext.y),
					HIT_REPLY, rootComment));
			x += ext.x + 16;
		}

		// Resolve link (Bitbucket only)
		boolean showResolve =
				providerType == PullRequestProviderType.BITBUCKET;
		if (showResolve && rootComment != null) {
			String resolveText = td.allResolved
					? "Reopen" : "Resolve"; //$NON-NLS-1$ //$NON-NLS-2$
			Point ext = gc.textExtent(resolveText);
			boolean hovered = isHovered(x, curY, ext.x, ext.y);
			gc.setForeground(hovered ? linkHoverColor : linkColor);
			gc.drawString(resolveText, x, curY, true);
			hitRegions.add(new HitRegion(
					new Rectangle(x, curY, ext.x, ext.y),
					HIT_RESOLVE, rootComment));
			x += ext.x + 16;
		}

		curY += gc.textExtent("Reply").y + ACTION_BAR_VPAD; //$NON-NLS-1$
		return curY;
	}

	// ---- Avatar rendering -------------------------------------------------

	private void paintAvatar(GC gc, int x, int y,
			String authorName, String avatarUrl) {
		gc.setAntialias(SWT.ON);

		// Try cached image first
		Image img = null;
		if (avatarUrl != null && !avatarUrl.isEmpty()) {
			img = avatarImages.get(avatarUrl);
			if (img == null) {
				// Request async load; paint initials for now
				requestAvatarLoad(avatarUrl, gc);
			}
		}

		if (img != null && !img.isDisposed()) {
			// Circular clip
			org.eclipse.swt.graphics.Path path =
					new org.eclipse.swt.graphics.Path(
							gc.getDevice());
			path.addArc(x, y, AVATAR_SIZE, AVATAR_SIZE, 0, 360);
			gc.setClipping(path);
			gc.drawImage(img, 0, 0, img.getBounds().width,
					img.getBounds().height, x, y,
					AVATAR_SIZE, AVATAR_SIZE);
			gc.setClipping((org.eclipse.swt.graphics.Region) null);
			path.dispose();
		} else {
			// Fallback: colored circle with initials
			gc.setBackground(avatarBgColor);
			gc.fillOval(x, y, AVATAR_SIZE, AVATAR_SIZE);
			gc.setForeground(whiteColor);
			gc.setFont(smallFont);
			String initials = AvatarCanvas.computeInitials(
					authorName);
			Point ext = gc.textExtent(initials);
			gc.drawString(initials,
					x + (AVATAR_SIZE - ext.x) / 2,
					y + (AVATAR_SIZE - ext.y) / 2, true);
		}
	}

	/**
	 * Initiates an async avatar load. When the image arrives the
	 * StyledText is redrawn.
	 */
	private void requestAvatarLoad(String avatarUrl, GC gc) {
		// Only request once per URL
		if (avatarImages.containsKey(avatarUrl)) {
			return;
		}
		// Put a null sentinel so we don't re-request
		avatarImages.put(avatarUrl, null);

		AvatarCache.getInstance().loadAvatar(avatarUrl,
				AVATAR_SIZE, image -> {
					if (image != null) {
						avatarImages.put(avatarUrl, image);
						// Trigger repaint on the UI thread
						// The PaintListener will pick up the image
						// on the next paint cycle automatically.
					}
				});
	}

	// ---- Text measurement / word-wrap ------------------------------------

	/**
	 * Measures the total height of a thread (for
	 * {@code setLineVerticalIndent}).
	 */
	private int measureThread(GC gc, ThreadData td, int width) {
		int threadHeight = PADDING_Y; // top padding

		boolean firstRoot = true; // first comment of each thread has different height
		for (PullRequestComment root : td.rootComments) {
			if (!firstRoot) {
				threadHeight += SECTION_GAP + SEPARATOR_HEIGHT + SECTION_GAP;
			}
			firstRoot = false;

			// measure root comment
			threadHeight += measureComment(gc, root, width, 0);

			// measure all replies
			List<PullRequestComment> replies = root.getReplies();
			if (replies != null) {
				for (PullRequestComment reply : replies) {
					// Reply separators are not painted (commented out
					// in paintThread), so don't measure them
					threadHeight += measureComment(gc, reply, width, REPLY_INDENT);
				}
			}
		}
		// Action bar (separator is not painted, only padding + text)
		gc.setFont(smallFont);
		threadHeight += gc.textExtent("Reply").y + 2 * ACTION_BAR_VPAD; //$NON-NLS-1$
		// Bottom padding so the 2px border stroke is not clipped
		threadHeight += 5;
		return threadHeight;
	}

	/**
	 * Measures the height of a single comment (header + body).
	 */
	private int measureComment(GC gc, PullRequestComment comment,
			int totalWidth, int indent) {
		int maxTextWidth = totalWidth - 2 * PADDING_X - indent;
		int h = 0;

		// Header
		h += AVATAR_SIZE + 2 * HEADER_SPACING;

		// Body
		String body = comment.getText();
		if (body != null && !body.isEmpty()) {
			h += SECTION_GAP;
			gc.setFont(smallFont);
			h += measureWrappedText(gc, body, maxTextWidth - 4);
			h += SECTION_GAP;
		}
		return h;
	}

	/**
	 * Draws word-wrapped text and returns the Y position below the
	 * last line.
	 */
	private int drawWrappedText(GC gc, String text, int x, int startY, int maxWidth) {
		if (maxWidth <= 0) {
			return startY;
		}
		int curY = startY;
		int lineHeight = gc.textExtent("Ay").y; //$NON-NLS-1$

		for (String paragraph : text.split("\n")) { //$NON-NLS-1$
			if (paragraph.isEmpty()) {
				curY += lineHeight;
				continue;
			}
			String[] words = paragraph.split("\\s+"); //$NON-NLS-1$
			StringBuilder line = new StringBuilder();
			for (String word : words) {
				if (line.length() > 0) {
					String test = line + " " + word; //$NON-NLS-1$
					if (gc.textExtent(test).x > maxWidth) {
						gc.drawString(line.toString(), x, curY,
								true);
						curY += lineHeight;
						line = new StringBuilder(word);
					} else {
						line.append(' ').append(word);
					}
				} else {
					line.append(word);
				}
			}
			if (line.length() > 0) {
				gc.drawString(line.toString(), x, curY, true);
				curY += lineHeight;
			}
		}
		return curY;
	}

	/**
	 * Measures the height of word-wrapped text without drawing.
	 */
	private int measureWrappedText(GC gc, String text, int maxWidth) {
		if (maxWidth <= 0) {
			return 0;
		}

		int lineHeight = gc.textExtent("Ay").y; //$NON-NLS-1$
		int totalHeight = 0;
		for (String paragraph : text.split("\n")) { //$NON-NLS-1$
			if (paragraph.isEmpty()) {
				totalHeight += lineHeight;
				continue;
			}
			String[] words = paragraph.split("\\s+"); //$NON-NLS-1$
			StringBuilder line = new StringBuilder();
			for (String word : words) {
				if (line.length() > 0) {
					String test = line + " " + word; //$NON-NLS-1$
					if (gc.textExtent(test).x > maxWidth) {
						totalHeight += lineHeight;
						line = new StringBuilder(word);
					} else {
						line.append(' ').append(word);
					}
				} else {
					line.append(word);
				}
			}
			if (line.length() > 0) {
				totalHeight += lineHeight;
			}
		}
		return totalHeight;
	}

	// ---- Permission helpers -----------------------------------------------

	private boolean canEdit(PullRequestComment comment) {
		if (currentUsername == null || comment == null) {
			return false;
		}
		String authorName = comment.getAuthorName();
		return authorName != null && currentUsername.equals(authorName);
	}

	private boolean canDelete(PullRequestComment comment) {
		if (currentUsername == null || comment == null) {
			return false;
		}
		String authorName = comment.getAuthorName();
		if (authorName == null) {
			return false;
		}
		return currentUsername.equals(authorName)
				|| authorName.contains("[bot]") //$NON-NLS-1$
				|| "Copilot".equals(authorName); //$NON-NLS-1$
	}

	// ---- Hit-testing helpers ----------------------------------------------

	private boolean isHovered(int x, int y, int w, int h) {
		if (hoveredRegion == null) {
			return false;
		}
		Rectangle r = hoveredRegion.bounds;
		// Close-enough overlap check
		return r.x >= x - 2 && r.x <= x + w + 2
				&& r.y >= y - 2 && r.y <= y + h + 2;
	}

	private void dispatchAction(HitRegion hr) {
		if (actionHandler == null || hr.comment == null) {
			return;
		}
		switch (hr.action) {
		case HIT_REPLY:
			actionHandler.onReply(hr.comment);
			break;
		case HIT_RESOLVE:
			actionHandler.onResolve(hr.comment);
			break;
		case HIT_EDIT:
			actionHandler.onEdit(hr.comment);
			break;
		case HIT_DELETE:
			actionHandler.onDelete(hr.comment);
			break;
		case HIT_SELECT:
			actionHandler.onSelect(hr.comment);
			break;
		default:
			break;
		}
	}

	// ---- Resource management ----------------------------------------------

	private void ensureColors(StyledText st) {
		if (colorsInitialised) {
			return;
		}
		colorsInitialised = true;

		// Get Eclipse theme-aware base colors
		Color widgetBg = st.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		Color widgetFg = st.getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color listBg = st.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
		Color listFg = st.getDisplay().getSystemColor(SWT.COLOR_LIST_FOREGROUND);

		// Use blended list background for comment bubbles to stand out from editor
		bgColor = blendColor(st.getDisplay(), listBg, widgetFg, BG_CONTRAST);

		// Header is slightly darker/lighter than background
		headerBgColor = blendColor(st.getDisplay(), listBg, widgetFg, HEADER_FACTOR);

		// Resolved comments get a slight green tint
		resolvedBgColor = blendWithGreen(st.getDisplay(), listBg, 0.05f);
		resolvedHeaderBgColor = blendWithGreen(st.getDisplay(), headerBgColor, 0.08f);

		// Text colors from theme
		authorColor = widgetFg;
		bodyColor = listFg;

		// Timestamp is slightly muted
		timestampColor = blendColor(st.getDisplay(), listFg, listBg, 0.6f);

		// Separators and borders
		separatorColor = blendColor(st.getDisplay(), listBg, widgetFg, SEPARATOR_FACTOR);
		borderColor = blendColor(st.getDisplay(), listBg, widgetFg, BORDER_FACTOR);

		// Links use Eclipse's hyperlink color or fall back to blue
		linkColor = st.getDisplay().getSystemColor(SWT.COLOR_LINK_FOREGROUND);
		linkHoverColor = blendColor(st.getDisplay(), linkColor, widgetFg, 0.8f);

		// Avatar background - use a distinct color
		avatarBgColor = st.getDisplay().getSystemColor(SWT.COLOR_TITLE_BACKGROUND);

		// Resolved badge - green with good contrast
		resolvedBadgeBgColor = new Color(st.getDisplay(), new RGB(34, 197, 94));
		whiteColor = st.getDisplay().getSystemColor(SWT.COLOR_WHITE);

		// Highlight border for selected comments - use link color for consistency
		highlightBorderColor = linkColor;

		// Action bar background - slightly different from header for visual separation
		actionBarBgColor = blendColor(st.getDisplay(), listBg, widgetFg, 0.95f);
	}

	/**
	 * Blends two colors together with a given factor.
	 * Factor 1.0 = fully base color, 0.0 = fully blend color.
	 */
	private Color blendColor(org.eclipse.swt.widgets.Display display,
			Color base, Color blend, float factor) {
		RGB baseRGB = base.getRGB();
		RGB blendRGB = blend.getRGB();
		int r = (int) (baseRGB.red * factor + blendRGB.red * (1 - factor));
		int g = (int) (baseRGB.green * factor + blendRGB.green * (1 - factor));
		int b = (int) (baseRGB.blue * factor + blendRGB.blue * (1 - factor));
		return new Color(display, new RGB(r, g, b));
	}

	/**
	 * Adds a subtle green tint to a color for resolved comments.
	 */
	private Color blendWithGreen(org.eclipse.swt.widgets.Display display,
			Color base, float greenAmount) {
		RGB rgb = base.getRGB();
		// Boost green channel slightly
		int r = rgb.red;
		int g = Math.min(255, (int) (rgb.green + greenAmount * 100));
		int b = rgb.blue;
		return new Color(display, new RGB(r, g, b));
	}

	private void ensureFonts(GC gc) {
		if (boldFont != null) {
			return;
		}
		Font defaultFont = JFaceResources.getDefaultFont();
		FontData[] fd = defaultFont.getFontData();
		for (FontData f : fd) {
			f.setStyle(SWT.BOLD);
		}
		boldFont = new Font(gc.getDevice(), fd);

		fd = defaultFont.getFontData();
		for (FontData f : fd) {
			f.setHeight(Math.max(f.getHeight() - 1, 7));
		}
		smallFont = new Font(gc.getDevice(), fd);
	}

	/**
	 * Disposes resources created by this renderer. Call when the
	 * compare editor is closed.
	 */
	void dispose() {
		threads.clear();
		hitRegions.clear();
		if (boldFont != null && !boldFont.isDisposed()) {
			boldFont.dispose();
			boldFont = null;
		}
		if (smallFont != null && !smallFont.isDisposed()) {
			smallFont.dispose();
			smallFont = null;
		}

		// Dispose dynamically created colors (from blending)
		// System colors (from getSystemColor) don't need disposal
		disposeColorIfNotSystem(headerBgColor);
		disposeColorIfNotSystem(resolvedBgColor);
		disposeColorIfNotSystem(resolvedHeaderBgColor);
		disposeColorIfNotSystem(timestampColor);
		disposeColorIfNotSystem(separatorColor);
		disposeColorIfNotSystem(borderColor);
		disposeColorIfNotSystem(linkHoverColor);
		disposeColorIfNotSystem(resolvedBadgeBgColor);
		disposeColorIfNotSystem(actionBarBgColor);

		// Avatar images are owned by AvatarCache — do not dispose
		avatarImages.clear();
		colorsInitialised = false;
	}

	/**
	 * Disposes a color only if it's not a system color.
	 * System colors are shared and managed by the Display.
	 */
	private void disposeColorIfNotSystem(Color color) {
		if (color != null && !color.isDisposed()) {
			// Check if it's a system color by comparing to known system colors
			// System colors return true for equals() with getSystemColor()
			try {
				color.dispose();
			} catch (IllegalArgumentException e) {
				// System color - ignore
			}
		}
	}
}
