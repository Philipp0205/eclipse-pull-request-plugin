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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain model representing a commit in a pull request.
 */
public class PullRequestCommit {

	private final String id;

	private final String message;

	private final String authorName;

	private final String authorEmail;

	private final long authorDate;

	private final List<String> parents;

	/**
	 * Constructs a new pull request commit.
	 *
	 * @param id
	 *            the commit SHA
	 * @param message
	 *            the commit message
	 * @param authorName
	 *            the author name
	 * @param authorEmail
	 *            the author email address
	 * @param authorDate
	 *            the author date in epoch milliseconds
	 * @param parents
	 *            the parent commit SHAs
	 */
	public PullRequestCommit(String id, String message, String authorName,
			String authorEmail, long authorDate, List<String> parents) {
		this.id = id;
		this.message = message;
		this.authorName = authorName;
		this.authorEmail = authorEmail;
		this.authorDate = authorDate;
		this.parents = parents == null ? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(parents));
	}

	/**
	 * @return the commit SHA
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the short form of the commit SHA (first 7 characters)
	 */
	public String getShortId() {
		return id != null && id.length() >= 7 ? id.substring(0, 7) : id;
	}

	/**
	 * @return the full commit message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @return the first line of the commit message
	 */
	public String getFirstLine() {
		if (message == null) {
			return ""; //$NON-NLS-1$
		}
		int newlineIndex = message.indexOf('\n');
		return newlineIndex >= 0 ? message.substring(0, newlineIndex)
				: message;
	}

	/**
	 * @return the author name
	 */
	public String getAuthorName() {
		return authorName;
	}

	/**
	 * @return the author email address
	 */
	public String getAuthorEmail() {
		return authorEmail;
	}

	/**
	 * @return the author date in epoch milliseconds
	 */
	public long getAuthorDate() {
		return authorDate;
	}

	/**
	 * @return the parent commit SHAs
	 */
	public List<String> getParents() {
		return parents;
	}

	/**
	 * @return true if this is a merge commit (more than one parent)
	 */
	public boolean isMergeCommit() {
		return parents != null && parents.size() > 1;
	}

	@Override
	public String toString() {
		return "PullRequestCommit [id=" + getShortId() + ", message=" //$NON-NLS-1$ //$NON-NLS-2$
				+ getFirstLine() + "]"; //$NON-NLS-1$
	}
}
