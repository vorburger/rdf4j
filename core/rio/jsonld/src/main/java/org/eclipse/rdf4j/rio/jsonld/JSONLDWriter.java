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
package org.eclipse.rdf4j.rio.jsonld;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.common.io.CharSink;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonWriter;
import no.hasmac.jsonld.serialization.RdfToJsonld;
import no.hasmac.rdf.RdfDataset;
import no.hasmac.rdf.RdfGraph;
import no.hasmac.rdf.RdfLiteral;
import no.hasmac.rdf.RdfNQuad;
import no.hasmac.rdf.RdfResource;
import no.hasmac.rdf.RdfTriple;
import no.hasmac.rdf.RdfValue;

/**
 * An RDFWriter that links to {@link JSONLDInternalRDFParser}.
 *
 * @author Peter Ansell
 */
public class JSONLDWriter extends AbstractRDFWriter implements CharSink {

	private final Model model = new LinkedHashModel();

	private final StatementCollector statementCollector = new StatementCollector(model);

	private final String baseURI;

	private final Writer writer;

	private static final SimpleValueFactory vf = SimpleValueFactory.getInstance();

	/**
	 * Create a JSONLDWriter using a {@link java.io.OutputStream}
	 *
	 * @param outputStream The OutputStream to write to.
	 */
	public JSONLDWriter(OutputStream outputStream) {
		this(outputStream, null);
	}

	/**
	 * Create a JSONLDWriter using a {@link java.io.OutputStream}
	 *
	 * @param outputStream The OutputStream to write to.
	 * @param baseURI      base URI
	 */
	public JSONLDWriter(OutputStream outputStream, String baseURI) {
		this.baseURI = baseURI;
		this.writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
	}

	/**
	 * Create a JSONLDWriter using a {@link java.io.Writer}
	 *
	 * @param writer The Writer to write to.
	 */
	public JSONLDWriter(Writer writer) {
		this(writer, null);
	}

	/**
	 * Create a JSONLDWriter using a {@link java.io.Writer}
	 *
	 * @param writer  The Writer to write to.
	 * @param baseURI base URI
	 */
	public JSONLDWriter(Writer writer, String baseURI) {
		this.baseURI = baseURI;
		this.writer = writer;
	}

	@Override
	public Writer getWriter() {
		return writer;
	}

	@Override
	public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
		checkWritingStarted();
		model.setNamespace(prefix, uri);
	}

	@Override
	public void startRDF() throws RDFHandlerException {
		super.startRDF();
		statementCollector.clear();
		model.clear();
	}

	@Override
	public void endRDF() throws RDFHandlerException {
		checkWritingStarted();
//        final JSONLDInternalRDFParser serialiser = new JSONLDInternalRDFParser();
		try {
//            final JsonLdOptions opts = new JsonLdOptions();
//            // opts.addBlankNodeIDs =
//            // getWriterConfig().get(BasicParserSettings.PRESERVE_BNODE_IDS);
//            WriterConfig writerConfig = getWriterConfig();
//            opts.setCompactArrays(writerConfig.get(JSONLDSettings.COMPACT_ARRAYS));
//            opts.setProduceGeneralizedRdf(writerConfig.get(JSONLDSettings.PRODUCE_GENERALIZED_RDF));
//            opts.setUseRdfType(writerConfig.get(JSONLDSettings.USE_RDF_TYPE));
//            opts.setUseNativeTypes(writerConfig.get(JSONLDSettings.USE_NATIVE_TYPES));
//            // opts.optimize = getWriterConfig().get(JSONLDSettings.OPTIMIZE);
//
//            Object output = JsonLdProcessor.fromRDF(model, opts, serialiser);
//
//            final JSONLDMode mode = getWriterConfig().get(JSONLDSettings.JSONLD_MODE);
//
//            if (writerConfig.get(JSONLDSettings.HIERARCHICAL_VIEW)) {
//                output = JSONLDHierarchicalProcessor.fromJsonLdObject(output);
//            }
//
//            if (baseURI != null && writerConfig.get(BasicWriterSettings.BASE_DIRECTIVE)) {
//                opts.setBase(baseURI);
//            }
//            if (mode == JSONLDMode.EXPAND) {
//                output = JsonLdProcessor.expand(output, opts);
//            }
//            // TODO: Implement inframe in JSONLDSettings
//            final Object inframe = null;
//            if (mode == JSONLDMode.FLATTEN) {
//                output = JsonLdProcessor.flatten(output, inframe, opts);
//            }
//            if (mode == JSONLDMode.COMPACT) {
//                final Map<String, Object> ctx = new LinkedHashMap<>();
//                addPrefixes(ctx, model.getNamespaces());
//                final Map<String, Object> localCtx = new HashMap<>();
//                localCtx.put(JsonLdConsts.CONTEXT, ctx);
//
//                output = JsonLdProcessor.compact(output, localCtx, opts);
//            }
//            if (writerConfig.get(BasicWriterSettings.PRETTY_PRINT)) {
//                JsonUtils.writePrettyPrint(writer, output);
//            } else {
//                JsonUtils.write(writer, output);
//            }

			JsonArray jsonArray = RdfToJsonld.with(new RdfDataset() {
				@Override
				public RdfGraph getDefaultGraph() {
					return new RdfGraph() {
						@Override
						public boolean contains(RdfTriple triple) {
							return model.contains(toRdf4jResource(triple.getSubject()),
									toRdf4jIri(triple.getPredicate()), toRdf4jValue(triple.getObject()),
									new Resource[] { null });
						}

						@Override
						public List<RdfTriple> toList() {
							return model.filter(null, null, null, new Resource[] { null })
									.stream()
									.map(JSONLDWriter::toRdfTriple)
									.collect(Collectors.toList());
						}
					};
				}

				@Override
				public RdfDataset add(RdfNQuad nquad) {
					throw new UnsupportedOperationException();
				}

				@Override
				public RdfDataset add(RdfTriple triple) {
					throw new UnsupportedOperationException();
				}

				@Override
				public List<RdfNQuad> toList() {
					return model.filter(null, null, null)
							.stream()
							.map(JSONLDWriter::toRdfNQuad)
							.collect(Collectors.toList());
				}

				@Override
				public Set<RdfResource> getGraphNames() {
					return model.contexts()
							.stream()
							.filter(Objects::nonNull)
							.map(JSONLDWriter::toRdfResource)
							.collect(Collectors.toSet());
				}

				@Override
				public Optional<RdfGraph> getGraph(RdfResource graphName) {

					Resource context = toRdf4jResource(graphName);

					if (model.contexts().contains(context)) {
						return Optional.of(new RdfGraph() {
							@Override
							public boolean contains(RdfTriple triple) {
								return model.contains(toRdf4jResource(triple.getSubject()),
										toRdf4jIri(triple.getPredicate()), toRdf4jValue(triple.getObject()), context);
							}

							@Override
							public List<RdfTriple> toList() {
								return model.filter(null, null, null, context)
										.stream()
										.map(JSONLDWriter::toRdfTriple)
										.collect(Collectors.toList());
							}
						});
					}

					return Optional.empty();

				}

				@Override
				public int size() {
					return model.size();
				}
			}).build();

			try (JsonWriter jsonWriter = Json.createWriter(writer)) {
				jsonWriter.writeArray(jsonArray);
				writer.flush();
			}

		} catch (JsonLdError | no.hasmac.jsonld.JsonLdError | IOException e) {
			throw new RDFHandlerException("Could not render JSONLD", e);
		}
	}

	private static RdfNQuad toRdfNQuad(Statement statement) {
		return new RdfNQuad() {
			@Override
			public Optional<RdfResource> getGraphName() {
				if (statement.getContext() != null) {
					return Optional.of(toRdfResource(statement.getContext()));
				}
				return Optional.empty();
			}

			@Override
			public RdfResource getSubject() {
				return toRdfResource(statement.getSubject());
			}

			@Override
			public RdfResource getPredicate() {
				return toRdfResource(statement.getPredicate());
			}

			@Override
			public RdfValue getObject() {
				return toRdfValue(statement.getObject());
			}
		};
	}

	private static RdfTriple toRdfTriple(Statement statement) {
		return new RdfTriple() {
			@Override
			public RdfResource getSubject() {
				return toRdfResource(statement.getSubject());
			}

			@Override
			public RdfResource getPredicate() {
				return toRdfResource(statement.getPredicate());
			}

			@Override
			public RdfValue getObject() {
				return toRdfValue(statement.getObject());
			}
		};
	}

	private static RdfValue toRdfValue(Value node) {
		if (node.isResource()) {
			return toRdfResource((Resource) node);
		} else if (node.isLiteral()) {
			return new RdfLiteral() {
				@Override
				public String getValue() {
					return node.stringValue();
				}

				@Override
				public String getDatatype() {
					return ((Literal) node).getDatatype().stringValue();
				}

				@Override
				public Optional<String> getLanguage() {
					return ((Literal) node).getLanguage();
				}

				@Override
				public int hashCode() {
					return Objects.hash(getDatatype(), getLanguage().orElse(null), getValue());
				}

				@Override
				public boolean equals(Object obj) {
					if (this == obj) {
						return true;
					}
					if (obj == null) {
						return false;
					}
					if (obj instanceof RdfLiteral) {
						RdfLiteral other = (RdfLiteral) obj;
						return Objects.equals(getDatatype(), other.getDatatype())
								&& Objects.equals(getLanguage().orElse(null), other.getLanguage().orElse(""))
								&& Objects.equals(getValue(), other.getValue());
					}

					return false;
				}

				@Override
				public String toString() {
					StringBuilder builder = new StringBuilder();

					builder.append(getValue());

					if (getLanguage().isPresent()) {
						builder.append('@');
						builder.append(getLanguage().get());

					} else if (getDatatype() != null) {
						builder.append("^^");
						builder.append(getDatatype());
					}

					return builder.toString();
				}

			};

		}
		throw new IllegalArgumentException("Unknown type of node: " + node);

	}

	private static RdfResource toRdfResource(Resource node) {
		return new RdfResource() {
			@Override
			public String getValue() {
				if (node.isBNode()) {
					return "_:" + ((BNode) node).getID();
				}
				return node.stringValue();
			}

			@Override
			public boolean isIRI() {
				return node.isIRI();
			}

			@Override
			public boolean isBlankNode() {
				return node.isBNode();
			}

			@Override
			public int hashCode() {
				return Objects.hash(getValue());
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj) {
					return true;
				}
				if (obj == null) {
					return false;
				}
				if (obj instanceof RdfResource) {
					RdfResource other = (RdfResource) obj;
					return Objects.equals(getValue(), other.getValue());
				}

				return false;

			}

			@Override
			public String toString() {
				return Objects.toString(getValue());
			}
		};
	}

	private Value toRdf4jValue(RdfValue node) {
		if (node.isIRI()) {
			return vf.createIRI(node.getValue());
		} else if (node.isBlankNode()) {
			return vf.createBNode(node.getValue());
		} else if (node.isLiteral()) {
			RdfLiteral literal = node.asLiteral();
			if (literal.getLanguage().isPresent()) {
				return vf.createLiteral(node.getValue(), literal.getLanguage().get());
			}
			if (literal.getDatatype() != null) {
				return vf.createLiteral(node.getValue(), vf.createIRI(literal.getDatatype()));
			}

			return vf.createLiteral(node.getValue());
		}
		throw new IllegalArgumentException("Unknown type of node: " + node);

	}

	private IRI toRdf4jIri(RdfResource node) {
		if (node.isIRI()) {
			return vf.createIRI(node.getValue());
		}
		throw new IllegalArgumentException("Unknown type of node: " + node);
	}

	private Resource toRdf4jResource(RdfResource node) {
		if (node.isIRI()) {
			return vf.createIRI(node.getValue());
		} else if (node.isBlankNode()) {
			return vf.createBNode(node.getValue().substring(2));
		}
		throw new IllegalArgumentException("Unknown type of node: " + node);
	}

	@Override
	public void consumeStatement(Statement st) throws RDFHandlerException {
		statementCollector.handleStatement(st);
	}

	@Override
	public void handleComment(String comment) throws RDFHandlerException {
		checkWritingStarted();
	}

	@Override
	public RDFFormat getRDFFormat() {
		return RDFFormat.JSONLD;
	}

	@Override
	public Collection<RioSetting<?>> getSupportedSettings() {
		final Collection<RioSetting<?>> result = new HashSet<>(super.getSupportedSettings());
		result.add(BasicWriterSettings.PRETTY_PRINT);
		result.add(BasicWriterSettings.BASE_DIRECTIVE);
		result.add(JSONLDSettings.COMPACT_ARRAYS);
		result.add(JSONLDSettings.HIERARCHICAL_VIEW);
		result.add(JSONLDSettings.JSONLD_MODE);
		result.add(JSONLDSettings.PRODUCE_GENERALIZED_RDF);
		result.add(JSONLDSettings.USE_RDF_TYPE);
		result.add(JSONLDSettings.USE_NATIVE_TYPES);

		return result;
	}

	/**
	 * Add name space prefixes to JSON-LD context, empty prefix gets the '@vocab' prefix
	 *
	 * @param ctx        context
	 * @param namespaces set of RDF name spaces
	 */
	private static void addPrefixes(Map<String, Object> ctx, Set<Namespace> namespaces) {
		for (final Namespace ns : namespaces) {
			ctx.put(ns.getPrefix().isEmpty() ? JsonLdConsts.VOCAB : ns.getPrefix(), ns.getName());
		}
	}
}
