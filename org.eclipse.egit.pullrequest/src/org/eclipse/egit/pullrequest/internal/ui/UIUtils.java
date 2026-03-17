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
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/**
 * Utility methods for UI-related operations.
 */
public class UIUtils {

	private UIUtils() {
		// Utility class, no instantiation
	}

	/**
	 * Determines if the current Eclipse theme is a dark theme based on the
	 * luminance of the system background color.
	 *
	 * @param display
	 *            the display to check
	 * @return {@code true} if the theme is dark, {@code false} otherwise
	 */
	public static boolean isDarkTheme(Display display) {
		Color bg = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		RGB rgb = bg.getRGB();
		double luminance = (0.299 * rgb.red + 0.587 * rgb.green
				+ 0.114 * rgb.blue) / 255.0;
		return luminance < 0.5;
	}
}
