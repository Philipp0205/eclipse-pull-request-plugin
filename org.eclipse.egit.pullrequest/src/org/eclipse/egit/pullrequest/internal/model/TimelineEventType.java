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

/**
 * Enum representing different types of timeline events in a pull request.
 */
public enum TimelineEventType {
	/**
	 * Pull request was opened
	 */
	OPENED,

	/**
	 * Pull request was closed without merging
	 */
	CLOSED,

	/**
	 * Pull request was merged
	 */
	MERGED,

	/**
	 * Pull request was reopened after being closed
	 */
	REOPENED,

	/**
	 * A general comment was added to the pull request
	 */
	COMMENTED,

	/**
	 * An inline code review comment was added
	 */
	REVIEW_COMMENT,

	/**
	 * One or more commits were pushed to the pull request branch
	 */
	COMMITTED,

	/**
	 * A review was submitted (approved, changes requested, or commented)
	 */
	REVIEWED,

	/**
	 * Pull request was converted to draft
	 */
	DRAFT,

	/**
	 * Draft pull request was marked as ready for review
	 */
	READY_FOR_REVIEW
}
