/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.testsuite.query.parser.sparql.manifest;

import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.rdf4j.common.text.StringUtil;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.util.RDFInserter;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base functionality for SPARQL compliance test suites using a W3C-style Manifest.
 *
 * @author Jeen Broekstra
 */
public abstract class SPARQLComplianceTest {

	private static final Logger logger = LoggerFactory.getLogger(SPARQLComplianceTest.class);
	private static final AtomicInteger tempDirNameForRepoCounter = new AtomicInteger();
	private List<String> ignoredTests = new ArrayList<>();

	protected File newTempDir(File folder) {
		File tmpDirPerRepo = new File(folder, "tmpDirPerRepo" + tempDirNameForRepoCounter.getAndIncrement());
		if (!tmpDirPerRepo.mkdir()) {
			fail("Could not create temporary directory for test");
		}
		return tmpDirPerRepo;
	}

	public SPARQLComplianceTest() {
	}

	/**
	 * @param displayName
	 * @param testURI
	 * @param name
	 */
	protected abstract class DynamicSparqlComplianceTest {
		private final String displayName;
		private final String testURI;
		private final String name;

		public DynamicSparqlComplianceTest(String displayName, String testURI, String name) {
			this.displayName = displayName;
			this.testURI = testURI;
			this.name = name;
		}

		public void test() {
			assumeThat(getIgnoredTests().contains(getName())).withFailMessage("test case '%s' is ignored", getName())
					.isFalse();
			try {
				setUp();
				runTest();
			} catch (Exception e) {
				fail("Exception", e);
			} finally {
				try {
					tearDown();
				} catch (Exception e2) {
					fail("Failed during teardown", e2);
				}
			}
		}

		/**
		 * @return the displayName
		 */
		public String getDisplayName() {
			return displayName;
		}

		/**
		 * @return the testURI
		 */
		public String getTestURI() {
			return testURI;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		protected void uploadDataset(Dataset dataset) throws Exception {
			try (RepositoryConnection con = getDataRepository().getConnection()) {
				// Merge default and named graphs to filter duplicates
				Set<IRI> graphURIs = new HashSet<>();
				graphURIs.addAll(dataset.getDefaultGraphs());
				graphURIs.addAll(dataset.getNamedGraphs());

				for (Resource graphURI : graphURIs) {
					upload(((IRI) graphURI), graphURI);
				}
			}
		}

		protected abstract Repository getDataRepository();

		protected void upload(IRI graphURI, Resource context) throws Exception {

			RepositoryConnection con = getDataRepository().getConnection();
			try {
				con.begin();
				RDFFormat rdfFormat = Rio.getParserFormatForFileName(graphURI.toString()).orElse(RDFFormat.TURTLE);
				RDFParser rdfParser = Rio.createParser(rdfFormat, getDataRepository().getValueFactory());
				// rdfParser.setPreserveBNodeIDs(true);

				RDFInserter rdfInserter = new RDFInserter(con);
				rdfInserter.enforceContext(context);
				rdfParser.setRDFHandler(rdfInserter);

				URL graphURL = new URL(graphURI.toString());
				try (InputStream in = graphURL.openStream()) {
					rdfParser.parse(in, graphURI.toString());
				}

				con.commit();
			} catch (Exception e) {
				if (con.isActive()) {
					con.rollback();
				}
				throw e;
			} finally {
				con.close();
			}
		}

		protected void compareGraphs(Iterable<Statement> queryResult, Iterable<Statement> expectedResult)
				throws Exception {
			if (!Models.isomorphic(expectedResult, queryResult)) {
				StringBuilder message = new StringBuilder(128);
				message.append("\n============ ");
				message.append(getName());
				message.append(" =======================\n");
				message.append("Expected result: \n");
				for (Statement st : expectedResult) {
					message.append(st.toString());
					message.append("\n");
				}
				message.append("=============");
				StringUtil.appendN('=', getName().length(), message);
				message.append("========================\n");

				message.append("Query result: \n");
				for (Statement st : queryResult) {
					message.append(st.toString());
					message.append("\n");
				}
				message.append("=============");
				StringUtil.appendN('=', getName().length(), message);
				message.append("========================\n");

				logger.error(message.toString());
				fail(message.toString());
			}
		}

		protected abstract void runTest() throws Exception;

		public abstract void tearDown() throws Exception;

		public abstract void setUp() throws Exception;

		protected void clear(Repository repo) {
			try (RepositoryConnection con = repo.getConnection()) {
				con.clear();
				con.clearNamespaces();
			}
		}
	}

	protected static final void printBindingSet(BindingSet bs, StringBuilder appendable) {
		List<String> names = new ArrayList<>(bs.getBindingNames());
		Collections.sort(names);

		for (String name : names) {
			if (bs.hasBinding(name)) {
				appendable.append(bs.getBinding(name));
				appendable.append(' ');
			}
		}
		appendable.append("\n");
	}

	/**
	 * Verifies if the selected subManifest occurs in the supplied list of excluded subdirs.
	 *
	 * @param subManifestFile the url of a sub-manifest
	 * @param excludedSubdirs an array of directory names. May be null.
	 * @return <code>false</code> if the supplied list of excluded subdirs is not empty and contains a match for the
	 *         supplied sub-manifest, <code>true</code> otherwise.
	 */
	protected static boolean includeSubManifest(String subManifestFile, List<String> excludedSubdirs) {
		boolean result = true;

		if (excludedSubdirs != null && !excludedSubdirs.isEmpty()) {
			int index = subManifestFile.lastIndexOf('/');
			String path = subManifestFile.substring(0, index);
			String sd = path.substring(path.lastIndexOf('/') + 1);

			for (String subdir : excludedSubdirs) {
				if (sd.equals(subdir)) {
					result = false;
					break;
				}
			}
		}
		return result;
	}

	/**
	 * @return the ignoredTests
	 */
	protected List<String> getIgnoredTests() {
		return ignoredTests;
	}

	protected void addIgnoredTest(String ignoredTest) {
		this.ignoredTests.add(ignoredTest);
	}

	protected boolean shouldIgnoredTest(String ignoredTest) {
		return this.ignoredTests.contains(ignoredTest);
	}

	/**
	 * @param ignoredTests the ignoredTests to set
	 */
	protected void setIgnoredTests(List<String> ignoredTests) {
		this.ignoredTests = ignoredTests;
	}

}
