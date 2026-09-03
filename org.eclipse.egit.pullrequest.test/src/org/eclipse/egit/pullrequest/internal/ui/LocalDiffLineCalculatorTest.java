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
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.File;
import java.nio.file.Files;

import org.eclipse.egit.pullrequest.internal.model.DiffHunkParser.DiffLines;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link LocalDiffLineCalculator}.
 */
public class LocalDiffLineCalculatorTest {

	/**
	 * Temporary repository location.
	 */
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void testCalculatesLinesFromLocalCommitRange() throws Exception {
		File directory = temporaryFolder.newFolder("repository"); //$NON-NLS-1$
		try (Git git = Git.init().setDirectory(directory).call()) {
			File file = new File(directory, "example.txt"); //$NON-NLS-1$
			Files.writeString(file.toPath(), "one\ntwo\nthree\n"); //$NON-NLS-1$
			git.add().addFilepattern("example.txt").call(); //$NON-NLS-1$
			RevCommit base = commit(git, "base"); //$NON-NLS-1$

			Files.writeString(file.toPath(),
					"one\ntwo\nadded\nthree\n"); //$NON-NLS-1$
			git.add().addFilepattern("example.txt").call(); //$NON-NLS-1$
			RevCommit head = commit(git, "head"); //$NON-NLS-1$

			DiffLines lines = LocalDiffLineCalculator.calculate(
					git.getRepository(), base.getName(), head.getName(),
					"example.txt"); //$NON-NLS-1$

			assertThat(lines.getLeftLines(),
					containsInAnyOrder(1, 2, 3));
			assertThat(lines.getRightLines(),
					containsInAnyOrder(1, 2, 3, 4));
		}
	}

	private static RevCommit commit(Git git, String message)
			throws Exception {
		return git.commit().setMessage(message)
				.setAuthor("Test User", "test@example.com") //$NON-NLS-1$ //$NON-NLS-2$
				.setCommitter("Test User", "test@example.com") //$NON-NLS-1$ //$NON-NLS-2$
				.call();
	}
}
