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

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable model class representing a timeline event in a pull request.
 * Timeline events include comments, reviews, commits, and status changes.
 */
public class TimelineEvent {

	private final String id;

	private final TimelineEventType type;

	private final Date createdDate;

	private final String actorName;

	private final String actorUsername;

	private final String actorAvatarUrl;

	private final String message;

	private final Map<String, String> metadata;

	/**
	 * Constructs a new timeline event.
	 *
	 * @param id
	 *            unique event identifier
	 * @param type
	 *            event classification
	 * @param createdDate
	 *            when the event occurred
	 * @param actorName
	 *            display name of who performed the action
	 * @param actorUsername
	 *            login/username of who performed the action
	 * @param actorAvatarUrl
	 *            avatar URL for display (may be null)
	 * @param message
	 *            body text for comments/reviews/commits (may be null)
	 * @param metadata
	 *            type-specific extra data (may be null)
	 */
	public TimelineEvent(String id, TimelineEventType type, Date createdDate,
			String actorName, String actorUsername, String actorAvatarUrl,
			String message, Map<String, String> metadata) {
		this.id = id;
		this.type = type;
		this.createdDate = createdDate;
		this.actorName = actorName;
		this.actorUsername = actorUsername;
		this.actorAvatarUrl = actorAvatarUrl;
		this.message = message;
		this.metadata = metadata == null ? Collections.emptyMap()
				: Collections.unmodifiableMap(new HashMap<>(metadata));
	}

	/**
	 * @return the unique event identifier
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the event type
	 */
	public TimelineEventType getType() {
		return type;
	}

	/**
	 * @return when the event occurred
	 */
	public Date getCreatedDate() {
		return createdDate;
	}

	/**
	 * @return display name of who performed the action
	 */
	public String getActorName() {
		return actorName;
	}

	/**
	 * @return login/username of who performed the action
	 */
	public String getActorUsername() {
		return actorUsername;
	}

	/**
	 * @return avatar URL for display, or null if not available
	 */
	public String getActorAvatarUrl() {
		return actorAvatarUrl;
	}

	/**
	 * @return body text for comments, reviews, or commit messages, or null if
	 *         not applicable
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @return the first line of the message, or empty string if message is
	 *         null
	 */
	public String getShortMessage() {
		if (message == null) {
			return ""; //$NON-NLS-1$
		}
		int newlineIndex = message.indexOf('\n');
		return newlineIndex >= 0 ? message.substring(0, newlineIndex)
				: message;
	}

	/**
	 * @return type-specific metadata map, never null (returns empty map if no
	 *         metadata)
	 */
	public Map<String, String> getMetadata() {
		return metadata;
	}

	@Override
	public String toString() {
		return "TimelineEvent [type=" + type + ", actor=" + actorUsername //$NON-NLS-1$ //$NON-NLS-2$
				+ ", id=" + id + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
