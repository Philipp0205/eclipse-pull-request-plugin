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
package org.eclipse.egit.pullrequest.internal.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics.Outcome;
import org.junit.Test;

/**
 * Tests for {@link ConnectionDiagnostics}
 */
public class ConnectionDiagnosticsTest {

	@Test
	public void testEmptyReportIsSuccessful() {
		ConnectionDiagnostics diagnostics = new ConnectionDiagnostics();

		assertThat(diagnostics.isSuccessful(), is(true));
		assertThat(diagnostics.getFirstFailure(), nullValue());
		assertThat(diagnostics.getSteps(), hasSize(0));
	}

	@Test
	public void testWarningDoesNotFailTheReport() {
		ConnectionDiagnostics diagnostics = new ConnectionDiagnostics();
		diagnostics.add("Server URL", Outcome.WARNING, "plain HTTP"); //$NON-NLS-1$ //$NON-NLS-2$

		assertThat(diagnostics.isSuccessful(), is(true));
		assertThat(diagnostics.getFirstFailure(), nullValue());
	}

	@Test
	public void testFirstFailureIsReported() {
		ConnectionDiagnostics diagnostics = new ConnectionDiagnostics();
		diagnostics.add("Name resolution", Outcome.OK, "resolves to 10.0.0.1"); //$NON-NLS-1$ //$NON-NLS-2$
		diagnostics.add("Authentication", Outcome.FAILED, "token rejected"); //$NON-NLS-1$ //$NON-NLS-2$
		diagnostics.add("Repository", Outcome.FAILED, "not found"); //$NON-NLS-1$ //$NON-NLS-2$

		assertThat(diagnostics.isSuccessful(), is(false));
		assertThat(diagnostics.getFirstFailure(), notNullValue());
		assertThat(diagnostics.getFirstFailure().getName(),
				equalTo("Authentication")); //$NON-NLS-1$
		assertThat(diagnostics.getSummary(),
				equalTo("Authentication: token rejected")); //$NON-NLS-1$
	}

	@Test
	public void testReportContainsOutcomeAndDetail() {
		ConnectionDiagnostics diagnostics = new ConnectionDiagnostics();
		diagnostics.add("Authentication", Outcome.FAILED, //$NON-NLS-1$
				"token rejected"); //$NON-NLS-1$

		String report = diagnostics.toReport();

		assertThat(report, containsString("FAILED")); //$NON-NLS-1$
		assertThat(report, containsString("Authentication")); //$NON-NLS-1$
		assertThat(report, containsString("token rejected")); //$NON-NLS-1$
	}

	@Test
	public void testNullDetailIsTolerated() {
		ConnectionDiagnostics diagnostics = new ConnectionDiagnostics();
		diagnostics.add("Configuration", Outcome.OK, null); //$NON-NLS-1$

		assertThat(diagnostics.getSteps().get(0).getDetail(), equalTo("")); //$NON-NLS-1$
		assertThat(diagnostics.toReport(),
				containsString("Configuration")); //$NON-NLS-1$
	}
}
