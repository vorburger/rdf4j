/*******************************************************************************
 * Copyright (c) 2018 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.query.algebra.evaluation.function.geosparql;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.GEO;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class GeometricUnaryFunctionTest {

	Literal amsterdam = SimpleValueFactory.getInstance().createLiteral("POINT(4.9 52.37)", GEO.WKT_LITERAL);

	protected abstract GeometricUnaryFunction testedFunction();

	@Test
	public void testRelationExceptionHandling() {
		GeometricUnaryFunction testedFunction = Mockito.spy(testedFunction());
		Mockito.doThrow(new RuntimeException("forsooth!")).when(testedFunction).operation(Mockito.any());
		assertThrows(ValueExprEvaluationException.class,
				() -> testedFunction.evaluate(SimpleValueFactory.getInstance(), amsterdam));
	}

}
