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
package org.eclipse.egit.pullrequest.internal.ui;

import java.text.MessageFormat;

import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.model.PullRequest;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;

/**
 * Editor input for the pull request overview editor.
 */
public class PullRequestOverviewEditorInput implements IEditorInput {

	private final PullRequest pullRequest;

	/**
	 * Creates a new editor input for a pull request overview.
	 *
	 * @param pullRequest
	 *            the pull request to display
	 */
	public PullRequestOverviewEditorInput(PullRequest pullRequest) {
		this.pullRequest = pullRequest;
	}

	/**
	 * Gets the pull request.
	 *
	 * @return the pull request
	 */
	public PullRequest getPullRequest() {
		return pullRequest;
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return null;
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return null;
	}

	@Override
	public String getName() {
		return MessageFormat.format(PRText.OverviewView_TitleFormat,
				Long.valueOf(pullRequest.getId()));
	}

	@Override
	public IPersistableElement getPersistable() {
		return null;
	}

	@Override
	public String getToolTipText() {
		return pullRequest.getTitle();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PullRequestOverviewEditorInput)) {
			return false;
		}
		PullRequestOverviewEditorInput other =
				(PullRequestOverviewEditorInput) obj;
		return pullRequest.getId() == other.pullRequest.getId();
	}

	@Override
	public int hashCode() {
		return Long.hashCode(pullRequest.getId());
	}
}
