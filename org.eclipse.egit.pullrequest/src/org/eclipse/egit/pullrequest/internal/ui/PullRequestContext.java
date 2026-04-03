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

import org.eclipse.egit.pullrequest.internal.model.PullRequest;

/**
 * Lightweight singleton service for tracking the currently active pull request
 * in the workbench.
 * <p>
 * This allows different views and editors to coordinate around a shared PR
 * context without tight coupling (e.g., comment overlay installers can
 * retrieve the active PR without depending on a specific view).
 */
public class PullRequestContext {

	private static final PullRequestContext INSTANCE = new PullRequestContext();

	private PullRequest activePullRequest;

	private PullRequestContext() {
		// Singleton
	}

	/**
	 * Gets the singleton instance.
	 *
	 * @return the instance
	 */
	public static PullRequestContext getInstance() {
		return INSTANCE;
	}

	/**
	 * Sets the currently active pull request.
	 *
	 * @param pullRequest
	 *            the pull request, or null to clear
	 */
	public void setActivePullRequest(PullRequest pullRequest) {
		this.activePullRequest = pullRequest;
	}

	/**
	 * Gets the currently active pull request.
	 *
	 * @return the pull request, or null if none is active
	 */
	public PullRequest getActivePullRequest() {
		return activePullRequest;
	}
}
