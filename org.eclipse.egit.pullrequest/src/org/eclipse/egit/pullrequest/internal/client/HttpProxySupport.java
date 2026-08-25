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
package org.eclipse.egit.pullrequest.internal.client;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * Resolves the proxy to use for provider requests from the Eclipse network
 * settings (Preferences &gt; General &gt; Network Connections).
 * <p>
 * {@link URL#openConnection()} only sees proxies that were pushed into the JVM
 * system properties, so an Eclipse-only proxy configuration would otherwise be
 * ignored and requests to a server that is reachable through the proxy would
 * time out. Hosts on the "no proxy for" list resolve to a direct connection.
 */
public final class HttpProxySupport {

	private HttpProxySupport() {
		// No instantiation
	}

	/**
	 * Selects the proxy Eclipse is configured to use for the given URL.
	 *
	 * @param url
	 *            the target URL
	 * @return the proxy to use, or null to connect directly
	 */
	@Nullable
	public static Proxy select(@NonNull URL url) {
		IProxyData data = selectProxyData(url);
		if (data == null || data.getHost() == null) {
			return null;
		}
		int port = data.getPort() > 0 ? data.getPort() : 80;
		Proxy.Type type = IProxyData.SOCKS_PROXY_TYPE.equals(data.getType())
				? Proxy.Type.SOCKS
				: Proxy.Type.HTTP;
		return new Proxy(type,
				InetSocketAddress.createUnresolved(data.getHost(), port));
	}

	/**
	 * Describes how requests to the given URL are routed, for use in a
	 * diagnostic report.
	 *
	 * @param url
	 *            the target URL
	 * @return a human readable description of the route
	 */
	@NonNull
	public static String describe(@NonNull URL url) {
		IProxyData data = selectProxyData(url);
		if (data == null || data.getHost() == null) {
			return "direct connection (no Eclipse proxy applies to this host)"; //$NON-NLS-1$
		}
		return "via " + data.getType() + " proxy " + data.getHost() + ':' //$NON-NLS-1$ //$NON-NLS-2$
				+ data.getPort()
				+ (data.isRequiresAuthentication()
						? " (requires authentication)" //$NON-NLS-1$
						: ""); //$NON-NLS-1$
	}

	private static IProxyData selectProxyData(URL url) {
		Bundle bundle = FrameworkUtil.getBundle(HttpProxySupport.class);
		if (bundle == null) {
			return null;
		}
		BundleContext context = bundle.getBundleContext();
		if (context == null) {
			return null;
		}
		ServiceReference<IProxyService> reference = context
				.getServiceReference(IProxyService.class);
		if (reference == null) {
			return null;
		}
		try {
			IProxyService service = context.getService(reference);
			if (service == null || !service.isProxiesEnabled()) {
				return null;
			}
			IProxyData[] candidates = service.select(toUri(url));
			return candidates == null || candidates.length == 0 ? null
					: candidates[0];
		} catch (URISyntaxException | IllegalArgumentException e) {
			return null;
		} finally {
			context.ungetService(reference);
		}
	}

	private static URI toUri(URL url) throws URISyntaxException {
		return new URI(url.getProtocol(), null, url.getHost(), url.getPort(),
				null, null, null);
	}
}
