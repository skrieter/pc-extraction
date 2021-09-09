/* -----------------------------------------------------------------------------
 * PC-Extractor - Program for extracting presence conditions from SPLs.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of PC-Extractor.
 * 
 * PC-Extractor is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * PC-Extractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with PC-Extractor.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/pc-extractor> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.pc_extraction.convert;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.spldev.formula.clauses.*;
import org.spldev.formula.clauses.LiteralList.*;
import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.compound.*;
import org.spldev.formula.expression.io.parse.*;
import org.spldev.formula.expression.io.parse.NodeReader.*;
import org.spldev.formula.expression.io.parse.Symbols.*;
import org.spldev.formula.expression.term.*;
import org.spldev.pc_extraction.util.*;
import org.spldev.util.data.*;
import org.spldev.util.tree.*;

public class Converter {

	private static class TempPC {
		private final String formulaString;
		private final Formula formula;
		private final Path sourceFilePath;

		public TempPC(String formulaString, Formula formula, Path sourceFilePath) {
			this.formulaString = formulaString;
			this.formula = formula;
			this.sourceFilePath = sourceFilePath;
		}
	}

	private NodeReader nodeReader;

	public Converter() {
		nodeReader = new NodeReader();
		final Symbols symbols = new Symbols(Arrays.asList( //
			new Pair<>(Operator.NOT, "!"), //
			new Pair<>(Operator.AND, "&&"), //
			new Pair<>(Operator.OR, "||")), //
			false);
		nodeReader.setSymbols(symbols);
		nodeReader.setIgnoreMissingFeatures(ErrorHandling.REMOVE);
		nodeReader.setIgnoreUnparsableSubExpressions(ErrorHandling.REMOVE);
	}

	public PresenceConditionList convert(CNF fmFormula, Path extractionPath) {
		if (!Files.isReadable(extractionPath)) {
			return null;
		}
		final FileProvider fileProvider = new FileProvider(extractionPath);
		fileProvider.setFileNameRegex(FileProvider.PCFileRegex);

		if (fmFormula != null) {
			nodeReader.setVariableNames(fmFormula.getVariableMap().getNames());
		} else {
			nodeReader.setVariableNames(null);
		}

		final Collection<String> pcNames = new LinkedHashSet<>();
		final List<TempPC> pcFormulas = fileProvider.getFileStream() //
			.flatMap(p -> {
				List<String> lines;
				try {
					lines = Files.readAllLines(p);
				} catch (final IOException e) {
					return Stream.empty();
				}
				final Path sourceFilePath = Paths.get(lines.get(0));

				return lines.subList(1, lines.size()).stream() //
					.filter(expr -> !expr.isEmpty()).distinct() //
					.map(expr -> {
						final Formula formula = nodeReader.read(expr).get();
						if (formula == null) {
							return null;
						} else {
							Formulas.getVariableStream(formula) //
								.map(Variable::getName) //
								.forEach(pcNames::add);
							return new TempPC(expr, formula, sourceFilePath);
						}
					}).filter(Objects::nonNull) //
				;
			}).collect(Collectors.toList());

		final CNF modelFormula = fmFormula != null ? fmFormula : new CNF(VariableMap.fromNames(pcNames));

		final HashMap<String, PresenceCondition> pcMap = new HashMap<>();
		final List<PresenceCondition> convertedPCs = pcFormulas.stream() //
			.map(tempPC -> {
				PresenceCondition pc = pcMap.get(tempPC.formulaString);
				if (pc == null) {
					final VariableMap variableMap = modelFormula.getVariableMap();
					final ClauseList clauses = new ClauseList();
					final CNF dnf;
					final CNF negatedDnf;
					if (Formulas.isCNF(tempPC.formula)) {
						final Formula cnfFormula = Trees.cloneTree(tempPC.formula);
						cnfFormula.mapChildren(c -> (c instanceof Literal) ? new Or((Literal) c) : null);
						cnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
							.filter(Objects::nonNull).forEach(clauses::add);
						final CNF cnf = new CNF(variableMap, clauses);
						dnf = new CNF(variableMap, Clauses.convertNF(cnf.getClauses()));
						negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
					} else if (Formulas.isDNF(tempPC.formula)) {
						final Formula dnfFormula = Trees.cloneTree(tempPC.formula);
						dnfFormula.mapChildren(c -> (c instanceof Literal) ? new And((Literal) c) : null);
						dnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
							.filter(Objects::nonNull).forEach(clauses::add);
						dnf = new CNF(variableMap, clauses);
						final CNF cnf = new CNF(variableMap, Clauses.convertNF(dnf.getClauses()));
						negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
					} else {
						if (tempPC.formula instanceof Or) {
							final Formula dnfFormula = Formulas.toDNF(tempPC.formula).get();
							dnfFormula.mapChildren(c -> (c instanceof Literal) ? new And((Literal) c) : null);
							dnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
								.filter(Objects::nonNull).forEach(clauses::add);
							dnf = new CNF(variableMap, clauses);
							final CNF cnf = new CNF(variableMap, Clauses.convertNF(dnf.getClauses()));
							negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
						} else if (tempPC.formula instanceof And) {
							final Formula cnfFormula = Formulas.toCNF(tempPC.formula).get();
							cnfFormula.mapChildren(c -> (c instanceof Literal) ? new Or((Literal) c) : null);
							cnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
								.filter(Objects::nonNull).forEach(clauses::add);
							final CNF cnf = new CNF(variableMap, clauses);
							dnf = new CNF(variableMap, Clauses.convertNF(cnf.getClauses()));
							negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
						} else if (tempPC.formula instanceof Literal) {
							final LiteralList clause = getClause(tempPC.formula, variableMap);
							if (clause != null) {
								clauses.add(clause);
							}
							final CNF cnf = new CNF(variableMap, clauses);
							dnf = new CNF(variableMap, Clauses.convertNF(cnf.getClauses()));
							negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
						} else {
							pc = new PresenceCondition();
							pcMap.put(tempPC.formulaString, pc);
							return pc;
						}
					}
					if (negatedDnf.getClauses().isEmpty() || (negatedDnf.getClauses().get(0).size() == 0)
						|| dnf.getClauses().isEmpty() || (dnf.getClauses().get(0).size() == 0)) {
						pc = new PresenceCondition();
					} else {
						pc = new PresenceCondition(tempPC.sourceFilePath, dnf, negatedDnf);
					}
					pcMap.put(tempPC.formulaString, pc);
					return pc;
				} else {
					if (pc.getDnf() != null) {
						return new PresenceCondition(tempPC.sourceFilePath, pc.getDnf(), pc.getNegatedDnf());
					} else {
						return pc;
					}
				}
			}).filter(pc -> pc.getDnf() != null).collect(Collectors.toList());

		final PresenceConditionList presenceConditionList = new PresenceConditionList(convertedPCs, modelFormula);
		presenceConditionList.setPCNames(new ArrayList<>(pcNames));

		return presenceConditionList;
	}

	private LiteralList getClause(Expression clauseExpression, VariableMap mapping) {
		if (clauseExpression instanceof Literal) {
			final Literal literal = (Literal) clauseExpression;
			final int variable = mapping.getIndex(literal.getName())
				.orElseThrow(() -> new RuntimeException(literal.getName()));
			return new LiteralList(new int[] { literal.isPositive() ? variable : -variable }, Order.NATURAL, false);
		} else {
			final List<? extends Expression> clauseChildren = clauseExpression.getChildren();
			if (clauseChildren.stream().anyMatch(literal -> literal == Literal.True)) {
				return null;
			} else {
				final int[] literals = clauseChildren.stream().filter(literal -> literal != Literal.False)
					.mapToInt(literal -> {
						final int variable = mapping.getIndex(literal.getName())
							.orElseThrow(() -> new RuntimeException(literal.getName()));
						return ((Literal) literal).isPositive() ? variable : -variable;
					}).toArray();
				return new LiteralList(literals, Order.NATURAL).clean().get();
			}
		}
	}

}
