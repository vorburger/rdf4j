/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.sail.memory.model;

import org.eclipse.rdf4j.model.BNode;

/**
 * A MemoryStore-specific extension of BNodeImpl giving it node properties.
 */
public class MemBNode extends MemResource implements BNode {

	private static final long serialVersionUID = -887382892580321647L;

	/**
	 * The object that created this MemBNode.
	 */
	transient final private Object creator;

	/**
	 * The blank node's identifier.
	 */
	private final String id;

	/**
	 * Creates a new MemBNode for a bnode ID.
	 *
	 * @param creator The object that is creating this MemBNode.
	 * @param id      bnode ID.
	 */
	public MemBNode(Object creator, String id) {
		this.id = id;
		this.creator = creator;
	}

	/*---------*
	 * Methods *
	 *---------*/

	@Override
	public Object getCreator() {
		return creator;
	}

	@Override
	public boolean hasStatements() {
		return !subjectStatements.isEmpty() || !objectStatements.isEmpty() || !contextStatements.isEmpty();
	}

	@Override
	public boolean hasPredicateStatements() {
		return false;
	}

	@Override
	public MemStatementList getPredicateStatementList() {
		return EMPTY_LIST;
	}

	@Override
	public int getPredicateStatementCount() {
		return 0;
	}

	@Override
	public void addPredicateStatement(MemStatement st) throws InterruptedException {
		// no-op
	}

	@Override
	public void cleanSnapshotsFromPredicateStatements(int currentSnapshot) throws InterruptedException {
		// no-op
	}

	@Override
	public String getID() {
		return id;
	}

	@Override
	public String stringValue() {
		return getID();
	}

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof BNode
				&& getID().equals(((BNode) o).getID());
	}

	@Override
	public int hashCode() {
		return getID().hashCode();
	}

	@Override
	public String toString() {
		return "_:" + getID();
	}
}
