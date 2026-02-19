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
