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
import java.nio.file.*;
import java.util.*;

import org.spldev.formula.clauses.*;
import org.spldev.pc_extraction.convert.*;
import org.spldev.pc_extraction.convert.Grouper.*;
import org.spldev.pc_extraction.extraction.cpp.*;
import org.spldev.util.data.Result;
import org.spldev.util.io.*;
import org.spldev.util.io.binary.*;
import org.spldev.util.logging.*;

public class PCExtractor {

	private boolean saveResults = true;
	private Grouping groupingValue = Grouping.PC_ALL_FM;

	public void setSaveIntermediateResults(boolean saveIntermediateResults) {
		saveResults = saveIntermediateResults;
	}

	public void setGroupingValue(Grouping groupingValue) {
		this.groupingValue = groupingValue;
	}

	public void deleteExpressionFiles(Path outputPath, String systemName) throws Exception {
		final Path pcListDir = outputPath.resolve("pclist").resolve(systemName);
		final Path extractDir = outputPath.resolve("extract").resolve(systemName);
		if (Files.exists(pcListDir)) {
			Files.walk(pcListDir)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}
		if (Files.exists(extractDir)) {
			Files.walk(extractDir)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}
	}

	public Expressions extract(Path outputPath, Path systemPath, CNF fmFormula) throws Exception {
		final Path pcListDir = outputPath.resolve("pclist").resolve(systemPath.getFileName());
		final Path extractDir = outputPath.resolve("extract").resolve(systemPath.getFileName());
		Files.createDirectories(pcListDir);
		Files.createDirectories(extractDir);

		extract(systemPath, extractDir.getParent());
		final PresenceConditionList pcList = convert(fmFormula, extractDir, pcListDir);
		final Expressions expressions = group(pcList, pcListDir);
		return expressions;
	}

	public Result<Expressions> loadExpressions(Path outputPath, String systemName) {
		final SerializableObjectFormat<Expressions> format = new SerializableObjectFormat<>();
		return FileHandler.load(outputPath.resolve("pclist").resolve(systemName)
			.resolve("grouped_" + groupingValue + "." + format.getFileExtension()), format);
	}

	private void extract(Path systemPath, Path extractDir) {
		new CPPExtractor().extract(systemPath, extractDir);
	}

	private PresenceConditionList convert(CNF fmFormula, Path extractDir, Path pcListDir) throws IOException {
		final SerializableObjectFormat<PresenceConditionList> format = new SerializableObjectFormat<>();
		final Path pcListFile = pcListDir.resolve("pclist_fm." + format.getFileExtension());
		if (Files.exists(pcListFile)) {
			final Result<PresenceConditionList> loadedPCList = FileHandler.load(pcListFile, format);
			if (loadedPCList.isPresent()) {
				return loadedPCList.get();
			} else {
				Logger.logProblems(loadedPCList.getProblems());
			}
		}
		final PresenceConditionList pcList = new Converter().convert(fmFormula, extractDir);
		if (pcList != null) {
			if (saveResults) {
				FileHandler.save(pcList, pcListFile, format);
			}
			return pcList;
		} else {
			return null;
		}
	}

	private Expressions group(PresenceConditionList pcList, Path pcListDir) throws Exception, IOException {
		final SerializableObjectFormat<Expressions> format = new SerializableObjectFormat<>();
		final Path expFile = pcListDir.resolve("grouped_" + groupingValue + "." + format.getFileExtension());
		if (Files.exists(expFile)) {
			final Result<Expressions> loadedExpressions = FileHandler.load(expFile, format);
			if (loadedExpressions.isPresent()) {
				return loadedExpressions.get();
			} else {
				Logger.logProblems(loadedExpressions.getProblems());
			}
		}
		final Expressions expressions = new Grouper().group(pcList, groupingValue);
		if (expressions == null) {
			return null;
		}
		if (saveResults) {
			FileHandler.save(expressions, expFile, format);
		}
		return expressions;
	}
}
