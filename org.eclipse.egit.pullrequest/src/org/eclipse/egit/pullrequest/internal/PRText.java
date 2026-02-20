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
package org.eclipse.egit.pullrequest.internal;

import org.eclipse.osgi.util.NLS;

/**
 * Externalized text for pull request review plugin
 */
public class PRText extends NLS {

	/** */
	public static String CommitFileDiffViewer_OpenWorkingTreeVersionInEditorMenuLabel;

	/** */
	public static String StagingView_CopyPaths;

	/** */
	public static String ChangedFilesView_MarkAllUnread;

	static {
		initializeMessages("org.eclipse.egit.pullrequest.internal.prtext", //$NON-NLS-1$
				PRText.class);
	}

	private PRText() {
		// No instantiation
	}
}
