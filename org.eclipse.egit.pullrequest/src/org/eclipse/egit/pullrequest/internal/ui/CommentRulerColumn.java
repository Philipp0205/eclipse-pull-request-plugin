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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.jface.text.source.AbstractRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * A vertical ruler column that displays comment indicator icons in the gutter
 * next to lines that have pull request review comments. This replaces the old
 * old {@code InlineCommentPainter} approach with a lightweight icon-based rendering
 * that is far more efficient for files with many comments.
 *
 * <p>
 * Each commented line shows a small speech-bubble icon with a count badge when
 * multiple comment threads exist on the same line. Hovering over an
 * uncommented line shows a "+" icon for creating new comments.
 * </p>
 *
 * <p>
 * Clicking a comment icon invokes the configured
 * {@link CommentClickHandler} to expand/collapse the comment thread inline.
 * </p>
 */
public class CommentRulerColumn extends AbstractRulerColumn {

	/**
	 * Callback interface for handling clicks on comment indicators.
	 */
	@FunctionalInterface
	public interface CommentClickHandler {

		/**
		 * Called when the user clicks a comment indicator in the ruler.
		 *
		 * @param line
		 *            the 1-based line number
		 * @param comments
		 *            the comments on that line
		 */
		void onCommentClick(int line, List<PullRequestComment> comments);
	}

	/**
	 * Callback interface for handling clicks on the "+" add-comment icon.
	 */
	@FunctionalInterface
	public interface NewCommentClickHandler {

		/**
		 * Called when the user clicks the "+" icon to create a new comment.
		 *
		 * @param line
		 *            the 1-based line number
		 */
		void onNewCommentClick(int line);
	}

	private static final int COLUMN_WIDTH = 20;

	private static final int ICON_SIZE = 14;

	private static final RGB COMMENT_ICON_RGB = new RGB(59, 130, 246);

	private static final RGB RESOLVED_ICON_RGB = new RGB(34, 163, 72);

	private static final RGB HOVER_ADD_RGB = new RGB(150, 150, 150);

	private static final RGB BADGE_RGB = new RGB(220, 38, 38);

	/**
	 * Map from 1-based line number to list of comments on that line.
	 */
	private final Map<Integer, List<PullRequestComment>> commentsMap =
			new HashMap<>();

	/**
	 * The currently expanded line (1-based), or -1 if no line is expanded.
	 */
	private int expandedLine = -1;

	private CommentClickHandler commentClickHandler;

	private NewCommentClickHandler newCommentClickHandler;

	private int hoveredModelLine = -1;

	private Color commentIconColor;

	private Color resolvedIconColor;

	private Color hoverAddColor;

	private Color badgeColor;

	private Color whiteColor;

	private Font badgeFont;

	private MouseListener mouseListener;

	private MouseMoveListener mouseMoveListener;

	private MouseTrackListener mouseTrackListener;

	/**
	 * Creates a new comment ruler column.
	 */
	public CommentRulerColumn() {
		setWidth(COLUMN_WIDTH);
	}

	/**
	 * Sets the comments to display in this ruler column.
	 *
	 * @param comments
	 *            the list of comments for the current file
	 */
	public void setComments(List<PullRequestComment> comments) {
		commentsMap.clear();
		if (comments != null) {
			for (PullRequestComment comment : comments) {
				if (comment.isInlineComment()
						&& comment.getLine() != null) {
					Integer lineKey = comment.getLine();
					commentsMap
							.computeIfAbsent(lineKey,
									k -> new ArrayList<>())
							.add(comment);
				}
			}
		}
		redraw();
	}

	/**
	 * Returns the comments for a given 1-based line number.
	 *
	 * @param line
	 *            the 1-based line number
	 * @return the list of comments, or an empty list
	 */
	public List<PullRequestComment> getComments(int line) {
		List<PullRequestComment> result = commentsMap
				.get(Integer.valueOf(line));
		return result != null ? result : Collections.emptyList();
	}

	/**
	 * Returns whether the given 1-based line has comments.
	 *
	 * @param line
	 *            the 1-based line number
	 * @return {@code true} if the line has comments
	 */
	public boolean hasComments(int line) {
		return commentsMap.containsKey(Integer.valueOf(line));
	}

	/**
	 * Sets the handler for comment icon clicks.
	 *
	 * @param handler
	 *            the click handler
	 */
	public void setCommentClickHandler(CommentClickHandler handler) {
		this.commentClickHandler = handler;
	}

	/**
	 * Sets the handler for "+" add-comment icon clicks.
	 *
	 * @param handler
	 *            the new comment click handler
	 */
	public void setNewCommentClickHandler(NewCommentClickHandler handler) {
		this.newCommentClickHandler = handler;
	}

	/**
	 * Sets the currently expanded line number. The ruler will highlight
	 * the icon for this line differently to indicate it is expanded.
	 *
	 * @param line
	 *            the 1-based line number, or -1 for no expansion
	 */
	public void setExpandedLine(int line) {
		this.expandedLine = line;
		redraw();
	}

	/**
	 * Returns the currently expanded line number.
	 *
	 * @return the 1-based line number, or -1 if no line is expanded
	 */
	public int getExpandedLine() {
		return expandedLine;
	}

	@Override
	public Control createControl(CompositeRuler parentRuler,
			Composite parentControl) {
		Control control = super.createControl(parentRuler, parentControl);

		ensureColors(control);
		installMouseListeners(control);

		return control;
	}

	@Override
	protected void paintLine(GC gc, int modelLine, int widgetLine,
			int linePixel, int lineHeight) {
		// Fill background
		gc.setBackground(getDefaultBackground());
		gc.fillRectangle(0, linePixel, getWidth(), lineHeight);

		// 1-based line number for comment lookup
		int oneBasedLine = modelLine + 1;

		if (hasComments(oneBasedLine)) {
			drawCommentIcon(gc, oneBasedLine, linePixel, lineHeight);
		} else if (modelLine == hoveredModelLine) {
			drawAddIcon(gc, linePixel, lineHeight);
		}
	}

	/**
	 * Draws a comment indicator icon for a commented line.
	 *
	 * @param gc
	 *            the graphics context
	 * @param oneBasedLine
	 *            the 1-based line number
	 * @param linePixel
	 *            the y pixel of the line
	 * @param lineHeight
	 *            the height of the line in pixels
	 */
	private void drawCommentIcon(GC gc, int oneBasedLine, int linePixel,
			int lineHeight) {
		List<PullRequestComment> lineComments = getComments(oneBasedLine);
		boolean allResolved = true;
		for (PullRequestComment c : lineComments) {
			if (!"RESOLVED".equals(c.getState())) { //$NON-NLS-1$
				allResolved = false;
				break;
			}
		}

		boolean isExpanded = (oneBasedLine == expandedLine);

		int iconX = (getWidth() - ICON_SIZE) / 2;
		int iconY = linePixel + (lineHeight - ICON_SIZE) / 2;

		Color iconColor = allResolved ? resolvedIconColor
				: commentIconColor;

		// Draw speech bubble icon
		if (isExpanded) {
			// Filled bubble for expanded state
			gc.setBackground(iconColor);
			gc.fillRoundRectangle(iconX, iconY, ICON_SIZE,
					ICON_SIZE - 3, 4, 4);
			// Small triangle at bottom
			gc.fillPolygon(new int[] { iconX + 3,
					iconY + ICON_SIZE - 3, iconX + 7,
					iconY + ICON_SIZE, iconX + 7,
					iconY + ICON_SIZE - 3 });
		} else {
			// Outline bubble for collapsed state
			gc.setForeground(iconColor);
			gc.setLineWidth(1);
			gc.drawRoundRectangle(iconX, iconY, ICON_SIZE,
					ICON_SIZE - 3, 4, 4);
			// Small triangle at bottom
			gc.drawPolygon(new int[] { iconX + 3,
					iconY + ICON_SIZE - 3, iconX + 7,
					iconY + ICON_SIZE, iconX + 7,
					iconY + ICON_SIZE - 3 });
		}

		// Draw count badge if multiple comments
		int totalComments = lineComments.size();
		for (PullRequestComment c : lineComments) {
			List<PullRequestComment> replies = c.getReplies();
			if (replies != null) {
				totalComments += replies.size();
			}
		}

		if (totalComments > 1) {
			drawBadge(gc, iconX, iconY, totalComments);
		}
	}

	/**
	 * Draws a count badge at the top-right corner of the icon.
	 *
	 * @param gc
	 *            the graphics context
	 * @param iconX
	 *            the x of the icon
	 * @param iconY
	 *            the y of the icon
	 * @param count
	 *            the number to display
	 */
	private void drawBadge(GC gc, int iconX, int iconY, int count) {
		String text = count > 9 ? "9+" : String.valueOf(count); //$NON-NLS-1$

		Font oldFont = gc.getFont();
		if (badgeFont != null) {
			gc.setFont(badgeFont);
		}

		int badgeSize = 10;
		int badgeX = iconX + ICON_SIZE - badgeSize / 2;
		int badgeY = iconY - badgeSize / 2 + 1;

		// Clamp to column bounds
		badgeX = Math.min(badgeX, getWidth() - badgeSize - 1);
		badgeX = Math.max(badgeX, 0);
		badgeY = Math.max(badgeY, 0);

		gc.setBackground(badgeColor);
		gc.fillOval(badgeX, badgeY, badgeSize, badgeSize);

		gc.setForeground(whiteColor);
		org.eclipse.swt.graphics.Point textExtent = gc.textExtent(text);
		int textX = badgeX + (badgeSize - textExtent.x) / 2;
		int textY = badgeY + (badgeSize - textExtent.y) / 2;
		gc.drawString(text, textX, textY, true);

		gc.setFont(oldFont);
	}

	/**
	 * Draws a "+" icon for adding a new comment (shown on hover).
	 *
	 * @param gc
	 *            the graphics context
	 * @param linePixel
	 *            the y pixel of the line
	 * @param lineHeight
	 *            the height of the line in pixels
	 */
	private void drawAddIcon(GC gc, int linePixel, int lineHeight) {
		int iconX = (getWidth() - ICON_SIZE) / 2;
		int iconY = linePixel + (lineHeight - ICON_SIZE) / 2;

		gc.setForeground(hoverAddColor);
		gc.setLineWidth(1);

		// Draw circle
		gc.drawOval(iconX, iconY, ICON_SIZE, ICON_SIZE);

		// Draw plus sign
		int center = ICON_SIZE / 2;
		int plusSize = ICON_SIZE / 3;
		gc.setLineWidth(2);
		gc.drawLine(iconX + center - plusSize, iconY + center,
				iconX + center + plusSize, iconY + center);
		gc.drawLine(iconX + center, iconY + center - plusSize,
				iconX + center, iconY + center + plusSize);
		gc.setLineWidth(1);
	}

	// ---- Mouse interaction ------------------------------------------------

	/**
	 * Installs mouse listeners on the ruler canvas for click and hover
	 * handling.
	 *
	 * @param control
	 *            the ruler canvas control
	 */
	private void installMouseListeners(Control control) {
		mouseListener = new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				// not used
			}

			@Override
			public void mouseDown(MouseEvent e) {
				if (e.button != 1) {
					return;
				}
				int line = getParentRuler().toDocumentLineNumber(e.y);
				if (line < 0) {
					return;
				}
				int oneBasedLine = line + 1;
				if (hasComments(oneBasedLine)) {
					if (commentClickHandler != null) {
						commentClickHandler.onCommentClick(
								oneBasedLine,
								getComments(oneBasedLine));
					}
				} else {
					if (newCommentClickHandler != null) {
						newCommentClickHandler
								.onNewCommentClick(oneBasedLine);
					}
				}
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				// not used
			}
		};

		mouseMoveListener = e -> {
			int line = getParentRuler().toDocumentLineNumber(e.y);
			if (line != hoveredModelLine) {
				hoveredModelLine = line;
				redraw();
			}
		};

		mouseTrackListener = new MouseTrackListener() {
			@Override
			public void mouseEnter(MouseEvent e) {
				// handled by mouseMove
			}

			@Override
			public void mouseExit(MouseEvent e) {
				if (hoveredModelLine != -1) {
					hoveredModelLine = -1;
					redraw();
				}
			}

			@Override
			public void mouseHover(MouseEvent e) {
				// not used
			}
		};

		control.addMouseListener(mouseListener);
		control.addMouseMoveListener(mouseMoveListener);
		control.addMouseTrackListener(mouseTrackListener);
	}

	// ---- Resource management ----------------------------------------------

	/**
	 * Creates the color and font resources needed for painting.
	 *
	 * @param control
	 *            the control to get the display from
	 */
	private void ensureColors(Control control) {
		commentIconColor = new Color(control.getDisplay(),
				COMMENT_ICON_RGB);
		resolvedIconColor = new Color(control.getDisplay(),
				RESOLVED_ICON_RGB);
		hoverAddColor = new Color(control.getDisplay(), HOVER_ADD_RGB);
		badgeColor = new Color(control.getDisplay(), BADGE_RGB);
		whiteColor = new Color(control.getDisplay(),
				new RGB(255, 255, 255));

		// Create a small font for the badge
		Font parentFont = control.getFont();
		FontData[] fontData = parentFont.getFontData();
		for (FontData fd : fontData) {
			fd.setHeight(Math.max(fd.getHeight() - 4, 5));
		}
		badgeFont = new Font(control.getDisplay(), fontData);
	}

	@Override
	public void dispose() {
		if (badgeFont != null && !badgeFont.isDisposed()) {
			badgeFont.dispose();
			badgeFont = null;
		}
		// Colors on modern SWT do not require explicit disposal
		commentIconColor = null;
		resolvedIconColor = null;
		hoverAddColor = null;
		badgeColor = null;
		whiteColor = null;

		super.dispose();
	}
}
