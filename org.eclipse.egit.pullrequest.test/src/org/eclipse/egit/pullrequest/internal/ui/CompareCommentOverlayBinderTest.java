/*******************************************************************************
 * Copyright (C) 2026, Philipp Hoenisch and contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.List;

import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.junit.Test;

/**
 * Tests for {@link CompareCommentOverlayBinder}.
 */
public class CompareCommentOverlayBinderTest {

	@Test
	public void testFiltersCommentsByNestedRepositoryPath() {
		PullRequestComment matching =
				comment("src/nested/Example.java"); //$NON-NLS-1$
		PullRequestComment other = comment("src/Other.java"); //$NON-NLS-1$
		PullRequestComment general = comment(null);

		List<PullRequestComment> result =
				CompareCommentOverlayBinder.filterComments(
						List.of(matching, other, general),
						"src/nested/Example.java"); //$NON-NLS-1$

		assertThat(result, contains(matching));
	}

	private static PullRequestComment comment(String path) {
		PullRequestComment comment = new PullRequestComment();
		comment.setPath(path);
		return comment;
	}
}
