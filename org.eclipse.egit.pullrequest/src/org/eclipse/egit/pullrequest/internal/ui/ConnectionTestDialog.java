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

import org.eclipse.core.runtime.Platform;
import org.eclipse.egit.pullrequest.internal.PRText;
import org.eclipse.egit.pullrequest.internal.client.ConnectionDiagnostics;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Shows the outcome of a provider connection test step by step, so that a
 * failure can be read and copied without leaving the preference page.
 */
public class ConnectionTestDialog extends TitleAreaDialog {

	private static final int COPY_ID = IDialogConstants.CLIENT_ID + 1;

	private final ConnectionDiagnostics diagnostics;

	private final String report;

	/**
	 * Creates the dialog.
	 *
	 * @param shell
	 *            the parent shell
	 * @param diagnostics
	 *            the diagnostics to present
	 */
	public ConnectionTestDialog(Shell shell,
			ConnectionDiagnostics diagnostics) {
		super(shell);
		this.diagnostics = diagnostics;
		this.report = diagnostics.toReport();
		setShellStyle(getShellStyle() | SWT.RESIZE);
		setHelpAvailable(false);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(PRText.ConnectionTest_DialogTitle);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Composite composite = new Composite(area, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		GridDataFactory.fillDefaults().grab(true, true).applyTo(composite);

		setTitle(PRText.ConnectionTest_DialogTitle);
		if (diagnostics.isSuccessful()) {
			setMessage(PRText.ConnectionTest_Success);
		} else {
			setMessage(MessageFormat.format(PRText.ConnectionTest_Failure,
					diagnostics.getSummary()), IMessageProvider.ERROR);
		}

		Text reportText = new Text(composite, SWT.MULTI | SWT.READ_ONLY
				| SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		reportText.setText(report);
		reportText.setFont(JFaceResources.getTextFont());
		GridDataFactory.fillDefaults().grab(true, true).hint(700, 320)
				.applyTo(reportText);

		Label hint = new Label(composite, SWT.WRAP);
		hint.setText(MessageFormat.format(PRText.ConnectionTest_LogHint,
				Platform.getLogFileLocation().toOSString()));
		GridDataFactory.fillDefaults().grab(true, false).hint(700, SWT.DEFAULT)
				.applyTo(hint);

		return area;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, COPY_ID, PRText.ConnectionTest_CopyButton, false)
				.setToolTipText(PRText.ConnectionTest_CopiedTooltip);
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
				true);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == COPY_ID) {
			Clipboard clipboard = new Clipboard(getShell().getDisplay());
			try {
				clipboard.setContents(new Object[] { report },
						new Transfer[] { TextTransfer.getInstance() });
			} finally {
				clipboard.dispose();
			}
			return;
		}
		super.buttonPressed(buttonId);
	}
}
