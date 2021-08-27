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
import java.util.*;

import org.spldev.formula.clauses.*;

public class PresenceConditionList extends ArrayList<PresenceCondition> implements Serializable {

	private static final long serialVersionUID = 4377594672333226651L;

	private final CNF formula;

	private ArrayList<String> pcNames;

	public PresenceConditionList(List<PresenceCondition> list, CNF formula) {
		super(list);
		this.formula = formula;
	}

	public CNF getFormula() {
		return formula;
	}

	public ArrayList<String> getPCNames() {
		return pcNames;
	}

	public void setPCNames(ArrayList<String> pcNames) {
		this.pcNames = pcNames;
	}

}
