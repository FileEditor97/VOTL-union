package union.utils.file;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ResourceLoaderUtil {

	/**
	 * Loads the names of all the files under the given resource URL without directories.
	 *
	 * @param clazz           The clazz instance that should be used to load the resources.
	 * @param directory       The name of the directory that should have its files listed.
	 * @return A list of file names that exists within the given resource URL.
	 * @throws IOException If no files where found using the given URL, or the
	 *                     given URL is not formatted strictly according to
	 *                     RFC2396 and cannot be converted to a URI.
	 */
	@NotNull
	public static List<String> getFiles(@NotNull Class<?> clazz, @NotNull String directory) throws IOException {
		if (!directory.endsWith("/")) {
			directory += "/";
		}

		URL resourceUrl = clazz.getClassLoader().getResource(directory);

		// If we're loading a resource within a normal file path,
		// we'll just continue to load it normally.
		if (resourceUrl != null && resourceUrl.getProtocol().equals("file")) {
			return loadResourcesFromFilePath(resourceUrl);
		}

		if (resourceUrl == null) {
			// Reformats the resource URL to use the class path, and then check if
			// the newly created resource URL actually belongs to a JAR file.
			resourceUrl = clazz.getClassLoader().getResource(
				clazz.getName().replace(".", "/") + ".class"
			);
			if (resourceUrl == null || !resourceUrl.getProtocol().equals("jar")) {
				throw new IOException("No valid resource URL could be generated from the given class instance");
			}
		}

		// Stripes out the "file:" part of the path, up to the bang separator,
		// which will then be the full path to where the JAR file is located.
		String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));

		// Creates a new JAR file instances using the newly created JAR
		// path, so we can loop through all the JAR file entries.
		JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8));

		Set<String> fileNames = new HashSet<>();
		Enumeration<JarEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName();

			// If the file name doesn't start without directory, we'll skip it
			// since it's not in the directory we're looking for files in.
			if (!name.startsWith(directory)) {
				continue;
			}

			// Gets the file entry name.
			String entry = name.substring(directory.length());

			// Checks if the file entry is actually a directory.
			int subDirectoryPos = entry.indexOf("/");
			if (subDirectoryPos >= 0) {
				// Skip the file entry if the user specified not to include subdirectories.
				continue;
			}

			// Skip any entries that are empty.
			if (entry.isEmpty()) {
				continue;
			}

			fileNames.add(entry);
		}
		jar.close();
		return new ArrayList<>(fileNames);
	}

	/**
	 * Loads the name of all the files using the given resource URL, ignoring directories.
	 *
	 * @param resourceUrl The resource URL that should be used to load the files with.
	 * @return A list of file names that exists within the given resource URL.
	 * @throws IOException If no files where found using the given URL, or the
	 *                     given URL is not formatted strictly according
	 *                     to RFC2396 and cannot be converted to a URI.
	 */
	private static List<String> loadResourcesFromFilePath(@NotNull URL resourceUrl) throws IOException {
		File[] files;
		try {
			files = new File(resourceUrl.toURI()).listFiles();
		} catch (URISyntaxException e) {
			throw new IOException("Invalid formatted URI used to look for files", e);
		}
		if (files == null) {
			return Collections.emptyList();
		}

		return Arrays.stream(files)
			.filter(file -> !file.isDirectory())
			.map(File::getName)
			.collect(Collectors.toList());
	}

}
