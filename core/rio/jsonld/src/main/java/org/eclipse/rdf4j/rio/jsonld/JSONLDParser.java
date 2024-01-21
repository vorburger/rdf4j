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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import no.hasmac.jsonld.JsonLd;
import no.hasmac.jsonld.JsonLdError;
import no.hasmac.jsonld.JsonLdOptions;
import no.hasmac.jsonld.document.Document;
import no.hasmac.jsonld.document.JsonDocument;
import no.hasmac.jsonld.lang.Keywords;
import no.hasmac.jsonld.loader.DocumentLoader;
import no.hasmac.jsonld.loader.DocumentLoaderOptions;
import no.hasmac.jsonld.loader.SchemeRouter;
import no.hasmac.rdf.RdfConsumer;
import no.hasmac.rdf.RdfValueFactory;

/**
 * An {@link RDFParser} that links to {@link JSONLDInternalTripleCallback}.
 *
 * @author Peter Ansell
 */
public class JSONLDParser extends AbstractRDFParser {

	/**
	 * Default constructor
	 */
	public JSONLDParser() {
		super();
	}

	/**
	 * Creates a JSONLD Parser using the given {@link ValueFactory} to create new {@link Value}s.
	 *
	 * @param valueFactory The ValueFactory to use
	 */
	public JSONLDParser(final ValueFactory valueFactory) {
		super(valueFactory);
	}

	@Override
	public RDFFormat getRDFFormat() {
		return RDFFormat.JSONLD;
	}

	@Override
	public Collection<RioSetting<?>> getSupportedSettings() {
		Collection<RioSetting<?>> result = super.getSupportedSettings();

		result.add(JSONLDSettings.EXPAND_CONTEXT);

		return result;
	}

	@Override
	public void parse(final InputStream in, final String baseURI)
			throws IOException, RDFParseException, RDFHandlerException {
		parse(in, null, baseURI);
	}

	@Override
	public void parse(final Reader reader, final String baseURI)
			throws IOException, RDFParseException, RDFHandlerException {
		parse(null, reader, baseURI);
	}

	/**
	 * Parse
	 *
	 * @param in
	 * @param reader
	 * @param baseURI
	 * @throws IOException
	 * @throws RDFParseException
	 * @throws RDFHandlerException
	 */
	private void parse(InputStream in, Reader reader, String baseURI)
			throws IOException, RDFParseException, RDFHandlerException {
		clear();

		try {

			Document document;
			if (in == null && reader != null) {
				document = JsonDocument.of(reader);
			} else if (in != null && reader == null) {
				document = JsonDocument.of(in);
			} else {
				throw new IllegalArgumentException("Either in or reader must be set");
			}

			JsonLdOptions opts = new JsonLdOptions();
			opts.setUriValidation(false);

			JsonDocument context = getParserConfig().get(JSONLDSettings.EXPAND_CONTEXT);

			if (context != null) {

				opts.setExpandContext(context);

				if (context.getDocumentUrl() != null) {
					Optional<JsonStructure> jsonContent = context.getJsonContent();
					if (jsonContent.isEmpty()) {
						throw new RDFParseException("Expand context is not a valid JSON document");
					}
					opts.getContextCache().put(context.getDocumentUrl().toString(), jsonContent.get());
					opts.setDocumentLoader(new DocumentLoader() {

						private final DocumentLoader defaultDocumentLoader = SchemeRouter.defaultInstance();

						@Override
						public Document loadDocument(URI url, DocumentLoaderOptions options) throws JsonLdError {
							if (url.equals(context.getDocumentUrl())) {
								return context;
							}
							return defaultDocumentLoader.loadDocument(url, options);
						}
					});
				}

			}

			if (baseURI != null) {
				URI uri = new URI(baseURI);
				opts.setBase(uri);
			}

			RDFHandler rdfHandler = getRDFHandler();

			JsonLd.toRdf(document).options(opts).base(baseURI).get(new RdfConsumer<>() {
				@Override
				public void handleTriple(Statement statement) {
					rdfHandler.handleStatement(statement);
				}

				@Override
				public void handleQuad(Statement statement) {
					rdfHandler.handleStatement(statement);
				}

				@Override
				public void handleNamespace(String prefix, String uri) {

				}
			}, new RdfValueFactory<Statement, Statement, IRI, BNode, Resource, Literal, Value>() {
				@Override
				public Statement createTriple(Resource subject, IRI predicate, Value object) {
					return valueFactory.createStatement(subject, predicate, object);
				}

				@Override
				public Statement createQuad(Resource subject, IRI predicate, Value object, Resource graphName) {
					return valueFactory.createStatement(subject, predicate, object, graphName);
				}

				@Override
				public Statement createQuad(Statement statement, Resource graphName) {
					return valueFactory.createStatement(statement.getSubject(), statement.getPredicate(),
							statement.getObject(), graphName);
				}

				@Override
				public IRI createIRI(String value) {
					return valueFactory.createIRI(value);
				}

				@Override
				public BNode createBlankNode(String value) {
					return (BNode) createNode(value);
				}

				@Override
				public Literal createTypedLiteral(String value, String datatype) {
					return valueFactory.createLiteral(value, valueFactory.createIRI(datatype));
				}

				@Override
				public Literal createString(String value) {
					return valueFactory.createLiteral(value);
				}

				@Override
				public Literal createLangString(String value, String lang) {
					return valueFactory.createLiteral(value, lang);
				}
			});

			extractPrefixes(document, rdfHandler::handleNamespace);

		} catch (no.hasmac.jsonld.JsonLdError e) {
			throw new RDFParseException("Could not parse JSONLD", e);
		} catch (RuntimeException e) {
			if (e.getCause() != null && e.getCause() instanceof RDFParseException) {
				throw (RDFParseException) e.getCause();
			}
			throw e;
		} catch (URISyntaxException e) {
			throw new RDFParseException("Base uri is not a valid URI, " + baseURI, e);
		} finally {
			clear();
		}
	}

	private static void extractPrefixes(Document document, BiConsumer<String, String> action) {
		try {
			JsonStructure js = document.getJsonContent().get();
			switch (js.getValueType()) {
			case ARRAY:
				extractPrefixes(js, action);
				break;
			case OBJECT:
				JsonValue jv = js.asJsonObject().get(Keywords.CONTEXT);
				extractPrefixes(jv, action);
				break;
			default:
				break;
			}
		} catch (Throwable ex) {
			// TODO
			throw new RuntimeException(ex);
		}
	}

	private static void extractPrefixes(JsonValue jsonValue, BiConsumer<String, String> action) {
		if (jsonValue == null) {
			return;
		}
		// JSON-LD 1.1 section 9.4
		switch (jsonValue.getValueType()) {
		case ARRAY:
			jsonValue.asJsonArray().forEach(jv -> extractPrefixes(jv, action));
			break;
		case OBJECT:
			extractPrefixesCxtDefn(jsonValue.asJsonObject(), action);
			break;
		case NULL:
			break; // We are only interested in prefixes
		case STRING:
			break; // We are only interested in prefixes
		default:
			break;
		}
	}

	private static void extractPrefixesCxtDefn(JsonObject jCxt, BiConsumer<String, String> action) {
		Set<String> keys = jCxt.keySet();
		keys.forEach(k -> {
			// "@vocab" : "uri"
			// "shortName" : "uri"
			// "shortName" : { "@type":"@id" , "@id": "uri" } -- presumed to be a single property aliases, not a prefix.
			JsonValue jvx = jCxt.get(k);
			if (JsonValue.ValueType.STRING != jvx.getValueType()) {
				return;
			}
			String prefix = k;
			if (Keywords.VOCAB.equals(k)) {
				prefix = "";
			} else if (k.startsWith("@"))
			// Keyword, not @vocab.
			{
				return;
			}
			// Pragmatic filter: URI ends in "#" or "/" or ":"
			String uri = JsonString.class.cast(jvx).getString();
			if (uri.endsWith("#") || uri.endsWith("/") || uri.endsWith(":")) {
				action.accept(prefix, uri);
				return;
			}
		});
	}

//
//
//	/**
//	 * Get an instance of JsonFactory configured using the settings from {@link #getParserConfig()}.
//	 *
//	 * @return A newly configured JsonFactory based on the currently enabled settings
//	 */
//	private JsonFactory configureNewJsonFactory() {
//		JsonFactoryBuilder builder = new JsonFactoryBuilder();
//		ParserConfig parserConfig = getParserConfig();
//
//		if (parserConfig.isSet(JSONSettings.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)) {
//			builder.configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
//					parserConfig.get(JSONSettings.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_COMMENTS)) {
//			builder.configure(JsonReadFeature.ALLOW_JAVA_COMMENTS,
//					parserConfig.get(JSONSettings.ALLOW_COMMENTS));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_NON_NUMERIC_NUMBERS)) {
//			builder.configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS,
//					parserConfig.get(JSONSettings.ALLOW_NON_NUMERIC_NUMBERS));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_NUMERIC_LEADING_ZEROS)) {
//			builder.configure(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS,
//					parserConfig.get(JSONSettings.ALLOW_NUMERIC_LEADING_ZEROS));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_SINGLE_QUOTES)) {
//			builder.configure(JsonReadFeature.ALLOW_SINGLE_QUOTES,
//					parserConfig.get(JSONSettings.ALLOW_SINGLE_QUOTES));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_UNQUOTED_CONTROL_CHARS)) {
//			builder.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
//					parserConfig.get(JSONSettings.ALLOW_UNQUOTED_CONTROL_CHARS));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_UNQUOTED_FIELD_NAMES)) {
//			builder.configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES,
//					parserConfig.get(JSONSettings.ALLOW_UNQUOTED_FIELD_NAMES));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_YAML_COMMENTS)) {
//			builder.configure(JsonReadFeature.ALLOW_YAML_COMMENTS,
//					parserConfig.get(JSONSettings.ALLOW_YAML_COMMENTS));
//		}
//		if (parserConfig.isSet(JSONSettings.ALLOW_TRAILING_COMMA)) {
//			builder.configure(JsonReadFeature.ALLOW_TRAILING_COMMA,
//					parserConfig.get(JSONSettings.ALLOW_TRAILING_COMMA));
//		}
//		if (parserConfig.isSet(JSONSettings.INCLUDE_SOURCE_IN_LOCATION)) {
//			builder.configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION,
//					parserConfig.get(JSONSettings.INCLUDE_SOURCE_IN_LOCATION));
//		}
//		if (parserConfig.isSet(JSONSettings.STRICT_DUPLICATE_DETECTION)) {
//			builder.configure(StreamReadFeature.STRICT_DUPLICATE_DETECTION,
//					parserConfig.get(JSONSettings.STRICT_DUPLICATE_DETECTION));
//		}
//		return builder.build().setCodec(JSON_MAPPER);
//	}
}
