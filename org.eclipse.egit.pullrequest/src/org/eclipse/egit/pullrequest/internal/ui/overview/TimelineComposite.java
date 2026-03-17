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
package org.eclipse.egit.pullrequest.internal.ui.overview;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.model.TimelineEvent;
import org.eclipse.egit.ui.internal.PreferenceBasedDateFormatter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * A scrollable container that displays a chronological list of timeline events
 * for a pull request. Supports loading states, empty states, error states, and
 * pagination via "Load more" link.
 */
public class TimelineComposite extends Composite {

	private final FormToolkit toolkit;

	private final PreferenceBasedDateFormatter dateFormatter;

	private Composite contentArea;

	private Label statusLabel;

	private Link loadMoreLink;

	private Runnable loadMoreCallback;

	private final List<TimelineEventComposite> eventComposites;

	/**
	 * Creates a new timeline composite.
	 *
	 * @param parent
	 *            the parent composite
	 * @param toolkit
	 *            the form toolkit for consistent Eclipse Forms styling
	 * @param dateFormatter
	 *            the date formatter for timestamps
	 */
	public TimelineComposite(Composite parent, FormToolkit toolkit,
			PreferenceBasedDateFormatter dateFormatter) {
		super(parent, SWT.NONE);
		this.toolkit = toolkit;
		this.dateFormatter = dateFormatter;
		this.eventComposites = new ArrayList<>();

		toolkit.adapt(this);
		createContent();
		showLoading();
	}

	private void createContent() {
		GridLayoutFactory.fillDefaults().margins(0, 0).applyTo(this);

		// Content area where events are displayed
		contentArea = toolkit.createComposite(this);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(contentArea);
		GridLayoutFactory.fillDefaults().margins(0, 0).applyTo(contentArea);

		// Status label for loading/empty/error states
		statusLabel = toolkit.createLabel(contentArea, "", SWT.WRAP); //$NON-NLS-1$
		GridDataFactory.fillDefaults().grab(true, false)
				.align(SWT.CENTER, SWT.CENTER).applyTo(statusLabel);
		statusLabel.setVisible(false);

		// Load more link at the bottom
		loadMoreLink = new Link(this, SWT.NONE);
		toolkit.adapt(loadMoreLink, true, true);
		loadMoreLink.setText(
				"<a>" + PRText.Timeline_LoadMore + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$
		GridDataFactory.fillDefaults().grab(true, false)
				.align(SWT.CENTER, SWT.CENTER).indent(0, 10)
				.applyTo(loadMoreLink);
		loadMoreLink.setVisible(false);
		loadMoreLink.addSelectionListener(
				org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter(
						e -> {
							if (loadMoreCallback != null) {
								loadMoreCallback.run();
							}
						}));
	}

	/**
	 * Sets the list of timeline events to display. Clears any existing events
	 * and recreates the UI.
	 *
	 * @param events
	 *            the list of timeline events
	 */
	public void setEvents(List<TimelineEvent> events) {
		clearEvents();
		addEvents(events);
		hideStatus();

		if (events.isEmpty()) {
			showEmpty();
		}

		layout(true, true);
	}

	/**
	 * Appends additional timeline events to the existing list.
	 *
	 * @param events
	 *            the list of timeline events to append
	 */
	public void addEvents(List<TimelineEvent> events) {
		if (events == null || events.isEmpty()) {
			return;
		}

		for (TimelineEvent event : events) {
			TimelineEventComposite eventComp = new TimelineEventComposite(
					contentArea, toolkit, event, dateFormatter);
			GridDataFactory.fillDefaults().grab(true, false).indent(0, 8)
					.applyTo(eventComp);
			eventComposites.add(eventComp);
		}

		layout(true, true);
	}

	/**
	 * Clears all timeline events from the display.
	 */
	public void clearEvents() {
		for (TimelineEventComposite eventComp : eventComposites) {
			if (!eventComp.isDisposed()) {
				eventComp.dispose();
			}
		}
		eventComposites.clear();
	}

	/**
	 * Shows the loading state.
	 */
	public void showLoading() {
		clearEvents();
		showStatus(PRText.Timeline_Loading);
		hideLoadMore();
	}

	/**
	 * Shows the empty state when there are no events.
	 */
	public void showEmpty() {
		showStatus(PRText.Timeline_Empty);
		hideLoadMore();
	}

	/**
	 * Shows the error state when loading fails.
	 */
	public void showError() {
		clearEvents();
		showStatus(PRText.Timeline_ErrorLoading);
		hideLoadMore();
	}

	/**
	 * Shows or hides the "Load more" link based on pagination state.
	 *
	 * @param hasMore
	 *            true if more events are available
	 */
	public void setHasMore(boolean hasMore) {
		if (hasMore) {
			loadMoreLink.setVisible(true);
		} else {
			hideLoadMore();
		}
		layout(true, true);
	}

	/**
	 * Sets the callback to invoke when the "Load more" link is clicked.
	 *
	 * @param callback
	 *            the callback runnable
	 */
	public void setLoadMoreCallback(Runnable callback) {
		this.loadMoreCallback = callback;
	}

	private void showStatus(String message) {
		statusLabel.setText(message);
		statusLabel.setVisible(true);
		layout(true, true);
	}

	private void hideStatus() {
		statusLabel.setVisible(false);
	}

	private void hideLoadMore() {
		loadMoreLink.setVisible(false);
	}

	@Override
	public void dispose() {
		clearEvents();
		super.dispose();
	}

	@Override
	public boolean setFocus() {
		// Focus the first event composite if available
		if (!eventComposites.isEmpty()) {
			TimelineEventComposite first = eventComposites.get(0);
			if (!first.isDisposed()) {
				return first.setFocus();
			}
		}
		return super.setFocus();
	}
}
