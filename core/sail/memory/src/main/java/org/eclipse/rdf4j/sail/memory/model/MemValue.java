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

import org.eclipse.rdf4j.model.Value;

/**
 * A MemoryStore-specific extension of the Value interface, giving it node properties.
 */
public interface MemValue extends Value {

	/*-----------*
	 * Constants *
	 *-----------*/

	/**
	 * A shared empty MemStatementList that is returned by MemIRI and MemBNode to represent an empty list. The use of a
	 * shared list reduces memory usage.
	 */
	MemStatementList EMPTY_LIST = new MemStatementList(0);

	/*---------*
	 * Methods *
	 *---------*/

	/**
	 * Returns the object that created this MemValue. MemValues are only unique within a single repository, but an
	 * application could use several repositories at the same time, passing MemValues generated by one Sail to another
	 * Sail. In such situations, the MemValue of the first Sail cannot be used by the second Sail.
	 */
	Object getCreator();

	/**
	 * Checks whether this MemValue has any statements. A MemValue object has statements if there is at least one
	 * statement where it is used as the subject, predicate, object or context value.
	 *
	 * @return <var>true</var> if the MemValue has statements, <var>false</var> otherwise.
	 */
	boolean hasStatements();

	/**
	 * Gets the list of statements for which this MemValue is the object.
	 *
	 * @return A MemStatementList containing the statements.
	 */
	MemStatementList getObjectStatementList();

	/**
	 * Gets the number of statements for which this MemValue is the object.
	 *
	 * @return An integer larger than or equal to 0.
	 */
	int getObjectStatementCount();

	/**
	 * Adds a statement to this MemValue's list of statements for which it is the object.
	 */
	void addObjectStatement(MemStatement st) throws InterruptedException;

	/**
	 * Removes a statement from this MemValue's list of statements for which it is the object.
	 */
	void removeObjectStatement(MemStatement st) throws InterruptedException;

	/**
	 * Removes statements from old snapshots (those that have expired at or before the specified snapshot version) from
	 * this MemValue's list of statements for which it is the object.
	 *
	 * @param currentSnapshot The current snapshot version.
	 */
	void cleanSnapshotsFromObjectStatements(int currentSnapshot) throws InterruptedException;

	boolean hasSubjectStatements();

	boolean hasPredicateStatements();

	boolean hasObjectStatements();

	boolean hasContextStatements();
}
