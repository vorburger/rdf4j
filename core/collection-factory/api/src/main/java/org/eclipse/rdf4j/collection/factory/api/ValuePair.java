package org.eclipse.rdf4j.collection.factory.api;

import java.io.Serializable;

import org.eclipse.rdf4j.model.Value;

public interface ValuePair extends Serializable {

	/**
	 * @return Returns the startValue.
	 */
	public Value getStartValue();

	/**
	 * @return Returns the endValue.
	 */
	public Value getEndValue();
}