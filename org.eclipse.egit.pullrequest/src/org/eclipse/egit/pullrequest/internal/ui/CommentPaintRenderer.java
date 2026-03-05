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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.pullrequest.internal.client.PullRequestProviderType;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
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

	// Hit action constants are now in CommentHitTestManager

	// ---- State -----------------------------------------------------------

	private final Map<Integer, ThreadData> threads = new HashMap<>();

	private CommentActionHandler actionHandler;

	/** Theme-aware colors and fonts. */
	private final CommentThemeColors colors = new CommentThemeColors();

	/** Hit-test management for clickable regions. */
	private final CommentHitTestManager hitTest = new CommentHitTestManager();

	/** Text measurement and layout caching. */
	private final CommentThreadMeasurer measurer = new CommentThreadMeasurer(colors);

	/** Painter for comment threads. */
	private final CommentThreadPainter painter = new CommentThreadPainter(
			colors, measurer, hitTest);

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
		painter.setCurrentUsername(username);
	}

	/**
	 * Sets the provider type (controls Resolve link visibility).
	 *
	 * @param type the provider type
	 */
	void setProviderType(PullRequestProviderType type) {
		painter.setProviderType(type);
	}

	/**
	 * Sets the highlighted comment for visual feedback when navigating
	 * from the Comments View.
	 *
	 * @param comment the comment to highlight, or {@code null} to clear
	 */
	void setHighlightedComment(PullRequestComment comment) {
		long id = (comment != null) ? comment.getId() : -1;
		painter.setHighlightedCommentId(id);
	}

	/**
	 * Clears the highlighted comment.
	 */
	void clearHighlight() {
		painter.setHighlightedCommentId(-1);
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
		hitTest.clearRegions();
		measurer.invalidateLayoutCache();
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
			colors.ensureFonts(gc);
			int h = measurer.measureThread(gc, td, width);
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

		colors.ensureColors(st);
		colors.ensureFonts(e.gc);

		// Rebuild hit regions on every paint
		hitTest.clearRegions();

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

				painter.paintThread(e.gc, td, bubbleX, bubbleY,
						contentWidth);
			} catch (IllegalArgumentException ex) {
				// Line not available — skip
			}
		}
	}

	// ---- MouseListener / MouseMoveListener --------------------------------

	@Override
	public void mouseDown(MouseEvent e) {
		hitTest.handleMouseDown(e, actionHandler);
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
		hitTest.handleMouseMove(e);
	}

	/**
	 * Disposes resources created by this renderer. Call when the
	 * compare editor is closed.
	 */
	void dispose() {
		threads.clear();
		hitTest.clearRegions();
		measurer.invalidateLayoutCache();
		painter.clearAvatarCache();
		colors.dispose();
	}
}
