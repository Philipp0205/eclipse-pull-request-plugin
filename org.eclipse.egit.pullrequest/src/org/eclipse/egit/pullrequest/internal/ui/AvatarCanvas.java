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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/**
 * A {@link Canvas} that displays a user avatar. Shows initials in a colored
 * circle as a placeholder, and asynchronously loads the real avatar image via
 * {@link AvatarCache} when a URL is provided.
 *
 * <p>
 * An optional approved-overlay can be enabled to draw a green border around the
 * avatar (used in the reviewer list).
 * </p>
 */
public class AvatarCanvas extends Canvas {

	private static final String DATA_AVATAR_IMAGE = "avatarImage"; //$NON-NLS-1$

	private final int size;

	private final String initials;

	private final Color fallbackBgColor;

	private Font initialsFont;

	private boolean approved;

	/**
	 * Creates a new avatar canvas.
	 *
	 * @param parent
	 *            the parent composite
	 * @param size
	 *            the avatar size in pixels (width and height)
	 * @param displayName
	 *            the user's display name (used for initials fallback)
	 * @param fallbackBgColor
	 *            the background color for the initials circle
	 * @param avatarUrl
	 *            the avatar image URL, or {@code null}
	 */
	public AvatarCanvas(Composite parent, int size, String displayName,
			Color fallbackBgColor, String avatarUrl) {
		super(parent, SWT.DOUBLE_BUFFERED);
		this.size = size;
		this.initials = computeInitials(displayName);
		this.fallbackBgColor = fallbackBgColor;

		setBackground(parent.getBackground());
		setData(DATA_AVATAR_IMAGE, null);

		addPaintListener(e -> {
			if (isDisposed()) {
				return;
			}
			paint(e.gc);
		});

		if (avatarUrl != null && !avatarUrl.isEmpty()) {
			AvatarCache.getInstance().loadAvatar(avatarUrl, size, image -> {
				if (!isDisposed() && image != null) {
					setData(DATA_AVATAR_IMAGE, image);
					redraw();
				}
			});
		}
	}

	/**
	 * Sets whether this avatar should show an approved indicator (green
	 * border).
	 *
	 * @param approved
	 *            {@code true} to draw an approved border
	 */
	public void setApproved(boolean approved) {
		this.approved = approved;
	}

	/**
	 * Sets the font used to draw the initials in the fallback circle.
	 *
	 * @param font
	 *            the font, or {@code null} to use the GC default
	 */
	public void setInitialsFont(Font font) {
		this.initialsFont = font;
	}

	private void paint(GC gc) {
		gc.setAntialias(SWT.ON);
		
		// Fill entire canvas with background color first
		gc.setBackground(getBackground());
		gc.fillRectangle(0, 0, size, size);

		Image avatarImage = (Image) getData(DATA_AVATAR_IMAGE);
		if (avatarImage != null && !avatarImage.isDisposed()) {
			// Use circular clipping to draw the avatar
			org.eclipse.swt.graphics.Path path = new org.eclipse.swt.graphics.Path(getDisplay());
			path.addArc(0, 0, size, size, 0, 360);
			gc.setClipping(path);
			gc.drawImage(avatarImage, 0, 0);
			gc.setClipping((org.eclipse.swt.graphics.Region) null);
			path.dispose();
		} else {
			// Fallback: colored circle with initials
			gc.setBackground(fallbackBgColor);
			gc.fillOval(0, 0, size, size);

			gc.setForeground(
					Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			if (initialsFont != null) {
				gc.setFont(initialsFont);
			}

			Point textExtent = gc.textExtent(initials);
			int x = (size - textExtent.x) / 2;
			int y = (size - textExtent.y) / 2;
			gc.drawText(initials, x, y, true);
		}

		if (approved) {
			gc.setForeground(
					Display.getCurrent().getSystemColor(
							SWT.COLOR_DARK_GREEN));
			gc.setLineWidth(3);
			gc.drawOval(1, 1, size - 2, size - 2);
		}
	}

	/**
	 * Extracts initials from a name (up to 2 characters).
	 *
	 * @param name
	 *            the full name
	 * @return the initials (1–2 uppercase letters)
	 */
	static String computeInitials(String name) {
		if (name == null || name.isEmpty()) {
			return "?"; //$NON-NLS-1$
		}

		String[] parts = name.trim().split("\\s+"); //$NON-NLS-1$
		if (parts.length >= 2) {
			return (String.valueOf(parts[0].charAt(0))
					+ parts[parts.length - 1].charAt(0)).toUpperCase();
		} else if (parts.length == 1 && !parts[0].isEmpty()) {
			return String.valueOf(parts[0].charAt(0)).toUpperCase();
		}
		return "?"; //$NON-NLS-1$
	}

	/**
	 * Returns a deterministic color for a given name by hashing.
	 *
	 * @param name
	 *            the name to derive a color from
	 * @return a new {@link Color} (caller must dispose)
	 */
	public static Color colorForName(String name) {
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

		int hash = Math.abs(
				(name != null ? name : "").hashCode()); //$NON-NLS-1$
		int index = hash % palette.length;
		return new Color(Display.getCurrent(), palette[index]);
	}
}
