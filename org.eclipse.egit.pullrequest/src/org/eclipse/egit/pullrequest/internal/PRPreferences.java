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
package org.eclipse.egit.pullrequest.internal;

/**
 * Preference constants for pull request review
 */
public class PRPreferences {

	/** Provider type preference */
	public static final String PULLREQUEST_PROVIDER_TYPE = "pullrequest_provider_type"; //$NON-NLS-1$

	/** Bitbucket server URL */
	public static final String BITBUCKET_SERVER_URL = "bitbucket_server_url"; //$NON-NLS-1$

	/** Bitbucket project key */
	public static final String BITBUCKET_PROJECT_KEY = "bitbucket_project_key"; //$NON-NLS-1$

	/** Bitbucket repository slug */
	public static final String BITBUCKET_REPO_SLUG = "bitbucket_repo_slug"; //$NON-NLS-1$

	/** Bitbucket access token */
	public static final String BITBUCKET_ACCESS_TOKEN = "bitbucket_access_token"; //$NON-NLS-1$

	/** Bitbucket username */
	public static final String BITBUCKET_USERNAME = "bitbucket_username"; //$NON-NLS-1$

	/** GitHub owner */
	public static final String GITHUB_OWNER = "github_owner"; //$NON-NLS-1$

	/** GitHub repository */
	public static final String GITHUB_REPO = "github_repo"; //$NON-NLS-1$

	/** GitHub access token */
	public static final String GITHUB_ACCESS_TOKEN = "github_access_token"; //$NON-NLS-1$

	/** Sash weights for changes view */
	public static final String PULLREQUEST_CHANGES_SASH_WEIGHTS = "pullrequest_changes_sash_weights"; //$NON-NLS-1$

	/** Show inline comments preference */
	public static final String PULLREQUEST_SHOW_INLINE_COMMENTS = "pullrequest_show_inline_comments"; //$NON-NLS-1$

	/** Animate inline comments preference */
	public static final String PULLREQUEST_ANIMATE_INLINE_COMMENTS = "pullrequest_animate_inline_comments"; //$NON-NLS-1$

	private PRPreferences() {
		// No instantiation
	}
}
