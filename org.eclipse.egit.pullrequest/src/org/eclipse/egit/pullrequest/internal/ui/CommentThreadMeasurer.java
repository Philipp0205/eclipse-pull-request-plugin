package org.eclipse.egit.pullrequest.internal.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.TextLayout;

/**
 * Measures the pixel height of comment threads for vertical indent
 * calculations.
 * <p>
 * Caches {@link TextLayout} objects for word-wrapped comment body text
 * to avoid redundant layout computation across measure and paint passes.
 * </p>
 */
final class CommentThreadMeasurer {

	// Layout constants from CommentPaintRenderer
	private static final int PADDING_X = 10;
	private static final int PADDING_Y = 8;
	private static final int AVATAR_SIZE = 24;
	private static final int HEADER_SPACING = 6;
	private static final int SECTION_GAP = 6;
	private static final int REPLY_INDENT = 16;
	private static final int SEPARATOR_HEIGHT = 1;
	private static final int ACTION_BAR_VPAD = 2;

	/**
	 * Cached {@link TextLayout} objects for comment body text,
	 * keyed by comment ID. Reused across measure and paint passes.
	 */
	private final Map<Long, TextLayout> bodyLayoutCache = new HashMap<>();

	/** The content width the cached layouts were built for. */
	private int cachedLayoutWidth = -1;

	private final CommentThemeColors colors;

	// ---- Constructor -----------------------------------------------------

	/**
	 * Creates a new measurer.
	 *
	 * @param colors
	 *            the theme colors (provides fonts)
	 */
	CommentThreadMeasurer(CommentThemeColors colors) {
		this.colors = colors;
	}

	// ---- Public API ------------------------------------------------------

	/**
	 * Measures the total pixel height of a comment thread.
	 *
	 * @param gc
	 *            the GC (provides Device for TextLayout)
	 * @param td
	 *            the thread data
	 * @param width
	 *            the available width
	 * @return the height in pixels
	 */
	int measureThread(GC gc,
			CommentPaintRenderer.ThreadData td, int width) {
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
		gc.setFont(colors.getSmallFont());
		threadHeight += gc.textExtent("Reply").y + 2 * ACTION_BAR_VPAD; //$NON-NLS-1$
		// Bottom padding so the 2px border stroke is not clipped
		threadHeight += 5;
		return threadHeight;
	}

	/**
	 * Measures the height of word-wrapped body text for the given
	 * comment using a cached {@link TextLayout}.
	 *
	 * @param gc
	 *            the GC
	 * @param comment
	 *            the comment
	 * @param maxWidth
	 *            the maximum width for wrapping
	 * @return the height in pixels
	 */
	int measureWrappedText(GC gc, PullRequestComment comment,
			int maxWidth) {
		if (maxWidth <= 0) {
			return 0;
		}
		return getOrCreateBodyLayout(gc, comment, maxWidth)
				.getBounds().height;
	}

	/**
	 * Returns a cached {@link TextLayout} for the comment body,
	 * creating one if needed. If the available width has changed
	 * since the last call, the entire cache is invalidated first.
	 *
	 * @param gc
	 *            the GC (provides Device)
	 * @param comment
	 *            the comment
	 * @param maxWidth
	 *            the maximum width for wrapping
	 * @return the text layout
	 */
	TextLayout getOrCreateBodyLayout(GC gc,
			PullRequestComment comment, int maxWidth) {
		if (maxWidth != cachedLayoutWidth) {
			invalidateLayoutCache();
			cachedLayoutWidth = maxWidth;
		}
		Long key = Long.valueOf(comment.getId());
		TextLayout layout = bodyLayoutCache.get(key);
		if (layout == null) {
			layout = new TextLayout(gc.getDevice());
			layout.setFont(colors.getSmallFont());
			layout.setWidth(maxWidth);
			layout.setText(comment.getText());
			bodyLayoutCache.put(key, layout);
		}
		return layout;
	}

	/**
	 * Disposes and clears all cached body {@link TextLayout}s.
	 * Called when threads change, the widget is resized (width
	 * change detected lazily), or on dispose.
	 */
	void invalidateLayoutCache() {
		for (TextLayout tl : bodyLayoutCache.values()) {
			if (tl != null && !tl.isDisposed()) {
				tl.dispose();
			}
		}
		bodyLayoutCache.clear();
		cachedLayoutWidth = -1;
	}

	// ---- Private helpers -------------------------------------------------

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
			gc.setFont(colors.getSmallFont());
			h += measureWrappedText(gc, comment, maxTextWidth - 4);
			h += SECTION_GAP;
		}
		return h;
	}
}
