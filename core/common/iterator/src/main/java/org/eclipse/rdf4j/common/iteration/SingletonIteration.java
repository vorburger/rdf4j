/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.common.iteration;

import java.util.NoSuchElementException;

/**
 * An Iteration that contains exactly one element.
 */
public class SingletonIteration<E, X extends Exception> implements CloseableIteration<E, X> {

	private E value;

	public SingletonIteration(E value) {
		this.value = value;
	}

	@Override
	public boolean hasNext() {
		return value != null;
	}

	@Override
	public E next() throws X {
		if (value == null) {
			throw new NoSuchElementException();
		}
		E temp = value;
		value = null;
		return temp;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public final void close() throws X {
		value = null;
	}

}
