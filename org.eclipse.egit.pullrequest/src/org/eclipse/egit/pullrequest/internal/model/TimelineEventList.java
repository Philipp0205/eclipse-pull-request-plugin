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
package org.eclipse.egit.pullrequest.internal.model;

import java.util.List;

/**
 * Model class representing a paginated list of timeline events. Supports both
 * GitHub's URL-based pagination (Link header) and Bitbucket's index-based
 * pagination (start/limit).
 */
public class TimelineEventList {

	private List<TimelineEvent> events;

	private boolean isLastPage;

	private int nextPageStart;

	private String nextPageUrl;

	/**
	 * @return the list of timeline events
	 */
	public List<TimelineEvent> getEvents() {
		return events;
	}

	/**
	 * @param events
	 *            the list of timeline events
	 */
	public void setEvents(List<TimelineEvent> events) {
		this.events = events;
	}

	/**
	 * @return true if this is the last page of results
	 */
	public boolean isLastPage() {
		return isLastPage;
	}

	/**
	 * @param isLastPage
	 *            true if this is the last page of results
	 */
	public void setLastPage(boolean isLastPage) {
		this.isLastPage = isLastPage;
	}

	/**
	 * @return the start index for the next page (Bitbucket-style pagination)
	 */
	public int getNextPageStart() {
		return nextPageStart;
	}

	/**
	 * @param nextPageStart
	 *            the start index for the next page
	 */
	public void setNextPageStart(int nextPageStart) {
		this.nextPageStart = nextPageStart;
	}

	/**
	 * @return the URL for the next page (GitHub-style pagination), or null if
	 *         not available
	 */
	public String getNextPageUrl() {
		return nextPageUrl;
	}

	/**
	 * @param nextPageUrl
	 *            the URL for the next page
	 */
	public void setNextPageUrl(String nextPageUrl) {
		this.nextPageUrl = nextPageUrl;
	}
}
