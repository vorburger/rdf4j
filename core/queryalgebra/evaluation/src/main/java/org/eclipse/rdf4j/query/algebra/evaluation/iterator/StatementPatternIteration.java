package org.eclipse.rdf4j.query.algebra.evaluation.iterator;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.ConvertingIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.common.iteration.FilterIteration;
import org.eclipse.rdf4j.common.iteration.Iteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.model.vocabulary.SESAME;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.StatementPattern.Scope;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategy;

public class StatementPatternIteration {

	private final StatementPattern statementPattern;
	private final BindingSet bindings;
	private final TripleSource tripleSource;
	private final boolean emptyGraph;

	private final Function<Value, Resource[]> contextSup;

	public StatementPatternIteration(StatementPattern statementPattern, BindingSet bindings, Dataset dataset,
			TripleSource tripleSource) {
		super();
		this.statementPattern = statementPattern;
		this.bindings = bindings;
		this.tripleSource = tripleSource;
		Set<IRI> graphs = null;
		if (dataset != null) {
			if (statementPattern.getScope() == Scope.DEFAULT_CONTEXTS) {
				graphs = dataset.getDefaultGraphs();
				if (graphs.isEmpty() && !dataset.getNamedGraphs().isEmpty()) {
					emptyGraph = true;
				} else {
					emptyGraph = false;
				}
			} else {
				graphs = dataset.getNamedGraphs();
				if (graphs.isEmpty() && !dataset.getDefaultGraphs().isEmpty()) {
					emptyGraph = true;
				} else {
					emptyGraph = false;
				}
			}
		} else {
			emptyGraph = false;
			graphs = null;
		}
		contextSup = extractContextsFromDatasets(statementPattern.getContextVar(), emptyGraph, graphs);
	}

	public CloseableIteration<BindingSet, QueryEvaluationException> evaluate() {
		if (emptyGraph) {
			return new EmptyIteration<>();
		}

		final Var subjVar = statementPattern.getSubjectVar();
		final Var predVar = statementPattern.getPredicateVar();
		final Var objVar = statementPattern.getObjectVar();
		final Var conVar = statementPattern.getContextVar();
		if (isUnbound(subjVar, bindings) || isUnbound(predVar, bindings) || isUnbound(objVar, bindings)
				|| isUnbound(conVar, bindings)) {
			// the variable must remain unbound for this solution see
			// https://www.w3.org/TR/sparql11-query/#assignment
			return new EmptyIteration<>();
		}

		final Value contextValue = StrictEvaluationStrategy.getVarValue(conVar, bindings);

		Resource[] contexts = contextSup.apply(contextValue);
		if (contexts == null) {
			return new EmptyIteration<>();
		}

		final Value subjValue = StrictEvaluationStrategy.getVarValue(subjVar, bindings);
		final Value predValue = StrictEvaluationStrategy.getVarValue(predVar, bindings);
		final Value objValue = StrictEvaluationStrategy.getVarValue(objVar, bindings);
		// Check that the subject is a Resource and the predicate can be an IRI
		// if not we can't return any value.
		Resource subjResouce;
		IRI predIri;
		try {
			subjResouce = (Resource) subjValue;
			predIri = (IRI) predValue;
		} catch (ClassCastException e) {
			// Invalid value type for subject, predicate and/or context
			return new EmptyIteration<>();
		}

		CloseableIteration<? extends Statement, QueryEvaluationException> stIter1 = tripleSource
				.getStatements(subjResouce, predIri, objValue, contexts);
		Predicate<Statement> filter = filterContextOrEqualVariables(statementPattern, subjVar, predVar, objVar,
				conVar,
				subjValue,
				predValue, objValue, contexts);
		if (filter != null) {
			stIter1 = new FilterIteration<Statement, QueryEvaluationException>(stIter1) {

				@Override
				protected boolean accept(Statement object) throws QueryEvaluationException {
					return filter.test(object);
				}
			};
		}

		// Return an iterator that converts the statements to var bindings
		return new ConvertStatmentToBindingSetIterator(stIter1, subjVar, predVar, objVar, conVar,
				bindings);
	}

	/**
	 * Generate a predicate that tests for Named contexts are matched by retrieving all statements from the store and
	 * filtering out the statements that do not have a context. Or the same variable might have been used multiple times
	 * in this StatementPattern, verify value equality in those cases.
	 */
	protected static Predicate<Statement> filterContextOrEqualVariables(StatementPattern statementPattern,
			final Var subjVar,
			final Var predVar,
			final Var objVar, final Var conVar, final Value subjValue, final Value predValue, final Value objValue,
			Resource[] contexts) {
		Predicate<Statement> filter = null;
		if (contexts.length == 0 && statementPattern.getScope() == Scope.NAMED_CONTEXTS) {
			filter = (st) -> st.getContext() != null;
		}
		return filterSameVariable(subjVar, predVar, objVar, conVar, subjValue, predValue, objValue, filter);
	}

	private static Predicate<Statement> filterSameVariable(final Var subjVar, final Var predVar, final Var objVar,
			final Var conVar, final Value subjValue, final Value predValue, final Value objValue,
			Predicate<Statement> filter) {

		if (subjVar != null && subjValue == null) {
			boolean subEqPredVar = subjVar.equals(predVar);
			boolean subEqObjVar = subjVar.equals(objVar);
			boolean subEqConVar = subjVar.equals(conVar);
			if (subEqPredVar || subEqObjVar || subEqConVar) {
				filter = andThen(filter, subjectVariableHasEquals(subEqPredVar, subEqObjVar, subEqConVar));
			}
		}
		if (predVar != null && predValue == null) {

			boolean predEqObjVar = predVar.equals(objVar);
			boolean predEqConVar = predVar.equals(conVar);
			if (predEqObjVar || predEqConVar) {
				filter = andThen(filter, predicateVariableHasEquals(predEqObjVar, predEqConVar));
			}
		}
		if (objVar != null && objValue == null) {
			boolean objEqConVar = objVar.equals(conVar);
			if (objEqConVar) {
				filter = andThen(filter, (st) -> {
					Value obj = st.getObject();
					Resource context = st.getContext();
					return obj.equals(context);
				});
			}
		}
		return filter;
	}

	private static Predicate<Statement> predicateVariableHasEquals(boolean predEqObjVar, boolean predEqConVar) {
		Predicate<Statement> eq = null;
		if (predEqObjVar) {
			eq = (st) -> st.getPredicate().equals(st.getObject());
		}
		if (predEqConVar) {
			eq = andThen(eq, (st) -> st.getPredicate().equals(st.getContext()));
		}
		return eq;
	}

	private static Predicate<Statement> subjectVariableHasEquals(boolean subEqPredVar, boolean subEqObjVar,
			boolean subEqConVar) {
		Predicate<Statement> eq = null;
		if (subEqPredVar) {
			eq = (st) -> st.getSubject().equals(st.getPredicate());
		}
		if (subEqObjVar) {
			eq = andThen(eq, (st) -> st.getSubject().equals(st.getObject()));
		}
		if (subEqConVar) {
			eq = andThen(eq, (st) -> st.getSubject().equals(st.getContext()));
		}
		return eq;
	}

	/**
	 * @param statementPattern
	 * @param contextValue
	 * @return the contexts that are valid for this statement pattern or null
	 */
	protected static Function<Value, Resource[]> extractContextsFromDatasets(final Var contextVar, boolean emptyGraph,
			Set<IRI> graphs) {

		if (emptyGraph) {
			return (cv) -> null;
		}

		if (graphs == null || graphs.isEmpty()) {
			// store default behaviour
			return (contextValue) -> contextsGivenContextVal(contextValue);
		} else {
			Resource[] filled = fillContextsFromDatasSetGraphs(graphs);
			//if contextVar is null contextValue must always be null;
			if (contextVar == null) {
				return (contextValue) -> filled;
			} else {
				return (contextValue) -> {
					if (contextValue != null) {
						if (graphs.contains(contextValue)) {
							return new Resource[] { (Resource) contextValue };
						} else {
							// Statement pattern specifies a context that is not part of
							// the dataset
							return null;
						}
					} else {
						return filled;
					}
				};
			}
		}
	}

	private static Resource[] contextsGivenContextVal(final Value contextValue) {
		if (contextValue != null) {
			if (RDF4J.NIL.equals(contextValue) || SESAME.NIL.equals(contextValue)) {
				return new Resource[] { (Resource) null };
			} else {
				return new Resource[] { (Resource) contextValue };
			}
		}
		/*
		 * TODO activate this to have an exclusive (rather than inclusive) interpretation of the default graph in SPARQL
		 * querying. else if (statementPattern.getScope() == Scope.DEFAULT_CONTEXTS ) { contexts = new Resource[] {
		 * (Resource)null }; }
		 */
		else {
			return new Resource[0];
		}
	}

	private static Resource[] fillContextsFromDatasSetGraphs(Set<IRI> graphs) {
		Resource[] contexts = new Resource[graphs.size()];
		int i = 0;
		for (IRI graph : graphs) {
			IRI context = null;
			if (!(RDF4J.NIL.equals(graph) || SESAME.NIL.equals(graph))) {
				context = graph;
			}
			contexts[i++] = context;
		}
		return contexts;
	}

	private static boolean isUnbound(Var var, BindingSet bindings) {
		if (var == null) {
			return false;
		} else {
			return bindings.hasBinding(var.getName()) && bindings.getValue(var.getName()) == null;
		}
	}

	/**
	 * Converts statements into the required bindingsets. A lot of work is done in the constructor and then uses
	 * invokedynamic code with lambdas for the actual conversion.
	 * 
	 * This allows avoiding of significant work during the iteration. Which pays of if the iteration is long, otherwise
	 * it of course is an unneeded expense.
	 */
	private static final class ConvertStatmentToBindingSetIterator
			extends ConvertingIteration<Statement, BindingSet, QueryEvaluationException> {
		private final Function<Statement, QueryBindingSet> action;

		private ConvertStatmentToBindingSetIterator(
				Iteration<? extends Statement, ? extends QueryEvaluationException> iter, Var subjVar, Var predVar,
				Var objVar, Var conVar, BindingSet bindings) {
			super(iter);
			Supplier<QueryBindingSet> bindingMaker = makeBindingSetSupplier(bindings);
			BiConsumer<QueryBindingSet, Statement> co = makeConverter(subjVar, predVar, objVar, conVar, bindings);
			if (co == null) {
				action = (st) -> bindingMaker.get();
			} else {
				action = (st) -> {
					QueryBindingSet result = bindingMaker.get();
					co.accept(result, st);
					return result;
				};
			}
		}

		private Supplier<QueryBindingSet> makeBindingSetSupplier(BindingSet bindings) {
			Supplier<QueryBindingSet> bindingMaker;
			if (bindings.size() == 0) {
				bindingMaker = QueryBindingSet::new;
			} else {
				bindingMaker = () -> new QueryBindingSet(bindings);
			}
			return bindingMaker;
		}

		/**
		 * We are going to chain biconsumer functions allowing us to avoid a lot of hasBindings etc. code
		 * 
		 * @return a converter from statement into QueryBindingSet or a null
		 */
		private BiConsumer<QueryBindingSet, Statement> makeConverter(Var subjVar, Var predVar, Var objVar, Var conVar,
				BindingSet bindings) {
			BiConsumer<QueryBindingSet, Statement> co = null;

			if (subjVar != null && !subjVar.isConstant() && !bindings.hasBinding(subjVar.getName())) {
				String subVarName = subjVar.getName();
				co = andThen(co, (result, st) -> addValueToBinding(result, subVarName, st.getSubject()));
			}
			// We should not overwrite previous set values so if pred == subj we don't need to call this again.
			// etc.
			if (predVar != null && !predVar.isConstant()
					&& !predVar.equals(subjVar)) {
				String predVarName = predVar.getName();
				co = andThen(co, (result, st) -> addValueToBinding(result, predVarName, st.getPredicate()));
			}
			if (objVar != null && !objVar.isConstant() && !objVar.equals(subjVar) && !objVar.equals(predVar)) {
				String objVarName = objVar.getName();
				co = andThen(co, (result, st) -> addValueToBinding(result, objVarName, st.getObject()));
			}
			if (conVar != null && !conVar.isConstant() && !conVar.equals(subjVar) && !conVar.equals(predVar)
					&& !conVar.equals(objVar)) {
				String conVarName = conVar.getName();
				co = andThen(co, (result, st) -> {
					if (st.getContext() != null) {
						addValueToBinding(result, conVarName, st.getContext());
					}
				});
			}
			return co;
		}

		private void addValueToBinding(QueryBindingSet result, String predVarName, Value value) {
			if (!result.hasBinding(predVarName)) {
				result.addBinding(predVarName, value);
			}
		}

		@Override
		protected BindingSet convert(Statement st) {
			return action.apply(st);
		}
	}

	private static Predicate<Statement> andThen(Predicate<Statement> pred, Predicate<Statement> and) {
		if (pred == null) {
			return and;
		}
		return pred.and(and);
	}

	private static BiConsumer<QueryBindingSet, Statement> andThen(BiConsumer<QueryBindingSet, Statement> co,
			BiConsumer<QueryBindingSet, Statement> and) {
		if (co == null)
			return and;
		return co.andThen(and);
	}
}
