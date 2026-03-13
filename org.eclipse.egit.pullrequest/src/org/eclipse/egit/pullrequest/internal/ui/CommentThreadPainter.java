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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;

/**
 * Handles all GC-based painting operations for comment threads.
 *
 * <p>
 * This class paints comment bubbles, headers, avatars, bodies, and
 * action bars using SWT graphics primitives. It returns hit regions
 * for interactive elements that the caller can register with
 * {@link CommentHitTestManager}.
 * </p>
 */
final class CommentThreadPainter {

	// ---- Layout constants ------------------------------------------------

	private static final int PADDING_X = 10;
	private static final int PADDING_Y = 8;
	private static final int AVATAR_SIZE = 24;
	private static final int HEADER_SPACING = 6;
	private static final int SECTION_GAP = 6;
	private static final int REPLY_INDENT = 16;
	private static final int BORDER_RADIUS = 8;
	private static final int SEPARATOR_HEIGHT = 1;
	private static final int ACTION_BAR_VPAD = 2;

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm"); //$NON-NLS-1$

	// ---- Dependencies ----------------------------------------------------

	private final CommentThemeColors colors;
	private final CommentThreadMeasurer measurer;
	private final CommentHitTestManager hitTest;

	/** Cached avatar images keyed by avatar URL. */
	private final Map<String, Image> avatarImages = new HashMap<>();

	/**
	 * The StyledText widget to repaint after async avatar loads.
	 * Updated on each paint pass.
	 */
	private StyledText styledText;

	// ---- State -----------------------------------------------------------

	private String currentUsername;
	private PullRequestProviderType providerType;
	private long highlightedCommentId = -1;

	// ---- Constructor -----------------------------------------------------

	/**
	 * Creates a new comment thread painter.
	 *
	 * @param colors
	 *            theme colors and fonts
	 * @param measurer
	 *            text measurer for layout caching
	 * @param hitTest
	 *            hit-test manager for registering clickable regions
	 */
	CommentThreadPainter(CommentThemeColors colors,
			CommentThreadMeasurer measurer,
			CommentHitTestManager hitTest) {
		this.colors = colors;
		this.measurer = measurer;
		this.hitTest = hitTest;
	}

	// ---- Configuration ---------------------------------------------------

	/**
	 * Sets the StyledText widget used to trigger a repaint after
	 * async avatar images are loaded.
	 *
	 * @param st
	 *            the StyledText widget
	 */
	void setStyledText(StyledText st) {
		this.styledText = st;
	}

	/**
	 * Sets the current user name (for edit/delete permission).
	 *
	 * @param username
	 *            the current user name
	 */
	void setCurrentUsername(String username) {
		this.currentUsername = username;
	}

	/**
	 * Sets the provider type (controls Resolve link visibility).
	 *
	 * @param type
	 *            the provider type
	 */
	void setProviderType(PullRequestProviderType type) {
		this.providerType = type;
	}

	/**
	 * Sets the highlighted comment for visual feedback.
	 *
	 * @param commentId
	 *            the comment ID to highlight, or -1 to clear
	 */
	void setHighlightedCommentId(long commentId) {
		this.highlightedCommentId = commentId;
	}

	// ---- Painting --------------------------------------------------------

	/**
	 * Paints a complete comment thread at the specified position.
	 *
	 * @param gc
	 *            the graphics context
	 * @param td
	 *            the thread data to paint
	 * @param x
	 *            the left edge of the bubble
	 * @param y
	 *            the top edge of the bubble
	 * @param width
	 *            the width of the bubble
	 */
	void paintThread(GC gc, CommentPaintRenderer.ThreadData td, int x, int y, int width) {
		Color bg = td.allResolved ? colors.getResolvedBgColor()
				: colors.getBgColor();

		// Outer bubble background + border
		int height = td.cachedHeight > 0 ? td.cachedHeight
				: measurer.measureThread(gc, td, width);

		gc.setAntialias(SWT.ON);
		gc.setBackground(bg);
		gc.fillRoundRectangle(x, y, width, height, BORDER_RADIUS, BORDER_RADIUS);
		gc.setForeground(colors.getBorderColor());
		gc.setLineWidth(2);
		gc.drawRoundRectangle(x, y, width - 1, height - 1, BORDER_RADIUS, BORDER_RADIUS);
		gc.setLineWidth(1); // Reset for other drawing operations

		int curY = y + PADDING_Y;
		boolean firstRoot = true;

		// Track comment bounds for highlight border
		Map<Long, Rectangle> commentBounds = new HashMap<>();

		for (PullRequestComment root : td.rootComments) {
			if (!firstRoot) {
				// Thread separator
				curY += SECTION_GAP;
				gc.setForeground(colors.getSeparatorColor());
				gc.drawLine(x + PADDING_X, curY, x + width - PADDING_X,
						curY);
				curY += SEPARATOR_HEIGHT + SECTION_GAP;
			}

			int commentStartY = firstRoot ? y : curY;
			curY = paintComment(gc, root, td, x, curY, width, 0, true, firstRoot);
			commentBounds.put(root.getId(), new Rectangle(x, commentStartY, width, curY - commentStartY));
			firstRoot = false;

			List<PullRequestComment> replies = root.getReplies();
			if (replies != null) {
				for (PullRequestComment reply : replies) {
					int replyStartY = curY;
					curY = paintComment(gc, reply, td, x, curY, width,
							REPLY_INDENT, false, false);
					commentBounds.put(reply.getId(), new Rectangle(x, replyStartY, width, curY - replyStartY));
				}
			}
		}

		curY = paintActionBar(gc, td, x, curY, width);

		// Draw highlight border AFTER all content is painted to prevent overlay
		if (highlightedCommentId != -1 && commentBounds.containsKey(highlightedCommentId)) {
			Rectangle bounds = commentBounds.get(highlightedCommentId);
			gc.setForeground(colors.getHighlightBorderColor());
			gc.setLineWidth(3);
			gc.drawRoundRectangle(bounds.x, bounds.y, bounds.width - 1,
					bounds.height, BORDER_RADIUS, BORDER_RADIUS);
			gc.setLineWidth(1); // Reset
		}
	}

	/**
	 * Paints a single comment (header + body) and returns the new Y
	 * position below it.
	 *
	 * @param gc
	 *            the graphics context
	 * @param comment
	 *            the comment to paint
	 * @param td
	 *            the thread data (for resolved state)
	 * @param boxX
	 *            the left edge of the bubble
	 * @param startY
	 *            the top Y position for this comment
	 * @param boxWidth
	 *            the width of the bubble
	 * @param indent
	 *            horizontal indent for replies
	 * @param isRoot
	 *            true if this is a root comment
	 * @param isFirstInThread
	 *            true if this is the first root comment in the thread
	 * @return the new Y position below this comment
	 */
	private int paintComment(GC gc, PullRequestComment comment,
			CommentPaintRenderer.ThreadData td, int boxX,
			int startY, int boxWidth, int indent, boolean isRoot,
			boolean isFirstInThread) {

		int x = boxX + PADDING_X + indent;
		int maxTextWidth = boxWidth - 2 * PADDING_X - indent;
		int curY = startY;

		// Paint header
		curY = paintCommentHeader(gc, comment, td, boxX, curY, boxWidth,
				x, isRoot, isFirstInThread);

		// Paint body
		curY = paintCommentBody(gc, comment, x, curY, maxTextWidth);

		return curY;
	}

	/**
	 * Paints the header section of a comment (background, avatar,
	 * author, timestamp, badges, and action links).
	 *
	 * @param gc
	 *            the graphics context
	 * @param comment
	 *            the comment to paint
	 * @param td
	 *            the thread data (for resolved state)
	 * @param boxX
	 *            the left edge of the bubble
	 * @param startY
	 *            the top Y position for the header
	 * @param boxWidth
	 *            the width of the bubble
	 * @param contentX
	 *            the X position for content (with padding/indent)
	 * @param isRoot
	 *            true if this is a root comment
	 * @param isFirstInThread
	 *            true if this is the first root comment in the thread
	 * @return the new Y position below the header
	 */
	private int paintCommentHeader(GC gc, PullRequestComment comment,
			CommentPaintRenderer.ThreadData td, int boxX,
			int startY, int boxWidth, int contentX, boolean isRoot,
			boolean isFirstInThread) {

		// ---- Header background ----
		Color hdrBg = td.allResolved ? colors.getResolvedHeaderBgColor()
				: colors.getHeaderBgColor();
		int headerHeight = AVATAR_SIZE + 2 * HEADER_SPACING;
		gc.setBackground(hdrBg);
		// For the first root comment, extend header upward to eliminate
		// gap at top of bubble
		int headerY = (isRoot && isFirstInThread) ? (startY - PADDING_Y)
				: startY;
		int actualHeaderHeight = (isRoot && isFirstInThread)
				? (headerHeight + PADDING_Y) : headerHeight;
		gc.fillRectangle(boxX + 1, headerY, boxWidth - 2,
				actualHeaderHeight);

		// ---- Avatar ----
		int avatarX = contentX;
		int avatarY = startY + HEADER_SPACING;
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
		gc.setFont(colors.getBoldFont());
		gc.setForeground(colors.getAuthorColor());
		int textX = avatarX + AVATAR_SIZE + 6;
		int textY = avatarY + (AVATAR_SIZE - gc.textExtent(author).y) / 2;
		gc.drawString(author, textX, textY, true);

		// ---- Timestamp ----
		String timeStamp = ""; //$NON-NLS-1$
		if (comment.getCreatedDate() != null) {
			synchronized (DATE_FORMAT) { timeStamp = DATE_FORMAT.format(comment.getCreatedDate());
			}
		}
		if (!timeStamp.isEmpty()) {
			gc.setFont(colors.getSmallFont());
			gc.setForeground(colors.getTimestampColor());
			int afterAuthor = textX + gc.textExtent(author).x + 20;
			gc.drawString(timeStamp, afterAuthor, textY, true);
		}

		// ---- Resolved badge ----
		if (isRoot && "RESOLVED".equals(comment.getState())) { //$NON-NLS-1$
			gc.setFont(colors.getSmallFont());
			String badge = "\u2713 Resolved"; //$NON-NLS-1$
			Point badgeExt = gc.textExtent(badge);
			int badgeX = boxX + boxWidth - PADDING_X - badgeExt.x - 8;
			int badgeY = textY;
			gc.setBackground(colors.getResolvedBadgeBgColor());
			gc.setForeground(colors.getWhiteColor());
			gc.fillRoundRectangle(badgeX - 4, badgeY - 1,
					badgeExt.x + 8, badgeExt.y + 2, 4, 4);
			gc.drawString(badge, badgeX, badgeY, true);
		}

		// ---- Edit / Delete links in header ----
		gc.setFont(colors.getSmallFont());
		int linkX = boxX + boxWidth - PADDING_X;
		if (canDelete(comment)) {
			String delText = "Delete"; //$NON-NLS-1$
			Point ext = gc.textExtent(delText);
			linkX -= ext.x;
			boolean hovered = hitTest.isHovered(linkX, textY, ext.x,
					ext.y);
			gc.setForeground(hovered ? colors.getLinkHoverColor()
					: colors.getLinkColor());
			gc.drawString(delText, linkX, textY, true);
			hitTest.addRegion(new Rectangle(linkX, textY, ext.x, ext.y),
					CommentHitTestManager.HIT_DELETE, comment);
			linkX -= 10;
		}
		if (canEdit(comment)) {
			String editText = "Edit"; //$NON-NLS-1$
			Point ext = gc.textExtent(editText);
			linkX -= ext.x;
			boolean hovered = hitTest.isHovered(linkX, textY, ext.x,
					ext.y);
			gc.setForeground(hovered ? colors.getLinkHoverColor()
					: colors.getLinkColor());
			gc.drawString(editText, linkX, textY, true);
			hitTest.addRegion(new Rectangle(linkX, textY, ext.x, ext.y),
					CommentHitTestManager.HIT_EDIT, comment);
		}

		return startY + headerHeight;
	}

	/**
	 * Paints the body text of a comment.
	 *
	 * @param gc
	 *            the graphics context
	 * @param comment
	 *            the comment to paint
	 * @param x
	 *            the left edge for body text
	 * @param startY
	 *            the top Y position for the body
	 * @param maxTextWidth
	 *            the maximum width for text wrapping
	 * @return the new Y position below the body
	 */
	private int paintCommentBody(GC gc, PullRequestComment comment, int x,
			int startY, int maxTextWidth) {
		String body = comment.getText();
		if (body == null || body.isEmpty()) {
			return startY;
		}

		int curY = startY + SECTION_GAP;
		gc.setFont(colors.getSmallFont());
		gc.setForeground(colors.getBodyColor());
		curY = drawWrappedText(gc, comment, x + 2, curY,
				maxTextWidth - 4);
		curY += SECTION_GAP;

		return curY;
	}

	/**
	 * Paints the action bar (Reply, Resolve) and returns the new Y
	 * below it.
	 *
	 * @param gc
	 *            the graphics context
	 * @param td
	 *            the thread data
	 * @param boxX
	 *            the left edge of the bubble
	 * @param startY
	 *            the top Y position for the action bar
	 * @param boxWidth
	 *            the width of the bubble
	 * @return the new Y position below the action bar
	 */
	private int paintActionBar(GC gc,
			CommentPaintRenderer.ThreadData td, int boxX,
			int startY, int boxWidth) {
		gc.setFont(colors.getSmallFont());
		int x = boxX + PADDING_X;
		int curY = startY;

		PullRequestComment rootComment = td.rootComments.isEmpty() ? null
				: td.rootComments.get(0);

		// Calculate action bar height
		int actionBarHeight = gc.textExtent("Reply").y //$NON-NLS-1$
				+ 2 * ACTION_BAR_VPAD;

		// Paint action bar background
		gc.setBackground(colors.getActionBarBgColor());
		gc.fillRectangle(boxX + 1, curY, boxWidth - 2, actionBarHeight);

		// Add top padding
		curY += ACTION_BAR_VPAD;

		// Reply link
		if (rootComment != null) {
			String replyText = "Reply"; //$NON-NLS-1$
			Point ext = gc.textExtent(replyText);
			boolean hovered = hitTest.isHovered(x, curY, ext.x, ext.y);
			gc.setForeground(hovered ? colors.getLinkHoverColor()
					: colors.getLinkColor());
			gc.drawString(replyText, x, curY, true);
			hitTest.addRegion(new Rectangle(x, curY, ext.x, ext.y),
					CommentHitTestManager.HIT_REPLY, rootComment);
			x += ext.x + 16;
		}

		// Resolve link (Bitbucket only)
		boolean showResolve = providerType == PullRequestProviderType.BITBUCKET;
		if (showResolve && rootComment != null) {
			String resolveText = td.allResolved ? "Reopen" //$NON-NLS-1$
					: "Resolve"; //$NON-NLS-1$
			Point ext = gc.textExtent(resolveText);
			boolean hovered = hitTest.isHovered(x, curY, ext.x, ext.y);
			gc.setForeground(hovered ? colors.getLinkHoverColor()
					: colors.getLinkColor());
			gc.drawString(resolveText, x, curY, true);
			hitTest.addRegion(new Rectangle(x, curY, ext.x, ext.y),
					CommentHitTestManager.HIT_RESOLVE, rootComment);
			x += ext.x + 16;
		}

		curY += gc.textExtent("Reply").y + ACTION_BAR_VPAD; //$NON-NLS-1$
		return curY;
	}

	// ---- Avatar rendering -------------------------------------------------

	/**
	 * Paints an avatar image or fallback initials circle.
	 *
	 * @param gc
	 *            the graphics context
	 * @param x
	 *            the left edge of the avatar
	 * @param y
	 *            the top edge of the avatar
	 * @param authorName
	 *            the author name (for initials fallback)
	 * @param avatarUrl
	 *            the avatar URL, or null
	 */
	private void paintAvatar(GC gc, int x, int y, String authorName,
			String avatarUrl) {
		gc.setAntialias(SWT.ON);

		// Try cached image first
		Image img = null;
		if (avatarUrl != null && !avatarUrl.isEmpty()) {
			img = avatarImages.get(avatarUrl);
			if (img == null) {
				// Request async load; paint initials for now
				requestAvatarLoad(avatarUrl);
			}
		}

		if (img != null && !img.isDisposed()) {
			// Circular clip
			org.eclipse.swt.graphics.Path path = new org.eclipse.swt.graphics.Path(
					gc.getDevice());
			path.addArc(x, y, AVATAR_SIZE, AVATAR_SIZE, 0, 360);
			gc.setClipping(path);
			gc.drawImage(img, 0, 0, img.getBounds().width,
					img.getBounds().height, x, y, AVATAR_SIZE,
					AVATAR_SIZE);
			gc.setClipping((org.eclipse.swt.graphics.Region) null);
			path.dispose();
		} else {
			// Fallback: colored circle with initials
			gc.setBackground(colors.getAvatarBgColor());
			gc.fillOval(x, y, AVATAR_SIZE, AVATAR_SIZE);
			gc.setForeground(colors.getWhiteColor());
			gc.setFont(colors.getSmallFont());
			String initials = AvatarCanvas.computeInitials(authorName);
			Point ext = gc.textExtent(initials);
			gc.drawString(initials, x + (AVATAR_SIZE - ext.x) / 2,
					y + (AVATAR_SIZE - ext.y) / 2, true);
		}
	}

	/**
	 * Initiates an async avatar load. When the image arrives the
	 * StyledText is redrawn.
	 *
	 * @param avatarUrl
	 *            the avatar URL
	 */
	private void requestAvatarLoad(String avatarUrl) {
		// Only request once per URL
		if (avatarImages.containsKey(avatarUrl)) {
			return;
		}
		// Put a null sentinel so we don't re-request
		avatarImages.put(avatarUrl, null);

		AvatarCache.getInstance().loadAvatar(avatarUrl, AVATAR_SIZE,
				image -> {
					if (image != null) {
						avatarImages.put(avatarUrl, image);
						// Trigger repaint so the avatar replaces
						// the initials fallback
						if (styledText != null
								&& !styledText.isDisposed()) {
							styledText.redraw();
						}
					}
				});
	}

	// ---- Text rendering ---------------------------------------------------

	/**
	 * Draws word-wrapped body text for the given comment using a cached
	 * {@link TextLayout} and returns the Y position below it.
	 *
	 * @param gc
	 *            the graphics context
	 * @param comment
	 *            the comment whose body to draw
	 * @param x
	 *            the left edge
	 * @param startY
	 *            the top Y position
	 * @param maxWidth
	 *            the maximum width for wrapping
	 * @return the new Y position below the text
	 */
	private int drawWrappedText(GC gc, PullRequestComment comment, int x,
			int startY, int maxWidth) {
		if (maxWidth <= 0) {
			return startY;
		}
		TextLayout layout = measurer.getOrCreateBodyLayout(gc, comment,
				maxWidth);
		layout.draw(gc, x, startY);
		return startY + layout.getBounds().height;
	}

	// ---- Permission helpers -----------------------------------------------

	/**
	 * Returns true if the current user can edit the comment.
	 *
	 * @param comment
	 *            the comment
	 * @return true if editable
	 */
	private boolean canEdit(PullRequestComment comment) {
		if (currentUsername == null || comment == null) {
			return false;
		}
		String authorName = comment.getAuthorName();
		return authorName != null && currentUsername.equals(authorName);
	}

	/**
	 * Returns true if the current user can delete the comment.
	 *
	 * @param comment
	 *            the comment
	 * @return true if deletable
	 */
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

	// ---- Cleanup ---------------------------------------------------------

	/**
	 * Clears cached avatar images. Call when disposing the renderer.
	 */
	void clearAvatarCache() {
		// Avatar images are owned by AvatarCache — do not dispose
		avatarImages.clear();
	}
}
