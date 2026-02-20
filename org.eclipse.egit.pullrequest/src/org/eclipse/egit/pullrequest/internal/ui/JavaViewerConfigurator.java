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

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.contentmergeviewer.ITokenComparator;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.compare.JavaTokenComparator;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;

/**
 * Helper class to configure SourceViewer instances with Java syntax
 * highlighting and partitioning. This class is isolated to ensure that JDT
 * classes are only loaded when actually used, allowing graceful degradation
 * when JDT is not available.
 */
@SuppressWarnings("restriction")
class JavaViewerConfigurator {

	/**
	 * Configures a SourceViewer with Java syntax highlighting.
	 *
	 * @param sourceViewer
	 *            the source viewer to configure
	 * @param documentPartitioning
	 *            the document partitioning ID (typically
	 *            {@link IJavaPartitions#JAVA_PARTITIONING})
	 */
	static void configure(SourceViewer sourceViewer,
			String documentPartitioning) {
		JavaTextTools tools = getJavaTextTools();
		if (tools == null) {
			return;
		}

		IPreferenceStore preferenceStore = createPreferenceStore();
		JavaSourceViewerConfiguration configuration = new JavaSourceViewerConfiguration(
				tools.getColorManager(), preferenceStore, null,
				documentPartitioning);

		sourceViewer.unconfigure();
		sourceViewer.configure(configuration);
	}

	/**
	 * Creates a document partitioner for Java source files.
	 *
	 * @return the Java document partitioner, or {@code null} if JDT is not
	 *         available
	 */
	static IDocumentPartitioner createJavaPartitioner() {
		JavaTextTools tools = getJavaTextTools();
		if (tools != null) {
			return tools.createDocumentPartitioner();
		}
		return null;
	}

	/**
	 * Creates a token comparator for Java source files.
	 *
	 * @param line
	 *            the line to compare
	 * @param ignoreWhitespace
	 *            whether to ignore whitespace
	 * @return the Java token comparator
	 */
	static ITokenComparator createJavaTokenComparator(String line,
			boolean ignoreWhitespace) {
		return new JavaTokenComparator(line, ignoreWhitespace);
	}

	/**
	 * Returns the Java document partitioning ID.
	 *
	 * @return {@link IJavaPartitions#JAVA_PARTITIONING}
	 */
	static String getJavaPartitioning() {
		return IJavaPartitions.JAVA_PARTITIONING;
	}

	private static JavaTextTools getJavaTextTools() {
		JavaPlugin plugin = JavaPlugin.getDefault();
		if (plugin != null) {
			return plugin.getJavaTextTools();
		}
		return null;
	}

	private static IPreferenceStore createPreferenceStore() {
		IPreferenceStore[] stores = new IPreferenceStore[2];
		stores[0] = JavaPlugin.getDefault().getPreferenceStore();
		stores[1] = EditorsUI.getPreferenceStore();
		return new ChainedPreferenceStore(stores);
	}
}
