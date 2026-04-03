package org.eclipse.egit.pullrequest.internal.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Multi-line input dialog for entering comment replies with a larger,
 * resizable text area.
 */
public class MultiLineInputDialog extends Dialog {

	private String title;

	private String message;

	private String value = ""; //$NON-NLS-1$

	private Text textControl;

	/**
	 * Creates a new multi-line input dialog.
	 *
	 * @param parentShell
	 *            the parent shell
	 * @param dialogTitle
	 *            the dialog title
	 * @param dialogMessage
	 *            the message displayed above the text area
	 * @param initialValue
	 *            the initial text value
	 */
	public MultiLineInputDialog(Shell parentShell, String dialogTitle,
			String dialogMessage, String initialValue) {
		super(parentShell);
		this.title = dialogTitle;
		this.message = dialogMessage;
		if (initialValue != null) {
			this.value = initialValue;
		}
		// Enable resizing
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		if (title != null) {
			shell.setText(title);
		}
		// Set minimum size
		shell.setMinimumSize(400, 200);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		GridLayoutFactory.swtDefaults().applyTo(composite);

		// Message label
		if (message != null) {
			Label label = new Label(composite, SWT.WRAP);
			label.setText(message);
			GridDataFactory.fillDefaults().grab(true, false)
					.hint(350, SWT.DEFAULT).applyTo(label);
		}

		// Multi-line text field
		textControl = new Text(composite,
				SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		textControl.setText(value);
		GridDataFactory.fillDefaults().grab(true, true).hint(400, 150)
				.applyTo(textControl);

		return composite;
	}

	@Override
	protected void okPressed() {
		value = textControl.getText();
		super.okPressed();
	}

	/**
	 * Returns the text entered by the user.
	 *
	 * @return the entered text
	 */
	public String getValue() {
		return value;
	}
}
