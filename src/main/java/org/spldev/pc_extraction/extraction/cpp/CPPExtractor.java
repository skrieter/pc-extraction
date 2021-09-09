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

import org.spldev.pc_extraction.util.*;
import org.spldev.util.logging.*;

import de.ovgu.spldev.featurecopp.config.*;
import de.ovgu.spldev.featurecopp.config.Configuration.*;
import de.ovgu.spldev.featurecopp.lang.cpp.*;
import de.ovgu.spldev.featurecopp.splmodel.*;

public class CPPExtractor {

	private static class NullStream extends PrintStream {
		public NullStream() {
			super(new OutputStream() {
				@Override
				public void write(int b) {
				}

				@Override
				public void write(byte[] arg0, int arg1, int arg2) throws IOException {
				}

				@Override
				public void write(byte[] arg0) throws IOException {
				}
			});
		}
	}

	private static class LevelComparator implements Comparator<FeatureModule.FeatureOccurrence> {
		@Override
		public int compare(FeatureModule.FeatureOccurrence occ1, FeatureModule.FeatureOccurrence occ2) {
			return Integer.compare(getLevel(occ1), getLevel(occ2));
		}

		private int getLevel(FeatureModule.FeatureOccurrence featureOccurrence) {
			final FeatureModule.FeatureOccurrence enclosingFeatureOccurence = featureOccurrence.enclosing;
			return enclosingFeatureOccurence != null ? getLevel(enclosingFeatureOccurence) + 1 : 0;
		}
	}

	private static final List<Charset> charsets = Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1);

	private Path systemPath;
	private Path outputPath;
	private List<Path> excludePaths = new ArrayList<>();

	private long fileCounter;

	public CPPExtractor() {
		excludePaths.add(Paths.get("scripts"));
		excludePaths.add(Paths.get("examples"));
		excludePaths.add(Paths.get("include/config"));
		excludePaths.add(Paths.get("config/scripts"));
	}

	public void setExcludePaths(List<Path> excludePaths) {
		this.excludePaths.clear();
		this.excludePaths.addAll(excludePaths);
	}

	public List<Path> getExcludePaths() {
		return excludePaths;
	}

	private List<String> extractPresenceConditions(CPPAnalyzer cppAnalyzer, List<String> lines) {
		final StringBuilder sb = new StringBuilder();
		for (final String line : lines) {
			sb.append(line.replaceAll("[\u000B\u000C\u0085\u2028\u2029\n\r]", "")).append('\n');
		}
		cppAnalyzer.featureTable.featureTable.clear();
		try {
			cppAnalyzer.process(Paths.get("temp"),
				new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (final Exception e) {
			Logger.logError("Parsing error: " + e.getMessage());
		}

		final String[] pcs = new String[lines.size() + 1];
		Arrays.fill(pcs, "");

		final HashMap<String, FeatureModule> featureTable = cppAnalyzer.featureTable.featureTable;
		featureTable.values().stream() //
			.flatMap(module -> module.featureOccurrences.stream()) //
			.sorted(new LevelComparator()) //
			.forEach(fo -> {
				final String expr = getNestedFeatureTree(fo).featureExprToString().replace("defined", "")
					.replace(" ", "");
				if (fo.getEndLine() <= 0) {
					Logger.logError("Invalid range of feature occurence (end line <= 0): " + expr);
				} else if (fo.getEndLine() >= pcs.length) {
					Logger.logError("Invalid range of feature occurence: (end line > number of lines)" + expr);
				} else {
					Arrays.fill(pcs, fo.getBeginLine() - 1, fo.getEndLine(), expr);
				}
			});

		return Arrays.asList(pcs);
	}

	private FeatureTree getNestedFeatureTree(FeatureModule.FeatureOccurrence featureOccurrence) {
		final FeatureModule.FeatureOccurrence enclosingFeatureOccurence = featureOccurrence.enclosing;
		if (enclosingFeatureOccurence != null) {
			final FeatureTree featureTree = featureOccurrence.ftree;
			final FeatureTree previousFeatureTree = getNestedFeatureTree(enclosingFeatureOccurence);
			final FeatureTree nestedFeatureTree = new FeatureTree();
			nestedFeatureTree.setKeyword(featureTree.getKeyword());
			nestedFeatureTree
				.setRoot(new FeatureTree.LogAnd(previousFeatureTree.getRoot(), featureTree.getRoot(), "&&"));
			return nestedFeatureTree;
		} else {
			return featureOccurrence.ftree;
		}
	}

	public boolean extract(Path systemPath, Path outputPath) {
		if (!Files.isReadable(systemPath)) {
			Logger.logError(systemPath + " is not readable!");
			return false;
		}
		Configuration.REPORT_ONLY = true;
		this.systemPath = systemPath.toAbsolutePath().normalize();
		this.outputPath = outputPath.toAbsolutePath().normalize();
		try {
			final de.ovgu.spldev.featurecopp.log.Logger logger = new de.ovgu.spldev.featurecopp.log.Logger();
			logger.addInfoStream(new NullStream());
			logger.addFailStream(new NullStream());
			final UserConf config = Configuration.getDefault();
			config.setInputDirectory("");
			config.setMacroPattern(".*");
			final CPPAnalyzer cppAnalyzer = new CPPAnalyzer(logger, config);

			final FileProvider fileProvider = new FileProvider(systemPath);
			fileProvider.setFileNameRegex(FileProvider.CFileRegex);
			excludePaths.forEach(fileProvider::addExclude);

			final long fileCount = fileProvider.getFileStream().count();
			fileCounter = 0;

			fileProvider.getFileStream() //
				.forEach(p -> {
					Logger.logProgress("(" + ++fileCounter + "/" + fileCount + ") " + p.toString());
					parse(cppAnalyzer, p);
				});
			return true;
		} catch (final Exception e) {
			Logger.logError(e);
			return false;
		}
	}

	private void parse(final CPPAnalyzer cppAnalyzer, Path p) {
		for (final Charset charset : charsets) {
			try {
				final List<String> lines = Files.readAllLines(p, charset);
				final List<String> pcs = extractPresenceConditions(cppAnalyzer, lines);

				final Path filePath = p.toAbsolutePath().normalize();
				final Path relativizeFilePath = systemPath.getFileName().resolve(systemPath.relativize(filePath));
				final Path outputDir = outputPath.resolve(relativizeFilePath).getParent();
				final Path outputFile = outputDir.resolve(filePath.getFileName().toString() + ".pc");
				Files.deleteIfExists(outputFile);
				Files.createDirectories(outputDir);
				Files.write(outputFile, Arrays.asList(relativizeFilePath.toString()), StandardOpenOption.CREATE);
				Files.write(outputFile, pcs, StandardOpenOption.APPEND);
			} catch (final MalformedInputException e) {
			} catch (final IOException e) {
				Logger.logError(p.toString());
				Logger.logError(e);
				return;
			}
		}
	}

}
