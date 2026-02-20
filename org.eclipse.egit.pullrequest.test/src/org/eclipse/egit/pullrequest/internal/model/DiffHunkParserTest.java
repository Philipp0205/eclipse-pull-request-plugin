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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Set;

import org.eclipse.egit.pullrequest.internal.model.DiffHunkParser.DiffLines;
import org.junit.Test;

/**
 * Tests for {@link DiffHunkParser}
 */
public class DiffHunkParserTest {

	@Test
	public void testParseNullPatch() {
		DiffLines result = DiffHunkParser.parse(null);

		assertThat(result, notNullValue());
		assertThat(result.getLeftLines(), hasSize(0));
		assertThat(result.getRightLines(), hasSize(0));
	}

	@Test
	public void testParseEmptyPatch() {
		DiffLines result = DiffHunkParser.parse(""); //$NON-NLS-1$

		assertThat(result, notNullValue());
		assertThat(result.getLeftLines(), hasSize(0));
		assertThat(result.getRightLines(), hasSize(0));
	}

	@Test
	public void testParseSingleHunkAddedLines() {
		// Hunk that adds 2 lines at line 5 of the new file
		String patch = "@@ -4,3 +4,5 @@\n" //$NON-NLS-1$
				+ " context line 4\n" //$NON-NLS-1$
				+ "+added line 5\n" //$NON-NLS-1$
				+ "+added line 6\n" //$NON-NLS-1$
				+ " context line 5\n" //$NON-NLS-1$
				+ " context line 6"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		// LEFT side: context lines at old 4, 5, 6
		Set<Integer> left = result.getLeftLines();
		assertThat(left, hasSize(3));
		assertThat(left, hasItem(Integer.valueOf(4)));
		assertThat(left, hasItem(Integer.valueOf(5)));
		assertThat(left, hasItem(Integer.valueOf(6)));

		// RIGHT side: context at new 4, added 5 and 6,
		// context at new 7 and 8
		Set<Integer> right = result.getRightLines();
		assertThat(right, hasSize(5));
		assertThat(right, hasItem(Integer.valueOf(4)));
		assertThat(right, hasItem(Integer.valueOf(5)));
		assertThat(right, hasItem(Integer.valueOf(6)));
		assertThat(right, hasItem(Integer.valueOf(7)));
		assertThat(right, hasItem(Integer.valueOf(8)));
	}

	@Test
	public void testParseSingleHunkRemovedLines() {
		// Hunk that removes 2 lines from old file at line 5-6
		String patch = "@@ -4,5 +4,3 @@\n" //$NON-NLS-1$
				+ " context line 4\n" //$NON-NLS-1$
				+ "-removed line 5\n" //$NON-NLS-1$
				+ "-removed line 6\n" //$NON-NLS-1$
				+ " context line 7\n" //$NON-NLS-1$
				+ " context line 8"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		// LEFT side: context 4, removed 5, removed 6,
		// context 7, context 8
		Set<Integer> left = result.getLeftLines();
		assertThat(left, hasSize(5));
		assertThat(left, hasItem(Integer.valueOf(4)));
		assertThat(left, hasItem(Integer.valueOf(5)));
		assertThat(left, hasItem(Integer.valueOf(6)));
		assertThat(left, hasItem(Integer.valueOf(7)));
		assertThat(left, hasItem(Integer.valueOf(8)));

		// RIGHT side: context at new 4, 5, 6
		Set<Integer> right = result.getRightLines();
		assertThat(right, hasSize(3));
		assertThat(right, hasItem(Integer.valueOf(4)));
		assertThat(right, hasItem(Integer.valueOf(5)));
		assertThat(right, hasItem(Integer.valueOf(6)));
	}

	@Test
	public void testParseSingleHunkMixedChanges() {
		// Hunk with context, removed, and added lines
		String patch = "@@ -10,4 +10,4 @@\n" //$NON-NLS-1$
				+ " context\n" //$NON-NLS-1$
				+ "-old line\n" //$NON-NLS-1$
				+ "+new line\n" //$NON-NLS-1$
				+ " context"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		// LEFT: context at 10, removed at 11, context at 12
		// (old line 13 = context after removed+added)
		Set<Integer> left = result.getLeftLines();
		assertThat(left, hasSize(3));
		assertThat(left, hasItem(Integer.valueOf(10)));
		assertThat(left, hasItem(Integer.valueOf(11)));
		assertThat(left, hasItem(Integer.valueOf(12)));

		// RIGHT: context at 10, added at 11, context at 12
		Set<Integer> right = result.getRightLines();
		assertThat(right, hasSize(3));
		assertThat(right, hasItem(Integer.valueOf(10)));
		assertThat(right, hasItem(Integer.valueOf(11)));
		assertThat(right, hasItem(Integer.valueOf(12)));
	}

	@Test
	public void testParseMultipleHunks() {
		// Two separate hunks in one patch
		String patch = "@@ -1,3 +1,3 @@\n" //$NON-NLS-1$
				+ " line 1\n" //$NON-NLS-1$
				+ "-old line 2\n" //$NON-NLS-1$
				+ "+new line 2\n" //$NON-NLS-1$
				+ " line 3\n" //$NON-NLS-1$
				+ "@@ -20,3 +20,4 @@\n" //$NON-NLS-1$
				+ " line 20\n" //$NON-NLS-1$
				+ "+added line 21\n" //$NON-NLS-1$
				+ " line 21\n" //$NON-NLS-1$
				+ " line 22"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		// LEFT: hunk1 context 1, removed 2, context 3;
		//        hunk2 context 20, context 21, context 22
		Set<Integer> left = result.getLeftLines();
		assertThat(left, hasSize(6));
		assertThat(left, hasItem(Integer.valueOf(1)));
		assertThat(left, hasItem(Integer.valueOf(2)));
		assertThat(left, hasItem(Integer.valueOf(3)));
		assertThat(left, hasItem(Integer.valueOf(20)));
		assertThat(left, hasItem(Integer.valueOf(21)));
		assertThat(left, hasItem(Integer.valueOf(22)));

		// RIGHT: hunk1 context 1, added 2, context 3;
		//         hunk2 context 20, added 21, context 22,
		//         context 23
		Set<Integer> right = result.getRightLines();
		assertThat(right, hasSize(7));
		assertThat(right, hasItem(Integer.valueOf(1)));
		assertThat(right, hasItem(Integer.valueOf(2)));
		assertThat(right, hasItem(Integer.valueOf(3)));
		assertThat(right, hasItem(Integer.valueOf(20)));
		assertThat(right, hasItem(Integer.valueOf(21)));
		assertThat(right, hasItem(Integer.valueOf(22)));
		assertThat(right, hasItem(Integer.valueOf(23)));
	}

	@Test
	public void testParseHunkHeaderWithCounts() {
		int[] result = DiffHunkParser
				.parseHunkHeader("@@ -10,5 +20,7 @@"); //$NON-NLS-1$

		assertThat(result, notNullValue());
		assertThat(result[0], equalTo(10));
		assertThat(result[1], equalTo(20));
	}

	@Test
	public void testParseHunkHeaderWithoutCounts() {
		// Single-line hunk (no comma counts)
		int[] result = DiffHunkParser
				.parseHunkHeader("@@ -1 +1 @@"); //$NON-NLS-1$

		assertThat(result, notNullValue());
		assertThat(result[0], equalTo(1));
		assertThat(result[1], equalTo(1));
	}

	@Test
	public void testParseHunkHeaderMixedCounts() {
		// Old side has count, new side has no count
		int[] result = DiffHunkParser
				.parseHunkHeader("@@ -5,3 +8 @@"); //$NON-NLS-1$

		assertThat(result, notNullValue());
		assertThat(result[0], equalTo(5));
		assertThat(result[1], equalTo(8));
	}

	@Test
	public void testParseHunkHeaderWithSectionHeading() {
		// GitHub often includes the function/section name
		// after the closing @@
		int[] result = DiffHunkParser.parseHunkHeader(
				"@@ -100,7 +105,9 @@ public void doSomething()"); //$NON-NLS-1$

		assertThat(result, notNullValue());
		assertThat(result[0], equalTo(100));
		assertThat(result[1], equalTo(105));
	}

	@Test
	public void testParseHunkHeaderMalformedNoMinus() {
		int[] result = DiffHunkParser
				.parseHunkHeader("@@ +1,5 @@"); //$NON-NLS-1$

		// The '-' comes from the @@ itself, so this depends
		// on implementation - but shouldn't crash
		// parseHunkHeader looks for '-' then '+', result may
		// be null or unexpected
	}

	@Test
	public void testParseHunkHeaderMalformedNoPlus() {
		int[] result = DiffHunkParser
				.parseHunkHeader("@@ -1,5 @@"); //$NON-NLS-1$

		assertThat(result, nullValue());
	}

	@Test
	public void testParseHunkHeaderMalformedGarbage() {
		int[] result = DiffHunkParser
				.parseHunkHeader("not a header"); //$NON-NLS-1$

		assertThat(result, nullValue());
	}

	@Test
	public void testParseRealWorldPatch() {
		// A realistic patch from GitHub's API:
		// File had line 1-3 unchanged, line 4 changed,
		// line 5 unchanged
		String patch = "@@ -1,5 +1,5 @@\n" //$NON-NLS-1$
				+ " import java.util.List;\n" //$NON-NLS-1$
				+ " import java.util.Map;\n" //$NON-NLS-1$
				+ " import java.util.Set;\n" //$NON-NLS-1$
				+ "-import java.util.ArrayList;\n" //$NON-NLS-1$
				+ "+import java.util.LinkedList;\n" //$NON-NLS-1$
				+ " import java.util.HashMap;"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		// LEFT: 1,2,3 (context), 4 (removed), 5 (context)
		Set<Integer> left = result.getLeftLines();
		assertThat(left, hasSize(5));
		for (int i = 1; i <= 5; i++) {
			assertThat(left, hasItem(Integer.valueOf(i)));
		}

		// RIGHT: 1,2,3 (context), 4 (added), 5 (context)
		Set<Integer> right = result.getRightLines();
		assertThat(right, hasSize(5));
		for (int i = 1; i <= 5; i++) {
			assertThat(right, hasItem(Integer.valueOf(i)));
		}
	}

	@Test
	public void testParsePatchOnlyAddedLines() {
		// New file scenario: only added lines
		String patch = "@@ -0,0 +1,3 @@\n" //$NON-NLS-1$
				+ "+line 1\n" //$NON-NLS-1$
				+ "+line 2\n" //$NON-NLS-1$
				+ "+line 3"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		// LEFT: no valid lines (old file didn't exist)
		assertThat(result.getLeftLines(), hasSize(0));

		// RIGHT: lines 1, 2, 3
		Set<Integer> right = result.getRightLines();
		assertThat(right, hasSize(3));
		assertThat(right, hasItem(Integer.valueOf(1)));
		assertThat(right, hasItem(Integer.valueOf(2)));
		assertThat(right, hasItem(Integer.valueOf(3)));
	}

	@Test
	public void testParsePatchOnlyRemovedLines() {
		// Deleted file scenario: only removed lines
		String patch = "@@ -1,3 +0,0 @@\n" //$NON-NLS-1$
				+ "-line 1\n" //$NON-NLS-1$
				+ "-line 2\n" //$NON-NLS-1$
				+ "-line 3"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		// LEFT: lines 1, 2, 3
		Set<Integer> left = result.getLeftLines();
		assertThat(left, hasSize(3));
		assertThat(left, hasItem(Integer.valueOf(1)));
		assertThat(left, hasItem(Integer.valueOf(2)));
		assertThat(left, hasItem(Integer.valueOf(3)));

		// RIGHT: no valid lines (new file doesn't exist)
		assertThat(result.getRightLines(), hasSize(0));
	}

	@Test
	public void testDiffLinesUnmodifiable() {
		String patch = "@@ -1,2 +1,2 @@\n" //$NON-NLS-1$
				+ " context\n" //$NON-NLS-1$
				+ "-old\n" //$NON-NLS-1$
				+ "+new"; //$NON-NLS-1$

		DiffLines result = DiffHunkParser.parse(patch);

		try {
			result.getLeftLines().add(Integer.valueOf(999));
			// Should have thrown UnsupportedOperationException
			assertThat("Expected exception", equalTo("not thrown")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (UnsupportedOperationException e) {
			// Expected: sets are unmodifiable
		}

		try {
			result.getRightLines().add(Integer.valueOf(999));
			assertThat("Expected exception", equalTo("not thrown")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (UnsupportedOperationException e) {
			// Expected: sets are unmodifiable
		}
	}
}
