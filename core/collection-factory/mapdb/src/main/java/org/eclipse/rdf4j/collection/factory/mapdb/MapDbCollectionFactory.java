/*******************************************************************************
 * Copyright (c) 2022 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.collection.factory.mapdb;

import java.io.IOError;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.rdf4j.collection.factory.api.BindingSetKey;
import org.eclipse.rdf4j.collection.factory.api.CollectionFactory;
import org.eclipse.rdf4j.collection.factory.impl.DefaultCollectionFactory;
import org.eclipse.rdf4j.common.exception.RDF4JException;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

public class MapDbCollectionFactory implements CollectionFactory {
	protected volatile DB db;
	protected volatile long colectionId = 0;
	protected final long iterationCacheSyncThreshold;
	private final CollectionFactory delegate;
//	private File tempFile;

	private static final class RDF4jMapDBException extends RDF4JException {

		private static final long serialVersionUID = 1L;

		public RDF4jMapDBException(String string, Throwable e) {
			super(string, e);
		}

	}

	public MapDbCollectionFactory(long iterationCacheSyncThreshold) {
		this(iterationCacheSyncThreshold, new DefaultCollectionFactory());
	}

	public MapDbCollectionFactory(long iterationCacheSyncThreshold, CollectionFactory delegate) {

		this.iterationCacheSyncThreshold = iterationCacheSyncThreshold;
		this.delegate = delegate;
	}

	protected void init() {
		if (this.db == null) {
			synchronized (this) {
				if (this.db == null) {
					try {
						this.db = DBMaker.newTempFileDB()
								.deleteFilesAfterClose()
								.closeOnJvmShutdown()
								.commitFileSyncDisable()
								.make();
					} catch (IOError e) {
						throw new RDF4jMapDBException("could not initialize temp db", e);
					}
				}
			}
		}
	}

	@Override
	public <T> List<T> createList() {
		return delegate.createList();
	}

	@Override
	public List<Value> createValueList() {
		return delegate.createValueList();
	}

	@Override
	public Set<BindingSet> createSetOfBindingSets() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			MemoryTillSizeXSet<BindingSet> set = new MemoryTillSizeXSet<>(colectionId++,
					delegate.createSetOfBindingSets());
			return new CommitingSet<>(set, iterationCacheSyncThreshold, db);
		} else {
			return delegate.createSetOfBindingSets();
		}
	}

	@Override
	public <T> Set<T> createSet() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			MemoryTillSizeXSet<T> set = new MemoryTillSizeXSet<T>(colectionId++, delegate.createSet());
			return new CommitingSet<T>(set, iterationCacheSyncThreshold, db);
		} else {
			return delegate.createSet();
		}
	}

	@Override
	public Set<Value> createValueSet() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			Set<Value> set = new MemoryTillSizeXSet<>(colectionId++, delegate.createValueSet());
			return new CommitingSet<Value>(set, iterationCacheSyncThreshold, db);
		} else {
			return delegate.createValueSet();
		}
	}

	@Override
	public <K, V> Map<K, V> createMap() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			return new CommitingMap<>(db.createHashMap(Long.toHexString(colectionId++)).make(),
					iterationCacheSyncThreshold, db);
		} else {
			return delegate.createMap();
		}
	}

	@Override
	public <V> Map<Value, V> createValueKeyedMap() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			return new CommitingMap<>(db.createHashMap(Long.toHexString(colectionId++)).make(),
					iterationCacheSyncThreshold, db);
		} else {
			return delegate.createValueKeyedMap();
		}
	}

	@Override
	public <T> Queue<T> createQueue() {
		return delegate.createQueue();
	}

	@Override
	public Queue<Value> createValueQueue() {
		return delegate.createValueQueue();
	}

	@Override
	public void close() throws RDF4JException {
		if (db != null) {
			db.close();
		}
	}

	@Override
	public <E> Map<BindingSetKey, E> createGroupByMap() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			return new CommitingMap<>(db.createHashMap(Long.toHexString(colectionId++)).make(),
					iterationCacheSyncThreshold, db);
		} else {
			return delegate.createGroupByMap();
		}
	}

	@Override
	public BindingSetKey createBindingSetKey(BindingSet bindingSet, List<Function<BindingSet, Value>> getValues) {
		return delegate.createBindingSetKey(bindingSet, getValues);
	}

	protected static final class CommitingSet<T> extends AbstractSet<T> {
		private final Set<T> wrapped;
		private final long iterationCacheSyncThreshold;
		private final DB db;
		private long iterationCount;

		public CommitingSet(Set<T> wrapped, long iterationCacheSyncThreshold, DB db) {
			super();
			this.wrapped = wrapped;
			this.iterationCacheSyncThreshold = iterationCacheSyncThreshold;
			this.db = db;
		}

		@Override
		public boolean add(T e) {

			boolean res = wrapped.add(e);
			if (iterationCount++ % iterationCacheSyncThreshold == 0) {
				// write to disk every $iterationCacheSyncThreshold items
				db.commit();
			}
			return res;

		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			int preinsertSize = wrapped.size();
			boolean res = wrapped.addAll(c);
			int inserted = preinsertSize - c.size();
			if (inserted + iterationCount > iterationCacheSyncThreshold) {
				// write to disk every $iterationCacheSyncThreshold items
				db.commit();
				iterationCount = 0;
			} else {
				iterationCount += inserted;
			}
			return res;
		}

		@Override
		public Iterator<T> iterator() {
			return wrapped.iterator();
		}

		@Override
		public int size() {
			return wrapped.size();
		}
	}

	protected static final class CommitingMap<K, V> extends AbstractMap<K, V> {
		private final Map<K, V> wrapped;
		private final long iterationCacheSyncThreshold;
		private final DB db;
		private long iterationCount;

		public CommitingMap(Map<K, V> wrapped, long iterationCacheSyncThreshold, DB db) {
			super();
			this.wrapped = wrapped;
			this.iterationCacheSyncThreshold = iterationCacheSyncThreshold;
			this.db = db;
		}

		@Override
		public V put(K k, V v) {

			V res = wrapped.put(k, v);
			if (iterationCount++ % iterationCacheSyncThreshold == 0) {
				// write to disk every $iterationCacheSyncThreshold items
				db.commit();
			}
			return res;

		}

		@Override
		public int size() {
			return wrapped.size();
		}

		@Override
		public Set<Entry<K, V>> entrySet() {
			return wrapped.entrySet();
		}
	}

	/**
	 * Only create a disk based set once the contents are large enough that it starts to pay off.
	 *
	 * @param <T> of the contents of the set.
	 */
	public class MemoryTillSizeXSet<V> extends AbstractSet<V> {
		private Set<V> wrapped;
		private final long setName;
		private Serializer<V> serializer;

		@SuppressWarnings("unchecked")
		public MemoryTillSizeXSet(long setName, Set<V> wrapped) {
			this(setName, wrapped, db.getDefaultSerializer());
		}

		public MemoryTillSizeXSet(long setName, Set<V> wrapped, Serializer<V> serializer) {
			super();
			this.setName = setName;
			this.wrapped = wrapped;
			this.serializer = serializer;
		}

		@Override
		public boolean add(V e) {
			if (wrapped instanceof HashSet && wrapped.size() > iterationCacheSyncThreshold) {
				Set<V> disk = makeDiskBasedSet();
				disk.addAll(wrapped);
				wrapped = disk;
			}
			return wrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends V> arg0) {
			if (wrapped instanceof HashSet && arg0.size() > iterationCacheSyncThreshold) {
				Set<V> disk = makeDiskBasedSet();
				disk.addAll(wrapped);
				wrapped = disk;
			}
			return wrapped.addAll(arg0);
		}

		private Set<V> makeDiskBasedSet() {
			return db.createHashSet(Long.toHexString(setName)).serializer(serializer).make();
		}

		@Override
		public void clear() {
			wrapped.clear();
		}

		@Override
		public boolean contains(Object o) {
			return wrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> arg0) {
			return wrapped.containsAll(arg0);
		}

		@Override
		public boolean isEmpty() {
			return wrapped.isEmpty();
		}

		@Override
		public boolean remove(Object o) {
			return wrapped.remove(o);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return wrapped.retainAll(c);
		}

		@Override
		public Object[] toArray() {
			return wrapped.toArray();
		}

		@Override
		public <T> T[] toArray(T[] arg0) {
			return wrapped.toArray(arg0);
		}

		@Override
		public Iterator<V> iterator() {
			return wrapped.iterator();
		}

		@Override
		public int size() {
			return wrapped.size();
		}

	}

}
