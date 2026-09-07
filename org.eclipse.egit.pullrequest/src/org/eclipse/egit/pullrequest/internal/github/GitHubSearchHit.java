/*******************************************************************************
 * Copyright (C) 2026, Philipp0205 and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.github;

import java.util.Date;

/**
 * One pull request in a GitHub issue search response.
 * <p>
 * The search tells which pull requests exist and when they changed, which is
 * all that is needed to order them and to pick the page the caller asked for.
 * Only that page is then read in full.
 *
 * @param path
 *            REST path of the pull request, for example
 *            {@code /repos/owner/name/pulls/7}
 * @param updated
 *            when the pull request was last updated, or {@code null} if the
 *            search did not report it
 */
record GitHubSearchHit(String path, Date updated) {
}
