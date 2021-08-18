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
package org.spldev.pc_extraction;

import java.io.*;
import java.util.*;

import org.spldev.formula.analysis.sat4j.twise.*;
import org.spldev.formula.clauses.*;

public final class Expressions implements Serializable {

	private static final long serialVersionUID = 2430619166140896491L;

	private CNF cnf;
	private final List<List<ClauseList>> expressions = new ArrayList<>(1);

	public CNF getCnf() {
		return cnf;
	}

	public void setCnf(CNF cnf) {
		this.cnf = cnf;
	}

	public void setExpressions(LiteralList literals) {
		expressions.clear();
		expressions.addAll(TWiseCombiner.convertLiterals(literals));
	}

	public void setExpressions(List<ClauseList> expressions) {
		this.expressions.clear();
		this.expressions.add(expressions);
	}

	public void setGroupedExpressions(List<List<ClauseList>> expressions) {
		this.expressions.clear();
		this.expressions.addAll(expressions);
	}

	public List<List<ClauseList>> getExpressions() {
		return expressions;
	}

}
