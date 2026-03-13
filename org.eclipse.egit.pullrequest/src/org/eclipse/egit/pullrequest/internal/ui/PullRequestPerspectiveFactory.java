/*******************************************************************************
 * Copyright (c) 2025 and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.egit.pullrequest.internal.ui;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

/**
 * Pull Request Review perspective factory
 */
public class PullRequestPerspectiveFactory implements IPerspectiveFactory {

	/**
	 * The perspective ID
	 */
	public static final String ID = "org.eclipse.egit.pullrequest.PullRequestReviewPerspective"; //$NON-NLS-1$

	@Override
	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();

		// Pull Request List on the top-left (30% width)
		layout.addView(
				PullRequestListView.VIEW_ID,
				IPageLayout.LEFT,
				0.3f,
				editorArea);

		// Changed Files and Commits Views below the PR List on the left
		// (60% height of left area) in a tabbed folder
		IFolderLayout leftBottom = layout.createFolder(
				"leftBottom", //$NON-NLS-1$
				IPageLayout.BOTTOM,
				0.6f,
				PullRequestListView.VIEW_ID);
		leftBottom.addView(PullRequestChangedFilesView.VIEW_ID);
		leftBottom.addView(PullRequestCommitsView.VIEW_ID);

		// Window->Show View shortcuts
		layout.addShowViewShortcut(PullRequestListView.VIEW_ID);
		layout.addShowViewShortcut(PullRequestChangedFilesView.VIEW_ID);
		layout.addShowViewShortcut(PullRequestCommitsView.VIEW_ID);

		// Window->Perspective->Open shortcuts
		layout.addPerspectiveShortcut("org.eclipse.egit.ui.GitRepositoriesPerspective"); //$NON-NLS-1$
		layout.addPerspectiveShortcut("org.eclipse.ui.resourcePerspective"); //$NON-NLS-1$
		layout.addPerspectiveShortcut("org.eclipse.team.ui.TeamSynchronizingPerspective"); //$NON-NLS-1$
	}
}
