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
package org.eclipse.egit.pullrequest;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.pullrequest.internal.PRPreferences;
import org.eclipse.egit.pullrequest.internal.ui.AvatarCache;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class for the pull request review plugin
 */
public class Activator extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "org.eclipse.egit.pullrequest"; //$NON-NLS-1$
	private static Activator plugin;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		AvatarCache.getInstance().dispose();
		plugin = null;
		super.stop(context);
	}

	/**
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given plug-in
	 * relative path
	 *
	 * @param path
	 *            the pathcontinue
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	/**
	 * Log an error message
	 *
	 * @param message
	 *            the message
	 * @param exception
	 *            the exception
	 */
	public static void logError(String message, Throwable exception) {
		ILog log = Platform.getLog(Activator.class);
		log.log(new Status(IStatus.ERROR, PLUGIN_ID, message, exception));
	}

	/**
	 * Log a warning message
	 *
	 * @param message
	 *            the message
	 */
	public static void logWarning(String message) {
		ILog log = Platform.getLog(Activator.class);
		log.log(new Status(IStatus.WARNING, PLUGIN_ID, message));
	}

	/**
	 * Log an info message
	 *
	 * @param message
	 *            the message
	 */
	public static void logInfo(String message) {
		ILog log = Platform.getLog(Activator.class);
		log.log(new Status(IStatus.INFO, PLUGIN_ID, message));
	}

	/**
	 * Log a message that is only of interest when diagnosing a problem.
	 * <p>
	 * Nothing is written unless the user enabled verbose logging on the pull
	 * request preference page. Messages end up in the same place as all other
	 * entries of this plug-in: the Error Log view and the workspace log file
	 * reported by {@link Platform#getLogFileLocation()}.
	 *
	 * @param message
	 *            the message
	 */
	public static void logDebug(String message) {
		if (!isVerboseLoggingEnabled()) {
			return;
		}
		ILog log = Platform.getLog(Activator.class);
		log.log(new Status(IStatus.INFO, PLUGIN_ID, message));
	}

	/**
	 * Tells whether verbose logging of provider requests is enabled.
	 *
	 * @return true if {@link #logDebug(String)} writes to the log
	 */
	public static boolean isVerboseLoggingEnabled() {
		Activator activator = plugin;
		if (activator == null) {
			return false;
		}
		return activator.getPreferenceStore()
				.getBoolean(PRPreferences.PULLREQUEST_VERBOSE_LOGGING);
	}

	@Override
	public IPreferenceStore getPreferenceStore() {
		IPreferenceStore store = super.getPreferenceStore();
		store.setDefault(
				PRPreferences.PULLREQUEST_ANIMATE_INLINE_COMMENTS,
				true);
		store.setDefault(
				PRPreferences.PULLREQUEST_EXPAND_COMMENTS_BY_DEFAULT,
				true);
		return store;
	}
}
