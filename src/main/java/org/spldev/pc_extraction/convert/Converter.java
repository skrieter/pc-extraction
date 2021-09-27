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
import org.spldev.formula.expression.io.parse.*;
import org.spldev.formula.expression.io.parse.NodeReader.*;
import org.spldev.formula.expression.io.parse.Symbols.*;
import org.spldev.formula.expression.term.*;
import org.spldev.formula.expression.transform.*;
import org.spldev.pc_extraction.util.*;
import org.spldev.util.data.*;
import org.spldev.util.logging.*;

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
						Formula formula = nodeReader.read(expr).get();
						if (formula == null) {
							return null;
						} else {
							formula = NormalForms.simplifyForNF(formula);
							Formulas.getVariableStream(formula) //
								.map(Variable::getName) //
								.forEach(pcNames::add);
							return new TempPC(expr, formula, sourceFilePath);
						}
					}).filter(Objects::nonNull) //
				;
			}).collect(Collectors.toList());

		final CNF modelFormula = fmFormula != null ? fmFormula : new CNF(VariableMap.fromNames(pcNames));

		final NodeWriter nodeWriter = new NodeWriter();
		nodeWriter.setSymbols(ShortSymbols.INSTANCE);

		final List<String> convertedDNFs = new ArrayList<>();

		final HashMap<String, PresenceCondition> pcMap = new HashMap<>();
		final List<PresenceCondition> convertedPCs = pcFormulas.stream() //
			.map(tempPC -> {
				PresenceCondition pc = pcMap.get(tempPC.formulaString);
				if (pc == null) {
					final VariableMap variableMap = modelFormula.getVariableMap();
					final CNF dnf;
					final CNF negatedDnf;
					if (tempPC.formula instanceof Literal) {
						convertedDNFs.add(nodeWriter.write(tempPC.formula));
						final LiteralList clause = getClause(tempPC.formula, variableMap);
						if (clause != null) {
							final ClauseList clauses = new ClauseList();
							clauses.add(clause);
							dnf = new CNF(variableMap, clauses);
							negatedDnf = new CNF(variableMap, clauses.negate());
						} else {
							dnf = null;
							negatedDnf = null;
						}
					} else {
						dnf = Formulas.toDNF(tempPC.formula).map(f -> {
							convertedDNFs.add(nodeWriter.write(f));
							final ClauseList clauses = new ClauseList();
							f.getChildren().stream() //
								.map(exp -> getClause(exp, variableMap)) //
								.filter(Objects::nonNull) //
								.forEach(clauses::add);
							return new CNF(variableMap, clauses);
						}).orElse((CNF) null);
						negatedDnf = Formulas.toCNF(tempPC.formula).map(f -> {
							final ClauseList cnfClauses = new ClauseList();
							f.getChildren().stream() //
								.map(exp -> getClause(exp, variableMap)) //
								.filter(Objects::nonNull) //
								.forEach(cnfClauses::add);
							return new CNF(variableMap, cnfClauses.negate());
						}).orElse((CNF) null);
					}
					if ((negatedDnf == null) || negatedDnf.getClauses().isEmpty() || (negatedDnf.getClauses().get(0)
						.size() == 0)
						|| (dnf == null) || dnf.getClauses().isEmpty() || (dnf.getClauses().get(0).size() == 0)) {
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

		final Path dnfPCsFile = extractionPath.resolve("filtered_pcs.list");
		try {
			Files.write(dnfPCsFile, convertedDNFs, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
		} catch (final IOException e) {
			Logger.logError(e);
		}
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
