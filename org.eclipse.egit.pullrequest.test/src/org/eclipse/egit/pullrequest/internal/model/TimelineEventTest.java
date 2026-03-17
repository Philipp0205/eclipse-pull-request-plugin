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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Date;

import org.junit.Test;

/**
 * Tests for {@link TimelineEvent}
 */
public class TimelineEventTest {

	@Test
	public void testCreateTimelineEvent() {
		Date now = new Date();
		TimelineEvent event = new TimelineEvent("123", //$NON-NLS-1$
				TimelineEventType.COMMENTED, now, "John Doe", //$NON-NLS-1$
				"johndoe", "https://example.com/avatar.png", //$NON-NLS-1$ //$NON-NLS-2$
				"Great work!", null); //$NON-NLS-1$

		assertThat(event, notNullValue());
		assertThat(event.getId(), equalTo("123")); //$NON-NLS-1$
		assertThat(event.getType(),
				equalTo(TimelineEventType.COMMENTED));
		assertThat(event.getCreatedDate(), equalTo(now));
		assertThat(event.getActorName(), equalTo("John Doe")); //$NON-NLS-1$
		assertThat(event.getActorUsername(), equalTo("johndoe")); //$NON-NLS-1$
		assertThat(event.getActorAvatarUrl(),
				equalTo("https://example.com/avatar.png")); //$NON-NLS-1$
		assertThat(event.getMessage(), equalTo("Great work!")); //$NON-NLS-1$
	}

	@Test
	public void testTimelineEventWithNullMessage() {
		Date now = new Date();
		TimelineEvent event = new TimelineEvent("456", //$NON-NLS-1$
				TimelineEventType.MERGED, now, "Jane Smith", //$NON-NLS-1$
				"janesmith", null, null, null); //$NON-NLS-1$

		assertThat(event, notNullValue());
		assertThat(event.getMessage(), equalTo(null));
	}

	@Test
	public void testTimelineEventList() {
		TimelineEventList list = new TimelineEventList();

		assertThat(list, notNullValue());
		assertThat(list.getEvents(), notNullValue());
		assertThat(list.isLastPage(), equalTo(true));
	}

	@Test
	public void testTimelineEventListPagination() {
		TimelineEventList list = new TimelineEventList();
		list.setLastPage(false);
		list.setNextPageStart(100);
		list.setNextPageUrl("https://api.github.com/next"); //$NON-NLS-1$

		assertThat(list.isLastPage(), equalTo(false));
		assertThat(list.getNextPageStart(), equalTo(100));
		assertThat(list.getNextPageUrl(),
				equalTo("https://api.github.com/next")); //$NON-NLS-1$
	}
}
