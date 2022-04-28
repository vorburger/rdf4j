/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.repository.sail;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.sail.SailException;

/**
 * @author Herko ter Horst
 */
class SailCloseableIteration<E> implements CloseableIteration<E, RepositoryException> {

	/**
	 * The underlying Iteration.
	 */
	private final CloseableIteration<? extends E, ? extends SailException> iter;
	/**
	 * Flag indicating whether this iteration has been closed.
	 */
	private boolean closed = false;

	public SailCloseableIteration(CloseableIteration<? extends E, ? extends SailException> iter) {
		this.iter = Objects.requireNonNull(iter, "The iterator was null");
	}

	private RepositoryException convert(Exception e) {
		if (e instanceof SailException) {
			return new RepositoryException(e);
		} else if (e instanceof RuntimeException) {
			throw (RuntimeException) e;
		} else if (e == null) {
			throw new IllegalArgumentException("e must not be null");
		} else {
			throw new IllegalArgumentException("Unexpected exception type: " + e.getClass());
		}
	}

	/**
	 * Checks whether the underlying Iteration contains more elements.
	 *
	 * @return <var>true</var> if the underlying Iteration contains more elements, <var>false</var> otherwise.
	 * @throws RepositoryException
	 */
	@Override
	public final boolean hasNext() throws org.eclipse.rdf4j.repository.RepositoryException {
		if (isClosed()) {
			return false;
		}
		try {
			boolean result = iter.hasNext();
			if (!result) {
				close();
			}
			return result;
		} catch (Exception e) {
			throw convert(e);
		}
	}

	/**
	 * Returns the next element from the wrapped Iteration.
	 *
	 * @throws RepositoryException
	 * @throws java.util.NoSuchElementException If all elements have been returned.
	 * @throws IllegalStateException            If the Iteration has been closed.
	 */
	@Override
	public final E next() throws org.eclipse.rdf4j.repository.RepositoryException {
		if (isClosed()) {
			throw new NoSuchElementException("The iteration has been closed.");
		}
		try {
			return iter.next();
		} catch (NoSuchElementException | IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw convert(e);
		}
	}

	/**
	 * Calls <var>remove()</var> on the underlying Iteration.
	 *
	 * @throws UnsupportedOperationException If the wrapped Iteration does not support the <var>remove</var> operation.
	 * @throws IllegalStateException         If the Iteration has been closed, or if {@link #next} has not yet been
	 *                                       called, or {@link #remove} has already been called after the last call to
	 *                                       {@link #next}.
	 */
	@Override
	public final void remove() throws org.eclipse.rdf4j.repository.RepositoryException {
		if (isClosed()) {
			throw new IllegalStateException("The iteration has been closed.");
		}
		try {
			iter.remove();
		} catch (UnsupportedOperationException | IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw convert(e);
		}
	}

	/**
	 * Checks whether this CloseableIteration has been closed.
	 *
	 * @return <var>true</var> if the CloseableIteration has been closed, <var>false</var> otherwise.
	 */
	@Override
	public final boolean isClosed() {
		return closed;
	}

	/**
	 * Calls {@link #handleClose()} upon first call and makes sure the resource closures are only executed once.
	 */
	@Override
	public final void close() throws org.eclipse.rdf4j.repository.RepositoryException {
		if (!closed) {
			closed = true;
			try {
				iter.close();
			} catch (Exception e) {
				throw convert(e);
			}
		}
	}
}
