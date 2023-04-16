/*******************************************************************************
 * Copyright (c) 2023 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php 
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/

package org.eclipse.rdf4j.http.server.repository.transaction;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.rdf4j.common.webapp.views.SimpleResponseView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author jeen
 *
 */
public class PingController extends AbstractActionController {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Override
	protected ModelAndView handleAction(HttpServletRequest request, HttpServletResponse response,
			Transaction transaction) throws Exception {

		String text = Long.toString(ActiveTransactionRegistry.INSTANCE.getTimeout(TimeUnit.MILLISECONDS));
		Map<String, String> model = Collections.singletonMap(SimpleResponseView.CONTENT_KEY, text);
		var result = new ModelAndView(SimpleResponseView.getInstance(), model);
		return result;
	}

}
