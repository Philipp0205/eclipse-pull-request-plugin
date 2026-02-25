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
package org.eclipse.egit.pullrequest.internal.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Describes the capabilities of a pull request provider, indicating what
 * features are supported by the platform (Bitbucket, GitHub, etc.)
 */
public class PullRequestProviderCapabilities {

	private final boolean supportsTaskSeverity;

	private final boolean supportsCommentState;

	private final boolean supportsReviewSubmission;

	private final boolean supportsRequestChanges;

	private final List<String> supportedStates;

	/**
	 * Creates a new capabilities descriptor
	 *
	 * @param supportsTaskSeverity
	 *            whether the provider supports marking comments as tasks with
	 *            severity levels (NORMAL, BLOCKER)
	 * @param supportsCommentState
	 *            whether the provider supports comment state (OPEN, RESOLVED)
	 * @param supportsReviewSubmission
	 *            whether the provider supports submitting formal reviews
	 *            (approve, request changes, comment)
	 * @param supportsRequestChanges
	 *            whether the provider supports the "request changes" review
	 *            action
	 * @param supportedStates
	 *            the PR states supported by this provider (e.g., OPEN, MERGED,
	 *            DECLINED for Bitbucket; OPEN, CLOSED for GitHub)
	 */
	public PullRequestProviderCapabilities(boolean supportsTaskSeverity,
			boolean supportsCommentState, boolean supportsReviewSubmission,
			boolean supportsRequestChanges, String... supportedStates) {
		this.supportsTaskSeverity = supportsTaskSeverity;
		this.supportsCommentState = supportsCommentState;
		this.supportsReviewSubmission = supportsReviewSubmission;
		this.supportsRequestChanges = supportsRequestChanges;
		this.supportedStates = Collections
				.unmodifiableList(Arrays.asList(supportedStates));
	}

	/**
	 * @return true if the provider supports marking comments as tasks with
	 *         severity levels (NORMAL, BLOCKER)
	 */
	public boolean supportsTaskSeverity() {
		return supportsTaskSeverity;
	}

	/**
	 * @return true if the provider supports comment state management (OPEN,
	 *         RESOLVED)
	 */
	public boolean supportsCommentState() {
		return supportsCommentState;
	}

	/**
	 * @return true if the provider supports submitting formal reviews (approve,
	 *         request changes, comment)
	 */
	public boolean supportsReviewSubmission() {
		return supportsReviewSubmission;
	}

	/**
	 * @return true if the provider supports the "request changes" review action
	 */
	public boolean supportsRequestChanges() {
		return supportsRequestChanges;
	}

	/**
	 * @return the list of PR states supported by this provider (e.g., OPEN,
	 *         MERGED, DECLINED for Bitbucket; OPEN, CLOSED for GitHub)
	 */
	public List<String> getSupportedStates() {
		return supportedStates;
	}

	/**
	 * Creates provider capabilities for the given provider type
	 *
	 * @param providerType
	 *            the provider type
	 * @return the capabilities for that provider
	 */
	public static PullRequestProviderCapabilities forProvider(
			PullRequestProviderType providerType) {
		switch (providerType) {
		case BITBUCKET:
			return new PullRequestProviderCapabilities(true, true, true, true,
					"OPEN", //$NON-NLS-1$
					"MERGED", "DECLINED", "ALL"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		case GITHUB:
			return new PullRequestProviderCapabilities(false, true, true, true,
					"open", //$NON-NLS-1$
					"closed", "all"); //$NON-NLS-1$ //$NON-NLS-2$
		default:
			return new PullRequestProviderCapabilities(false, false, false,
					false, "OPEN"); //$NON-NLS-1$
		}
	}
}
