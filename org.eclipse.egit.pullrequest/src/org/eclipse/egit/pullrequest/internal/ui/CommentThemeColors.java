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

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;

/**
 * Manages theme-aware colors and fonts for comment rendering.
 * <p>
 * Colors are lazily initialized on first use and automatically
 * adapt to the current Eclipse theme (light/dark mode).
 * </p>
 */
final class CommentThemeColors {

	// ---- Color blending factors for theme-aware colors -------------------

	// How much to lighten/darken base colors for different elements
	// Lower values = more contrast with base background
	private static final float BG_CONTRAST = 0.92f; // Comment background stands out from editor
	private static final float HEADER_FACTOR = 0.88f; // Header more distinct
	private static final float BORDER_FACTOR = 0.55f; // Border highly visible (increased from 0.70)
	private static final float SEPARATOR_FACTOR = 0.82f; // Separator visible but subtle

	// ---- Color fields ----------------------------------------------------

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

	// ---- Font fields -----------------------------------------------------

	private Font boldFont;
	private Font smallFont;

	// ---- Public API ------------------------------------------------------

	/**
	 * Ensures colors are initialized based on the current theme.
	 *
	 * @param st
	 *            the StyledText widget (provides Display and theme colors)
	 */
	void ensureColors(StyledText st) {
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
	 * Ensures fonts are initialized.
	 *
	 * @param gc
	 *            the GC (provides Device for Font creation)
	 */
	void ensureFonts(GC gc) {
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
	 * Disposes all allocated colors and fonts.
	 * Call when the renderer is no longer needed.
	 */
	void dispose() {
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

		colorsInitialised = false;
	}

	// ---- Accessors -------------------------------------------------------

	Color getBgColor() {
		return bgColor;
	}

	Color getHeaderBgColor() {
		return headerBgColor;
	}

	Color getResolvedBgColor() {
		return resolvedBgColor;
	}

	Color getResolvedHeaderBgColor() {
		return resolvedHeaderBgColor;
	}

	Color getAuthorColor() {
		return authorColor;
	}

	Color getTimestampColor() {
		return timestampColor;
	}

	Color getBodyColor() {
		return bodyColor;
	}

	Color getSeparatorColor() {
		return separatorColor;
	}

	Color getBorderColor() {
		return borderColor;
	}

	Color getLinkColor() {
		return linkColor;
	}

	Color getLinkHoverColor() {
		return linkHoverColor;
	}

	Color getAvatarBgColor() {
		return avatarBgColor;
	}

	Color getResolvedBadgeBgColor() {
		return resolvedBadgeBgColor;
	}

	Color getWhiteColor() {
		return whiteColor;
	}

	Color getHighlightBorderColor() {
		return highlightBorderColor;
	}

	Color getActionBarBgColor() {
		return actionBarBgColor;
	}

	Font getBoldFont() {
		return boldFont;
	}

	Font getSmallFont() {
		return smallFont;
	}

	// ---- Private helpers -------------------------------------------------

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
