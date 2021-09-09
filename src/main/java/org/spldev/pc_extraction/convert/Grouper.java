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

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.spldev.formula.analysis.sat4j.twise.*;
import org.spldev.formula.clauses.*;
import org.spldev.formula.expression.atomic.literal.*;

public class Grouper {

	public enum Grouping {
		PC_ALL_FM, PC_ALL_FM_FM, PC_FOLDER_FM, PC_FILE_FM, PC_VARS_FM, PC_ALL, PC_FOLDER, PC_FILE, PC_VARS, FM_ONLY
	}

	private final Object idObject = new Object();

	public Function<PresenceCondition, ?> allGrouper = pc -> idObject;
	public Function<PresenceCondition, ?> fileGrouper = PresenceCondition::getFilePath;
	public Function<PresenceCondition, ?> folderGrouper = pc -> pc.getFilePath().getParent();

	public Expressions group(PresenceConditionList pcList, Grouping grouping) throws Exception {
		switch (grouping) {
		case PC_ALL_FM:
		case PC_ALL:
			return group(pcList, allGrouper);
		case PC_FOLDER_FM:
		case PC_FOLDER:
			return group(pcList, folderGrouper);
		case PC_FILE_FM:
		case PC_FILE:
			return group(pcList, fileGrouper);
		case FM_ONLY:
		case PC_VARS:
			return groupVars(pcList);
		case PC_ALL_FM_FM:
			return groupVars2(pcList);
		case PC_VARS_FM:
			return groupPCFMVars(pcList);
		default:
			return null;
		}
	}

	public Expressions group(PresenceConditionList pcList, Function<PresenceCondition, ?> grouper) {
		final Map<?, List<PresenceCondition>> groupedPCs = pcList.stream().collect(Collectors.groupingBy(grouper));
		final Expressions expressions = new Expressions();
		groupedPCs.values().stream().map(this::createExpressions).forEach(expressions.getExpressions()::add);
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	public Expressions groupVars2(PresenceConditionList pcList) {
		final VariableMap newVariables = pcList.getFormula().getVariableMap();
		final LinkedHashSet<ClauseList> pcs = pcList.stream().flatMap(this::createExpression)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		pcs.addAll(TWiseCombiner.convertLiterals(LiteralList.getLiterals(newVariables)).get(0));

		final Expressions expressions = new Expressions();
		expressions.setExpressions(new ArrayList<>(pcs));
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	public Expressions groupVars(PresenceConditionList pcList) {
		final VariableMap newVariables = pcList.getFormula().getVariableMap();

		final Expressions expressions = new Expressions();
		expressions.setExpressions(LiteralList.getLiterals(newVariables));
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	public Expressions groupPCFMVars(PresenceConditionList pcList) {
		final VariableMap newVariables = pcList.getFormula().getVariableMap();

		final Expressions expressions = new Expressions();
		expressions.setExpressions(LiteralList.getLiterals(newVariables, pcList.getPCNames()));
		expressions.setCnf(pcList.getFormula());
		return expressions;
	}

	private List<ClauseList> createExpressions(List<PresenceCondition> pcList) {
		final List<ClauseList> exps = pcList.stream() //
			.flatMap(this::createExpression) //
			.peek(Collections::sort) //
			.distinct() //
			.collect(Collectors.toList());

		sort(exps);
		return exps;
	}

	private final Stream<ClauseList> createExpression(PresenceCondition pc) {
		final Stream.Builder<ClauseList> streamBuilder = Stream.builder();
		if ((pc != null) && (pc.getDnf() != null)) {
			streamBuilder.accept(pc.getDnf().getClauses());
			streamBuilder.accept(pc.getNegatedDnf().getClauses());
		}
		return streamBuilder.build().filter(list -> !list.isEmpty());
	}

	private void sort(List<ClauseList> exps) {
		Collections.sort(exps, (Comparator<ClauseList>) (o1, o2) -> {
			final int clauseCountDiff = o1.size() - o2.size();
			if (clauseCountDiff != 0) {
				return clauseCountDiff;
			}
			int clauseLengthDiff = 0;
			for (int i = 0; i < o1.size(); i++) {
				clauseLengthDiff += o1.get(i).size() - o2.get(i).size();
			}
			return clauseLengthDiff;
		});
	}

}
