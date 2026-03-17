/*******************************************************************************
 * Copyright (C) 2026, Philipp Hoenisch and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.bitbucket;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.egit.pullrequest.internal.model.TimelineEvent;
import org.eclipse.egit.pullrequest.internal.model.TimelineEventList;
import org.eclipse.egit.pullrequest.internal.model.TimelineEventType;
import org.junit.Test;

/**
 * Tests for {@link BitbucketJsonParser}
 */
public class BitbucketJsonParserTest {

	@Test
	public void testParseReviewersEmpty() {
		String json = "{\"id\":42,\"version\":1," //$NON-NLS-1$
				+ "\"title\":\"Test PR\",\"state\":\"OPEN\"," //$NON-NLS-1$
				+ "\"open\":true,\"closed\":false," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"updatedDate\":1705319100000," //$NON-NLS-1$
				+ "\"fromRef\":{\"id\":\"refs/heads/feature\"," //$NON-NLS-1$
				+ "\"displayId\":\"feature\"}," //$NON-NLS-1$
				+ "\"toRef\":{\"id\":\"refs/heads/main\"," //$NON-NLS-1$
				+ "\"displayId\":\"main\"}," //$NON-NLS-1$
				+ "\"author\":{\"user\":{\"name\":\"author\"}}," //$NON-NLS-1$
				+ "\"reviewers\":[]}"; //$NON-NLS-1$

		PullRequest pr = BitbucketJsonParser.parseSinglePullRequest(json, null);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), notNullValue());
		assertThat(pr.getReviewers(), hasSize(0));
	}

	@Test
	public void testParseSingleReviewer() {
		String json = "{\"id\":42,\"version\":1," //$NON-NLS-1$
				+ "\"title\":\"Test PR\",\"state\":\"OPEN\"," //$NON-NLS-1$
				+ "\"open\":true,\"closed\":false," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"updatedDate\":1705319100000," //$NON-NLS-1$
				+ "\"fromRef\":{\"id\":\"refs/heads/feature\"," //$NON-NLS-1$
				+ "\"displayId\":\"feature\"}," //$NON-NLS-1$
				+ "\"toRef\":{\"id\":\"refs/heads/main\"," //$NON-NLS-1$
				+ "\"displayId\":\"main\"}," //$NON-NLS-1$
				+ "\"author\":{\"user\":{\"name\":\"author\"}}," //$NON-NLS-1$
				+ "\"reviewers\":[{" //$NON-NLS-1$
				+ "\"user\":{\"name\":\"reviewer1\"," //$NON-NLS-1$
				+ "\"displayName\":\"Reviewer One\"," //$NON-NLS-1$
				+ "\"emailAddress\":\"reviewer1@example.com\"}," //$NON-NLS-1$
				+ "\"role\":\"REVIEWER\"," //$NON-NLS-1$
				+ "\"approved\":false," //$NON-NLS-1$
				+ "\"status\":\"UNAPPROVED\"}]}"; //$NON-NLS-1$

		PullRequest pr = BitbucketJsonParser.parseSinglePullRequest(json, null);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), notNullValue());
		assertThat(pr.getReviewers(), hasSize(1));

		PullRequest.PullRequestParticipant reviewer =
				pr.getReviewers().get(0);
		assertThat(reviewer.getUser().getName(),
				equalTo("reviewer1")); //$NON-NLS-1$
		assertThat(reviewer.getUser().getDisplayName(),
				equalTo("Reviewer One")); //$NON-NLS-1$
		assertThat(reviewer.getUser().getEmailAddress(),
				equalTo("reviewer1@example.com")); //$NON-NLS-1$
		assertThat(reviewer.getRole(), equalTo("REVIEWER")); //$NON-NLS-1$
		assertThat(reviewer.isApproved(), equalTo(false));
	}

	@Test
	public void testParseReviewerApproved() {
		String json = "{\"id\":42,\"version\":1," //$NON-NLS-1$
				+ "\"title\":\"Test PR\",\"state\":\"OPEN\"," //$NON-NLS-1$
				+ "\"open\":true,\"closed\":false," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"updatedDate\":1705319100000," //$NON-NLS-1$
				+ "\"fromRef\":{\"id\":\"refs/heads/feature\"," //$NON-NLS-1$
				+ "\"displayId\":\"feature\"}," //$NON-NLS-1$
				+ "\"toRef\":{\"id\":\"refs/heads/main\"," //$NON-NLS-1$
				+ "\"displayId\":\"main\"}," //$NON-NLS-1$
				+ "\"author\":{\"user\":{\"name\":\"author\"}}," //$NON-NLS-1$
				+ "\"reviewers\":[{" //$NON-NLS-1$
				+ "\"user\":{\"name\":\"reviewer1\"}," //$NON-NLS-1$
				+ "\"role\":\"REVIEWER\"," //$NON-NLS-1$
				+ "\"approved\":true," //$NON-NLS-1$
				+ "\"status\":\"APPROVED\"}]}"; //$NON-NLS-1$

		PullRequest pr = BitbucketJsonParser.parseSinglePullRequest(json, null);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), hasSize(1));
		assertThat(pr.getReviewers().get(0).isApproved(),
				equalTo(true));
	}

	@Test
	public void testParseMultipleReviewers() {
		String json = "{\"id\":42,\"version\":1," //$NON-NLS-1$
				+ "\"title\":\"Test PR\",\"state\":\"OPEN\"," //$NON-NLS-1$
				+ "\"open\":true,\"closed\":false," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"updatedDate\":1705319100000," //$NON-NLS-1$
				+ "\"fromRef\":{\"id\":\"refs/heads/feature\"," //$NON-NLS-1$
				+ "\"displayId\":\"feature\"}," //$NON-NLS-1$
				+ "\"toRef\":{\"id\":\"refs/heads/main\"," //$NON-NLS-1$
				+ "\"displayId\":\"main\"}," //$NON-NLS-1$
				+ "\"author\":{\"user\":{\"name\":\"author\"}}," //$NON-NLS-1$
				+ "\"reviewers\":[" //$NON-NLS-1$
				+ "{\"user\":{\"name\":\"alice\",\"displayName\":\"Alice\"}," //$NON-NLS-1$
				+ "\"role\":\"REVIEWER\",\"approved\":false}," //$NON-NLS-1$
				+ "{\"user\":{\"name\":\"bob\",\"displayName\":\"Bob\"}," //$NON-NLS-1$
				+ "\"role\":\"REVIEWER\",\"approved\":true}," //$NON-NLS-1$
				+ "{\"user\":{\"name\":\"charlie\"}," //$NON-NLS-1$
				+ "\"role\":\"REVIEWER\",\"approved\":false}]}"; //$NON-NLS-1$

		PullRequest pr = BitbucketJsonParser.parseSinglePullRequest(json, null);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), hasSize(3));
		assertThat(pr.getReviewers().get(0).getUser().getName(),
				equalTo("alice")); //$NON-NLS-1$
		assertThat(pr.getReviewers().get(0).isApproved(),
				equalTo(false));
		assertThat(pr.getReviewers().get(1).getUser().getName(),
				equalTo("bob")); //$NON-NLS-1$
		assertThat(pr.getReviewers().get(1).isApproved(),
				equalTo(true));
		assertThat(pr.getReviewers().get(2).getUser().getName(),
				equalTo("charlie")); //$NON-NLS-1$
	}

	@Test
	public void testParseParticipantsWithReviewers() {
		// Test that participants array correctly filters reviewers
		String json = "{\"id\":42,\"version\":1," //$NON-NLS-1$
				+ "\"title\":\"Test PR\",\"state\":\"OPEN\"," //$NON-NLS-1$
				+ "\"open\":true,\"closed\":false," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"updatedDate\":1705319100000," //$NON-NLS-1$
				+ "\"fromRef\":{\"id\":\"refs/heads/feature\"," //$NON-NLS-1$
				+ "\"displayId\":\"feature\"}," //$NON-NLS-1$
				+ "\"toRef\":{\"id\":\"refs/heads/main\"," //$NON-NLS-1$
				+ "\"displayId\":\"main\"}," //$NON-NLS-1$
				+ "\"author\":{\"user\":{\"name\":\"author\"}}," //$NON-NLS-1$
				+ "\"reviewers\":[" //$NON-NLS-1$
				+ "{\"user\":{\"name\":\"reviewer1\"}," //$NON-NLS-1$
				+ "\"role\":\"REVIEWER\",\"approved\":true}]}"; //$NON-NLS-1$

		PullRequest pr = BitbucketJsonParser.parseSinglePullRequest(json, null);

		assertThat(pr, notNullValue());
		assertThat(pr.getReviewers(), hasSize(1));
		assertThat(pr.getReviewers().get(0).getRole(),
				equalTo("REVIEWER")); //$NON-NLS-1$
	}

	@Test
	public void testParseTimelineCommentActivity() {
		String json = "{\"id\":123,\"action\":\"COMMENTED\"," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"comment\":{\"text\":\"Great work!\"}," //$NON-NLS-1$
				+ "\"user\":{\"name\":\"reviewer\"," //$NON-NLS-1$
				+ "\"displayName\":\"Reviewer Name\"}}"; //$NON-NLS-1$

		TimelineEvent event = BitbucketJsonParser
				.parseTimelineActivity(json);

		assertThat(event, notNullValue());
		assertThat(event.getType(),
				equalTo(TimelineEventType.COMMENTED));
		assertThat(event.getActorUsername(), equalTo("reviewer")); //$NON-NLS-1$
		assertThat(event.getActorName(),
				equalTo("Reviewer Name")); //$NON-NLS-1$
		assertThat(event.getMessage(), equalTo("Great work!")); //$NON-NLS-1$
	}

	@Test
	public void testParseTimelineApprovedActivity() {
		String json = "{\"id\":456,\"action\":\"APPROVED\"," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"user\":{\"name\":\"reviewer\"," //$NON-NLS-1$
				+ "\"displayName\":\"Reviewer Name\"}}"; //$NON-NLS-1$

		TimelineEvent event = BitbucketJsonParser
				.parseTimelineActivity(json);

		assertThat(event, notNullValue());
		assertThat(event.getType(), equalTo(TimelineEventType.REVIEWED));
		assertThat(event.getActorUsername(), equalTo("reviewer")); //$NON-NLS-1$
		assertThat(event.getMetadata().get("state"), //$NON-NLS-1$
				equalTo("approved")); //$NON-NLS-1$
	}

	@Test
	public void testParseTimelineMergedActivity() {
		String json = "{\"id\":789,\"action\":\"MERGED\"," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"user\":{\"name\":\"maintainer\"," //$NON-NLS-1$
				+ "\"displayName\":\"Maintainer Name\"}}"; //$NON-NLS-1$

		TimelineEvent event = BitbucketJsonParser
				.parseTimelineActivity(json);

		assertThat(event, notNullValue());
		assertThat(event.getType(), equalTo(TimelineEventType.MERGED));
		assertThat(event.getActorUsername(), equalTo("maintainer")); //$NON-NLS-1$
	}

	@Test
	public void testParseTimelineOpenedActivity() {
		String json = "{\"id\":101,\"action\":\"OPENED\"," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"user\":{\"name\":\"author\"," //$NON-NLS-1$
				+ "\"displayName\":\"Author Name\"}}"; //$NON-NLS-1$

		TimelineEvent event = BitbucketJsonParser
				.parseTimelineActivity(json);

		assertThat(event, notNullValue());
		assertThat(event.getType(), equalTo(TimelineEventType.OPENED));
		assertThat(event.getActorUsername(), equalTo("author")); //$NON-NLS-1$
	}

	@Test
	public void testParseTimelineActivities() {
		String json = "{\"values\":[" //$NON-NLS-1$
				+ "{\"id\":1,\"action\":\"COMMENTED\"," //$NON-NLS-1$
				+ "\"createdDate\":1705318800000," //$NON-NLS-1$
				+ "\"comment\":{\"text\":\"First\"}," //$NON-NLS-1$
				+ "\"user\":{\"name\":\"user1\"," //$NON-NLS-1$
				+ "\"displayName\":\"User 1\"}}," //$NON-NLS-1$
				+ "{\"id\":2,\"action\":\"MERGED\"," //$NON-NLS-1$
				+ "\"createdDate\":1705319100000," //$NON-NLS-1$
				+ "\"user\":{\"name\":\"user2\"," //$NON-NLS-1$
				+ "\"displayName\":\"User 2\"}}" //$NON-NLS-1$
				+ "],\"isLastPage\":false,\"nextPageStart\":2}"; //$NON-NLS-1$

		TimelineEventList events = BitbucketJsonParser
				.parseTimelineActivities(json);

		assertThat(events, notNullValue());
		assertThat(events.getEvents(), hasSize(2));
		assertThat(events.getEvents().get(0).getType(),
				equalTo(TimelineEventType.COMMENTED));
		assertThat(events.getEvents().get(1).getType(),
				equalTo(TimelineEventType.MERGED));
		assertThat(events.isLastPage(), equalTo(false));
		assertThat(events.getNextPageStart(), equalTo(2));
	}
}
