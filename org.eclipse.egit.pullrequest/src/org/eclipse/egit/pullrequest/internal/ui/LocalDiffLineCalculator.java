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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.egit.pullrequest.internal.model.DiffHunkParser;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

/**
 * Calculates commentable lines from the local Git objects used by a
 * Synchronize comparison.
 */
final class LocalDiffLineCalculator {

	private static final String EMPTY_TREE = "^tree"; //$NON-NLS-1$

	private LocalDiffLineCalculator() {
		// Utility class
	}

	/**
	 * Calculates commentable lines for one repository-relative path.
	 *
	 * @param repository
	 *            the local repository
	 * @param baseRevision
	 *            the base revision, or {@code ^tree} for an empty tree
	 * @param headRevision
	 *            the head revision
	 * @param path
	 *            the repository-relative path
	 * @return commentable lines, or empty line sets when no entry matches
	 * @throws IOException
	 *             if Git objects cannot be read
	 */
	static DiffHunkParser.DiffLines calculate(Repository repository,
			String baseRevision, String headRevision, String path)
			throws IOException {
		ByteArrayOutputStream patch = new ByteArrayOutputStream();
		try (ObjectReader reader = repository.newObjectReader();
				DiffFormatter formatter = new DiffFormatter(patch)) {
			formatter.setRepository(repository);
			formatter.setDetectRenames(true);
			AbstractTreeIterator base = treeIterator(repository, reader,
					baseRevision);
			AbstractTreeIterator head = treeIterator(repository, reader,
					headRevision);
			List<DiffEntry> entries = formatter.scan(base, head);
			for (DiffEntry entry : entries) {
				if (path.equals(entry.getNewPath())
						|| path.equals(entry.getOldPath())) {
					formatter.format(entry);
					break;
				}
			}
		}
		return DiffHunkParser.parse(
				patch.toString(StandardCharsets.UTF_8));
	}

	private static AbstractTreeIterator treeIterator(Repository repository,
			ObjectReader reader, String revision) throws IOException {
		if (EMPTY_TREE.equals(revision)) {
			return new EmptyTreeIterator();
		}
		ObjectId tree = repository.resolve(revision + "^{tree}"); //$NON-NLS-1$
		if (tree == null) {
			throw new IOException("Cannot resolve revision " + revision); //$NON-NLS-1$
		}
		CanonicalTreeParser parser = new CanonicalTreeParser();
		parser.reset(reader, tree);
		return parser;
	}
}
