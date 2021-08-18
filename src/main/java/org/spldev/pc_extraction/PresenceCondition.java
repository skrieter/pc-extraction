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
import java.net.*;
import java.nio.file.*;
import java.util.*;

import org.spldev.formula.clauses.*;
import org.spldev.formula.clauses.LiteralList.*;

public class PresenceCondition implements Serializable {

	private static final long serialVersionUID = -6830211519154462391L;

	private final CNF dnf, negatedDnf;
	private final URI filePath;

	public PresenceCondition() {
		filePath = null;
		dnf = null;
		negatedDnf = null;
	}

	public PresenceCondition(Path filePath, CNF dnf, CNF negatedDnf) {
		this.filePath = filePath.toUri();
		this.dnf = dnf;
		this.negatedDnf = negatedDnf;
		dnf.getClauses().stream().forEach(c -> c.setOrder(Order.NATURAL));
		Collections.sort(dnf.getClauses(), Comparator.comparing(LiteralList::toLiteralString));
	}

	public Path getFilePath() {
		return Paths.get(filePath);
	}

	public CNF getDnf() {
		return dnf;
	}

	public CNF getNegatedDnf() {
		return negatedDnf;
	}

	@Override
	public int hashCode() {
		return Objects.hash(filePath, dnf);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		final PresenceCondition other = (PresenceCondition) obj;
		return Objects.equals(filePath, other.filePath) && Objects.equals(dnf, other.dnf);
	}

}
