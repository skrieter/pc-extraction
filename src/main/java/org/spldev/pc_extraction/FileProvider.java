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
import java.util.function.*;
import java.util.stream.*;

public class FileProvider {

	public static final String CFileRegex = ".+[.](c|h|cxx|hxx)\\Z";
	public static final String PCFileRegex = ".+[.](pc)\\Z";

	public static final Function<String, Predicate<Path>> fileFilterCreator = regex -> file -> Files.isReadable(file)
			&& Files.isRegularFile(file) && file.getFileName().toString().matches(regex);

	private final List<Path> excludes = new LinkedList<>();
	private String fileNameRegex = null;

	public Path projectroot;

	public FileProvider(Path projectroot) {
		this.projectroot = projectroot;
	}

	public void setProjectroot(Path projectroot) {
		this.projectroot = projectroot;
	}

	public String getFileNameRegex() {
		return fileNameRegex;
	}

	public void setFileNameRegex(String fileNameRegex) {
		this.fileNameRegex = fileNameRegex;
	}

	public void addExclude(Path path) {
		excludes.add(projectroot.resolve(path));
	}

	public Stream<Path> getFiles(Path root, boolean recursive) {
		return getPaths(root, getFilePredicate(), recursive ? Integer.MAX_VALUE : 1);
	}

	public Stream<Path> getFolders(Path root) {
		return getPaths(root, getFolderPredicate(root), Integer.MAX_VALUE);
	}

	public Stream<Path> getFolderStream() {
		return getFolders(projectroot);
	}

	public Stream<Path> getFileStream() {
		return getFolders(projectroot).flatMap(folder -> getFiles(folder, false));
	}

	private Stream<Path> getPaths(Path root, Predicate<Path> filter, int maxDepth) {
		try {
			return Files.walk(root, maxDepth).sequential().filter(filter);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Predicate<Path> getFilePredicate() {
		Predicate<Path> filter = file -> Files.isRegularFile(file);
		if ((fileNameRegex != null) && !fileNameRegex.isEmpty()) {
			filter = filter.and(fileFilterCreator.apply(fileNameRegex));
		}
		return filter;
	}

	private Predicate<Path> getFolderPredicate(Path root) {
		Predicate<Path> filter = file -> Files.isDirectory(file);
		filter = filter.and(file -> {
			final int nameCount = root.getNameCount();
			final int nameCount2 = file.getNameCount();
			if (nameCount < nameCount2) {
				final Path subPath = file.subpath(nameCount, nameCount2);
				for (final Path path : subPath) {
					if (path.toString().startsWith(".")) {
						return false;
					}
				}
				for (final Path excludePath : excludes) {
					if (file.startsWith(excludePath)) {
						return false;
					}
				}
			}
			return true;
		});
		return filter;
	}

}
