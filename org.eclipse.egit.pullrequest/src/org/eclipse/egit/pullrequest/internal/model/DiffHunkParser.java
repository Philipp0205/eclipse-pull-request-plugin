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
import java.util.HashSet;
import java.util.Set;

/**
 * Parses unified diff patch strings to extract the set of valid
 * (commentable) line numbers for each side of a pull request diff.
 * <p>
 * The GitHub API only accepts inline comments on lines that appear
 * within the diff hunks. This parser extracts those line numbers
 * from the patch text returned by the GitHub "List pull request
 * files" endpoint.
 * </p>
 * <p>
 * For the RIGHT (new file) side, valid lines are context lines
 * (prefixed with a space) and added lines (prefixed with
 * {@code +}). For the LEFT (old file) side, valid lines are
 * context lines and removed lines (prefixed with {@code -}).
 * </p>
 */
public final class DiffHunkParser {

	/**
	 * Result of parsing a unified diff patch, containing the sets
	 * of valid line numbers for each side.
	 */
	public static class DiffLines {

		private final Set<Integer> leftLines;

		private final Set<Integer> rightLines;

		/**
		 * Creates a new DiffLines result.
		 *
		 * @param leftLines
		 *            the valid line numbers for the LEFT
		 *            (old file) side
		 * @param rightLines
		 *            the valid line numbers for the RIGHT
		 *            (new file) side
		 */
		DiffLines(Set<Integer> leftLines,
				Set<Integer> rightLines) {
			this.leftLines = Collections
					.unmodifiableSet(leftLines);
			this.rightLines = Collections
					.unmodifiableSet(rightLines);
		}

		/**
		 * Returns the valid line numbers for the LEFT (old
		 * file) side.
		 *
		 * @return unmodifiable set of 1-based line numbers
		 */
		public Set<Integer> getLeftLines() {
			return leftLines;
		}

		/**
		 * Returns the valid line numbers for the RIGHT (new
		 * file) side.
		 *
		 * @return unmodifiable set of 1-based line numbers
		 */
		public Set<Integer> getRightLines() {
			return rightLines;
		}
	}

	private DiffHunkParser() {
		// utility class
	}

	/**
	 * Parses a unified diff patch string and extracts the valid
	 * line numbers for each side.
	 * <p>
	 * The patch format is the standard unified diff format:
	 * </p>
	 *
	 * <pre>
	 * {@literal @}@ -oldStart,oldCount +newStart,newCount @@
	 *  context line
	 * -removed line
	 * +added line
	 * </pre>
	 *
	 * @param patch
	 *            the unified diff patch string, may be
	 *            {@code null}
	 * @return the parsed diff lines, or empty sets if patch is
	 *         {@code null} or empty
	 */
	public static DiffLines parse(String patch) {
		Set<Integer> leftLines = new HashSet<>();
		Set<Integer> rightLines = new HashSet<>();

		if (patch == null || patch.isEmpty()) {
			return new DiffLines(leftLines, rightLines);
		}

		String[] lines = patch.split("\n"); //$NON-NLS-1$
		int oldLine = 0;
		int newLine = 0;

		for (String line : lines) {
			if (line.startsWith("@@")) { //$NON-NLS-1$
				// Parse hunk header:
				// @@ -oldStart,oldCount +newStart,newCount @@
				int[] header = parseHunkHeader(line);
				if (header != null) {
					oldLine = header[0];
					newLine = header[1];
				}
			} else if (line.startsWith("-")) { //$NON-NLS-1$
				// Removed line: valid on LEFT side only
				leftLines.add(Integer.valueOf(oldLine));
				oldLine++;
			} else if (line.startsWith("+")) { //$NON-NLS-1$
				// Added line: valid on RIGHT side only
				rightLines.add(Integer.valueOf(newLine));
				newLine++;
			} else {
				// Context line (starts with space or is
				// empty): valid on both sides
				leftLines.add(Integer.valueOf(oldLine));
				rightLines.add(Integer.valueOf(newLine));
				oldLine++;
				newLine++;
			}
		}

		return new DiffLines(leftLines, rightLines);
	}

	/**
	 * Parses a hunk header line to extract the starting line
	 * numbers.
	 *
	 * @param header
	 *            the hunk header line (e.g.
	 *            {@code "@@ -1,5 +1,7 @@"})
	 * @return an array of {@code [oldStart, newStart]}, or
	 *         {@code null} if parsing fails
	 */
	static int[] parseHunkHeader(String header) {
		// Format: @@ -oldStart[,oldCount] +newStart[,newCount] @@
		int minusIdx = header.indexOf('-');
		if (minusIdx == -1) {
			return null;
		}

		int plusIdx = header.indexOf('+', minusIdx);
		if (plusIdx == -1) {
			return null;
		}

		int secondAt = header.indexOf("@@", 2); //$NON-NLS-1$
		if (secondAt == -1) {
			secondAt = header.length();
		}

		try {
			// Extract old start line
			String oldPart = header.substring(
					minusIdx + 1, plusIdx).trim();
			int oldComma = oldPart.indexOf(',');
			int oldStart;
			if (oldComma != -1) {
				oldStart = Integer
						.parseInt(oldPart.substring(0, oldComma));
			} else {
				oldStart = Integer.parseInt(oldPart);
			}

			// Extract new start line
			String newPart = header.substring(
					plusIdx + 1, secondAt).trim();
			int newComma = newPart.indexOf(',');
			int newStart;
			if (newComma != -1) {
				newStart = Integer
						.parseInt(newPart.substring(0, newComma));
			} else {
				newStart = Integer.parseInt(newPart);
			}

			return new int[] { oldStart, newStart };
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
