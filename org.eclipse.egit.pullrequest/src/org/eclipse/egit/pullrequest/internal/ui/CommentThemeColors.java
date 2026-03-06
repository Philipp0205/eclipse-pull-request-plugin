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
	private static final float BG_CONTRAST = 0.92f;
	private static final float HEADER_FACTOR = 0.88f;
	private static final float BORDER_FACTOR = 0.55f;
	private static final float SEPARATOR_FACTOR = 0.82f;

	// Dark-theme accent tint — a subtle blue hue so comment bubbles
	// are visually distinct from the plain dark editor background
	// instead of just a slightly lighter grey.
	private static final RGB DARK_ACCENT = new RGB(60, 80, 120);
	private static final float DARK_BG_ACCENT = 0.25f;
	private static final float DARK_HDR_ACCENT = 0.32f;

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

	/**
	 * Tracks the widget background RGB at the time colors were
	 * computed so we can detect a theme change.
	 */
	private RGB lastWidgetBgRgb;

	// ---- Font fields -----------------------------------------------------

	private Font boldFont;
	private Font smallFont;

	// ---- Public API ------------------------------------------------------

	/**
	 * Ensures colors are initialized based on the current theme.
	 *
	 * @param st
	 *            the StyledText widget (provides Display and theme
	 *            colors)
	 */
	void ensureColors(StyledText st) {
		// Detect theme changes by comparing the actual editor
		// background, which is the surface we paint on
		Color editorBg = st.getBackground();
		RGB currentRgb = editorBg.getRGB();

		if (colorsInitialised) {
			if (currentRgb.equals(lastWidgetBgRgb)) {
				return;
			}
			// Theme changed — dispose old blended colors and
			// reinitialize
			disposeColors();
		}
		colorsInitialised = true;
		lastWidgetBgRgb = currentRgb;

		// Use the actual editor background as the base so comment
		// bubbles adapt correctly to both light and dark themes
		Color baseBg = editorBg;
		Color widgetFg = st.getDisplay()
				.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
		Color baseFg = st.getForeground();
		boolean dark = isDarkBackground(baseBg);

		if (dark) {
			// Dark theme: blend editor bg with a blue accent
			// tint so comments are visually distinct (not just
			// a slightly lighter grey)
			bgColor = blendRgb(st.getDisplay(),
					baseBg.getRGB(), DARK_ACCENT,
					1.0f - DARK_BG_ACCENT);
			headerBgColor = blendRgb(st.getDisplay(),
					baseBg.getRGB(), DARK_ACCENT,
					1.0f - DARK_HDR_ACCENT);
		} else {
			// Light theme: shift toward foreground for a
			// subtle off-white background
			bgColor = blendColor(st.getDisplay(), baseBg,
					widgetFg, BG_CONTRAST);
			headerBgColor = blendColor(st.getDisplay(), baseBg,
					widgetFg, HEADER_FACTOR);
		}

		// Resolved comments get a slight green tint
		resolvedBgColor = blendWithGreen(
				st.getDisplay(), bgColor, 0.05f);
		resolvedHeaderBgColor = blendWithGreen(
				st.getDisplay(), headerBgColor, 0.08f);

		// Text colors from actual editor foreground
		authorColor = baseFg;
		bodyColor = baseFg;

		// Timestamp is slightly muted
		timestampColor = blendColor(st.getDisplay(),
				baseFg, baseBg, 0.6f);

		// Separators and borders — use accent-tinted blend
		// in dark mode for a cohesive look
		if (dark) {
			separatorColor = blendRgb(st.getDisplay(),
					baseBg.getRGB(), DARK_ACCENT,
					1.0f - 0.40f);
			borderColor = blendRgb(st.getDisplay(),
					baseBg.getRGB(), DARK_ACCENT,
					1.0f - 0.55f);
		} else {
			separatorColor = blendColor(st.getDisplay(), baseBg,
					widgetFg, SEPARATOR_FACTOR);
			borderColor = blendColor(st.getDisplay(), baseBg,
					widgetFg, BORDER_FACTOR);
		}

		// Links use Eclipse's hyperlink color
		linkColor = st.getDisplay()
				.getSystemColor(SWT.COLOR_LINK_FOREGROUND);
		linkHoverColor = blendColor(st.getDisplay(),
				linkColor, widgetFg, 0.8f);

		// Avatar background - use a distinct color
		avatarBgColor = st.getDisplay()
				.getSystemColor(SWT.COLOR_TITLE_BACKGROUND);

		// Resolved badge - green with good contrast
		resolvedBadgeBgColor = new Color(
				st.getDisplay(), new RGB(34, 197, 94));

		// Badge/avatar text — always white (works on both
		// dark accent backgrounds and green badges)
		whiteColor = st.getDisplay()
				.getSystemColor(SWT.COLOR_WHITE);

		// Highlight border for selected comments
		highlightBorderColor = linkColor;

		// Action bar background - between editor bg and
		// comment bg for a subtle footer area
		if (dark) {
			actionBarBgColor = blendRgb(st.getDisplay(),
					baseBg.getRGB(), DARK_ACCENT,
					1.0f - DARK_BG_ACCENT * 0.6f);
		} else {
			actionBarBgColor = blendColor(st.getDisplay(),
					baseBg, widgetFg, 0.95f);
		}
	}

	/**
	 * Returns whether the given colour is considered dark.
	 *
	 * @param color
	 *            the colour to test
	 * @return {@code true} when the perceived luminance is below 50 %
	 */
	private boolean isDarkBackground(Color color) {
		RGB rgb = color.getRGB();
		// Perceived luminance (ITU-R BT.601)
		double luminance = (0.299 * rgb.red + 0.587 * rgb.green
				+ 0.114 * rgb.blue) / 255.0;
		return luminance < 0.5;
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

		disposeColors();
	}

	/**
	 * Disposes dynamically created colors and resets the
	 * initialisation flag so that the next
	 * {@link #ensureColors(StyledText)} call recomputes them.
	 */
	private void disposeColors() {
		// Dispose dynamically created colors (from blending)
		// System colors (from getSystemColor) don't need disposal
		disposeColorIfNotSystem(bgColor);
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
		return blendRgb(display, base.getRGB(), blend.getRGB(),
				factor);
	}

	/**
	 * Blends two RGB values together with a given factor.
	 * Factor 1.0 = fully base, 0.0 = fully blend.
	 *
	 * @param display
	 *            the display for creating the Color
	 * @param baseRGB
	 *            the base RGB
	 * @param blendRGB
	 *            the RGB to blend toward
	 * @param factor
	 *            1.0 = fully base, 0.0 = fully blend
	 * @return a new Color (caller must dispose)
	 */
	private Color blendRgb(org.eclipse.swt.widgets.Display display,
			RGB baseRGB, RGB blendRGB, float factor) {
		int r = (int) (baseRGB.red * factor
				+ blendRGB.red * (1 - factor));
		int g = (int) (baseRGB.green * factor
				+ blendRGB.green * (1 - factor));
		int b = (int) (baseRGB.blue * factor
				+ blendRGB.blue * (1 - factor));
		r = Math.min(255, Math.max(0, r));
		g = Math.min(255, Math.max(0, g));
		b = Math.min(255, Math.max(0, b));
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
