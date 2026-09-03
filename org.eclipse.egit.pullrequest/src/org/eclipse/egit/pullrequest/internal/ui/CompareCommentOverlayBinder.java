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

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareViewerSwitchingPane;
import org.eclipse.compare.contentmergeviewer.TextMergeViewer;
import org.eclipse.egit.pullrequest.Activator;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.model.DiffHunkParser;
import org.eclipse.egit.pullrequest.internal.model.PullRequestComment;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Installs pull request comment overlays on the compare viewer opened by the
 * stock EGit Synchronize action.
 */
final class CompareCommentOverlayBinder {

	private static final String BINDING_KEY = Activator.PLUGIN_ID
			+ ".compareCommentOverlay"; //$NON-NLS-1$

	private static final int MAX_RETRIES = 20;

	private CompareCommentOverlayBinder() {
		// Utility class
	}

	/**
	 * Binds comments after the stock Synchronize action opens or reuses its
	 * compare editor.
	 *
	 * @param filePath
	 *            the repository-relative file path
	 * @param diffLines
	 *            the locally calculated commentable lines
	 */
	static void bindAfterOpen(String filePath,
			DiffHunkParser.DiffLines diffLines) {
		Display display = Display.getDefault();
		if (display != null && !display.isDisposed()) {
			display.asyncExec(() -> bind(filePath, diffLines, 0));
		}
	}

	static List<PullRequestComment> filterComments(
			List<PullRequestComment> comments, String filePath) {
		return comments.stream()
				.filter(comment -> filePath.equals(comment.getPath()))
				.collect(Collectors.toList());
	}

	private static void bind(String filePath,
			DiffHunkParser.DiffLines diffLines, int retryCount) {
		IWorkbenchWindow window = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow();
		IWorkbenchPage page = window != null ? window.getActivePage() : null;
		IEditorPart editor = page != null ? page.getActiveEditor() : null;
		Viewer viewer = editor != null
				? findContentViewer(editor.getEditorInput()) : null;

		if (viewer == null) {
			retry(filePath, diffLines, retryCount);
			return;
		}
		if (!(viewer instanceof TextMergeViewer)
				|| !Activator.getDefault().getPreferenceStore().getBoolean(
						PRPreferences.PULLREQUEST_SHOW_INLINE_COMMENTS)) {
			return;
		}

		Control control = viewer.getControl();
		if (control == null || control.isDisposed()) {
			retry(filePath, diffLines, retryCount);
			return;
		}

		Object existing = control.getData(BINDING_KEY);
		if (existing instanceof CommentOverlayInstaller) {
			((CommentOverlayInstaller) existing).dispose();
		}

		CommentOverlayInstaller installer =
				new CommentOverlayInstaller(viewer);
		installer.setFilePath(filePath);
		installer.setDiffLines(diffLines);
		installer.installComments(filterComments(
				PullRequestContext.getInstance().getComments(), filePath));
		control.setData(BINDING_KEY, installer);
		control.addDisposeListener(event -> installer.dispose());
	}

	private static Viewer findContentViewer(Object editorInput) {
		if (!(editorInput instanceof CompareEditorInput)) {
			return null;
		}
		try {
			Field field = findField(editorInput.getClass(),
					"fContentInputPane"); //$NON-NLS-1$
			if (field == null) {
				return null;
			}
			field.setAccessible(true);
			Object pane = field.get(editorInput);
			if (pane instanceof CompareViewerSwitchingPane) {
				return ((CompareViewerSwitchingPane) pane).getViewer();
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			Activator.logError(
					"Failed to locate stock compare viewer", e); //$NON-NLS-1$
		}
		return null;
	}

	private static Field findField(Class<?> type, String name) {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				current = current.getSuperclass();
			}
		}
		return null;
	}

	private static void retry(String filePath,
			DiffHunkParser.DiffLines diffLines, int retryCount) {
		if (retryCount >= MAX_RETRIES) {
			Activator.logWarning(
					"Stock compare viewer was not ready for comments"); //$NON-NLS-1$
			return;
		}
		Display display = Display.getDefault();
		if (display != null && !display.isDisposed()) {
			display.timerExec(100,
					() -> bind(filePath, diffLines, retryCount + 1));
		}
	}
}
