/*******************************************************************************
 * Copyright (c) 2021 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.sail.lmdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.eclipse.rdf4j.sail.SailLockedException;
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LmdbStoreDirLockTest {

	@Test
	public void testLocking(@TempDir File dataDir) throws Exception {
		LmdbStore sail = new LmdbStore(dataDir, new LmdbStoreConfig("spoc,posc"));
		sail.init();

		try {
			LmdbStore sail2 = new LmdbStore(dataDir, new LmdbStoreConfig("spoc,posc"));
			sail2.init();
			try {
				fail("initialized a second lmdb store with same dataDir");
			} finally {
				sail2.shutDown();
			}
		} catch (SailLockedException e) {
			// Expected: should not be able to open two lmdb stores with the
			// same dataDir
			assertNotNull(e);
		} finally {
			sail.shutDown();
		}
	}
}
