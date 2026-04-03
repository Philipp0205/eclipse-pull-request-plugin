package org.eclipse.egit.pullrequest.internal.ui;

import org.eclipse.compare.ITypedElement;
import org.eclipse.swt.graphics.Image;

/**
 * Empty typed element for use in compare editors when a file doesn't exist
 */
public class EmptyTypedElement implements ITypedElement {

	private String name;

	/**
	 * Creates a new empty typed element
	 *
	 * @param name
	 *            the name used for display
	 */
	public EmptyTypedElement(String name) {
		this.name = name;
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getType() {
		return ITypedElement.UNKNOWN_TYPE;
	}

}
