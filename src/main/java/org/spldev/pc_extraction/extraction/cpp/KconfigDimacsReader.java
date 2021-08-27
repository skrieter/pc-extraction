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
package org.spldev.pc_extraction.extraction.cpp;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.spldev.formula.clauses.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.io.*;
import org.spldev.formula.expression.term.*;

public class KconfigDimacsReader {

	private static final Charset charset = Charset.forName("UTF-8");

	public CNF load(String name, Path kbuildOutputPath) throws Exception {
		final Path kbuildPath = kbuildOutputPath.resolve(name).toAbsolutePath();
		final Path featureFile = kbuildPath.resolve(name + ".features");
		final Path modelFile = kbuildPath.resolve("model.dimacs");

		final Set<String> featureNames = Files.lines(featureFile, charset).filter(line -> !line.isEmpty())
				.collect(Collectors.toSet());
		final String source = new String(Files.readAllBytes(modelFile), charset);

		final DimacsReader r = new DimacsReader();
		r.setReadingVariableDirectory(true);
		final CNF cnf = Clauses.convertToCNF(r.read(new StringReader(source)));

		final Set<String> dirtyVariables = cnf.getVariableMap() //
				.getNames().stream() //
				.filter(variable -> !featureNames.contains("CONFIG_" + variable)) //
				.distinct() //
				.collect(Collectors.toSet());
		final CNF slicedCNF = Clauses.slice(cnf, dirtyVariables);

		final VariableMap slicedVariables = slicedCNF.getVariableMap();
		final VariableMap newVariables = VariableMap.fromNames(featureNames);
		final ClauseList newClauseList = new ClauseList();

		for (final LiteralList clause : slicedCNF.getClauses()) {
			final int[] oldLiterals = clause.getLiterals();
			final int[] newLiterals = new int[oldLiterals.length];
			for (int i = 0; i < oldLiterals.length; i++) {
				final int literal = oldLiterals[i];
				final int var = newVariables.getVariable("CONFIG_" + slicedVariables.getName(literal))
						.map(Variable::getIndex).get();
				newLiterals[i] = literal > 0 ? var : -var;
			}
			newClauseList.add(new LiteralList(newLiterals));
		}

		return new CNF(newVariables, newClauseList);
	}

}
