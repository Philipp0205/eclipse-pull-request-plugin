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
import java.util.List;

import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Manages hit-testing for clickable regions in comment rendering.
 * <p>
 * Tracks clickable regions (Reply, Resolve, Edit, Delete, Select) painted
 * during the most recent paint pass, and handles mouse events to dispatch
 * actions to a {@link CommentActionHandler}.
 * </p>
 */
final class CommentHitTestManager {

	/** Action constants for hit regions. */
	static final int HIT_REPLY = 1;
	static final int HIT_RESOLVE = 2;
	static final int HIT_EDIT = 3;
	static final int HIT_DELETE = 4;
	static final int HIT_SELECT = 5;

	/**
	 * A clickable region painted during the last paint pass.
	 */
	static final class HitRegion {
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

	/**
	 * Hit regions computed during the most recent paint pass.
	 * Rebuilt on every paint call so they are always in
	 * current widget-relative coordinates.
	 */
	private final List<HitRegion> hitRegions = new ArrayList<>();

	/** Currently hovered hit-region (for visual feedback). */
	private HitRegion hoveredRegion;

	// ---- Public API ------------------------------------------------------

	/**
	 * Registers a new hit region for a clickable area.
	 *
	 * @param bounds
	 *            the bounds of the clickable region
	 * @param action
	 *            one of HIT_REPLY, HIT_RESOLVE, HIT_EDIT, HIT_DELETE, HIT_SELECT
	 * @param comment
	 *            the associated comment
	 */
	void addRegion(Rectangle bounds, int action,
			PullRequestComment comment) {
		hitRegions.add(new HitRegion(bounds, action, comment));
	}

	/**
	 * Clears all hit regions. Call at the start of each paint pass.
	 */
	void clearRegions() {
		hitRegions.clear();
	}

	/**
	 * Returns the currently hovered hit region, or {@code null}.
	 *
	 * @return the hovered region or null
	 */
	HitRegion getHoveredRegion() {
		return hoveredRegion;
	}

	/**
	 * Checks if the given rectangle overlaps with the currently
	 * hovered region (used for hover color feedback during painting).
	 *
	 * @param x
	 *            x coordinate
	 * @param y
	 *            y coordinate
	 * @param w
	 *            width
	 * @param h
	 *            height
	 * @return {@code true} if hovered
	 */
	boolean isHovered(int x, int y, int w, int h) {
		if (hoveredRegion == null) {
			return false;
		}
		Rectangle r = hoveredRegion.bounds;
		// Close-enough overlap check (2px tolerance)
		return r.x >= x - 2 && r.x <= x + w + 2
				&& r.y >= y - 2 && r.y <= y + h + 2;
	}

	/**
	 * Handles mouse-down events to detect clicks on hit regions.
	 *
	 * @param e
	 *            the mouse event
	 * @param actionHandler
	 *            the handler to dispatch actions to
	 */
	void handleMouseDown(MouseEvent e,
			CommentActionHandler actionHandler) {
		if (e.button != 1 || actionHandler == null) {
			return;
		}
		for (HitRegion hr : hitRegions) {
			if (hr.bounds.contains(e.x, e.y)) {
				dispatchAction(hr, actionHandler);
				return;
			}
		}
	}

	/**
	 * Handles mouse-move events to track hover state and update
	 * the cursor.
	 *
	 * @param e
	 *            the mouse event
	 */
	void handleMouseMove(MouseEvent e) {
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

	// ---- Private helpers -------------------------------------------------

	/**
	 * Dispatches a hit region action to the handler.
	 */
	private void dispatchAction(HitRegion hr,
			CommentActionHandler actionHandler) {
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
}
