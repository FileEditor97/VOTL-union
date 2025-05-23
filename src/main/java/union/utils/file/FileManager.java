package union.utils.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.jayway.jsonpath.spi.json.JsonOrgJsonProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import union.App;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.interactions.DiscordLocale;

import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import ch.qos.logback.classic.Logger;

@SuppressWarnings({"LoggingSimilarMessage", "unused", "DuplicateExpressions"})
public class FileManager {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(FileManager.class);

	public static final Configuration CONF = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
	
	private Map<String, File> files;
	private List<DiscordLocale> locales;

	public FileManager() {}

	// name - with what name associate this file
	// internal - path to file inside jar file (/filename)
	// external - path where to store this file
	public FileManager addFile(String name, String internal, String external){
		createUpdateLoad(name, internal, external, false);
		
		return this;
	}

	public FileManager addFileUpdate(String name, String internal, String external){
		createUpdateLoad(name, internal, external, true);

		return this;
	}

	public FileManager addSettings(String name, String pathname){
		createSettings(name, pathname);

		return this;
	}
	
	// Convenience method do add new languages easier.
	public FileManager addLang(@NotNull String file) throws Exception {
		if (locales == null)
			locales = new ArrayList<>();
		
		DiscordLocale locale = DiscordLocale.from(file);
		if (locale.equals(DiscordLocale.UNKNOWN)) {
			throw new Exception("Unknown language was provided");
		}
		locales.add(locale);

		return addFileUpdate(file, "/lang/" + file + ".json", Constants.DATA_PATH + "lang" + Constants.SEPAR + file + ".json");
	}
	
	public Map<String, File> getFiles() {
		return files;
	}
	
	public List<DiscordLocale> getLanguages() {
		return locales;
	}

	private void createUpdateLoad(String name, String internal, String external, boolean update) {
		if (files == null)
			files = new HashMap<>();

		File file = new File(external);
		
		String[] split = external.contains("/") ? external.split(Constants.SEPAR) : external.split(Pattern.quote(Constants.SEPAR));

		try {
			if (!file.exists()) {
				if (App.class.getResource(internal) == null)
					throw new FileNotFoundException("Resource file '"+internal+"' not found.");
				if ((split.length == 2 && !split[0].equals(".")) || (split.length >= 3 && split[0].equals("."))) {
					if (!file.getParentFile().mkdirs() && !file.getParentFile().exists()) {
						logger.error("Failed to create directory {}", split[1]);
					}
				}
				if (file.createNewFile()) {
					if (!export(App.class.getResourceAsStream(internal), Paths.get(external))) {
						logger.error("Failed to write {}!", name);
					} else {
						logger.info("Successfully created {}!", name);
						files.put(name, file);
					}
				}
				return;
			}
			if (update) {
				File tempFile = File.createTempFile("check-", ".tmp");
				if (!export(App.class.getResourceAsStream(internal), tempFile.toPath())) {
					logger.error("Failed to write temp file {}!", tempFile.getName());
				} else {
					if (Files.mismatch(file.toPath(), tempFile.toPath()) != -1) {
						if (export(App.class.getResourceAsStream(internal), Paths.get(external))) {
							logger.info("Successfully updated {}!", name);
							files.put(name, file);
							return;
						} else {
							logger.error("Failed to overwrite {}!", name);
						}
					}
				}
				boolean ignored = tempFile.delete();
			}
			files.put(name, file);
			logger.info("Successfully loaded {}!", name);
		} catch (IOException ex) {
			logger.error("Couldn't locate nor create {}", file.getAbsolutePath(), ex);
		}
	}

	private void createSettings(String name, String pathname) {
		if (files == null)
			files = new HashMap<>();

		File file = new File(pathname);

		String[] split = pathname.contains("/") ? pathname.split(Constants.SEPAR) : pathname.split(Pattern.quote(Constants.SEPAR));

		try {
			if (!file.exists()) {
				if ((split.length == 2 && !split[0].equals(".")) || (split.length >= 3 && split[0].equals("."))) {
					if (!file.getParentFile().mkdirs() && !file.getParentFile().exists()) {
						logger.error("Failed to create directory {}", split[1]);
					}
				}
				if (file.createNewFile()) {
					FileWriter fw = new FileWriter(file);
					fw.write("{\"unionVerify\":true,\"unionPlayer\":true,\"botWhitelist\":[],\"databases\":{},\"servers\":{}}");
					fw.close();
					logger.info("Successfully created {}!", name);
					files.put(name, file);
				}
				return;
			}

			files.put(name, file);
			logger.info("Successfully loaded {}!", name);
		} catch (IOException ex) {
			logger.error("Couldn't locate nor create {}", file.getAbsolutePath(), ex);
		}
	}

	public String updateFile(String name, String internal) {
		File file = getFile(name);
		try {
			File tempFile = File.createTempFile("check-", ".tmp");
			if (!export(App.class.getResourceAsStream(internal), tempFile.toPath())) {
				logger.error("Failed to write temp file {}!", tempFile.getName());
				return "Failed to write temp file.";
			} else {
				if (Files.mismatch(file.toPath(), tempFile.toPath()) != -1) {
					if (export(App.class.getResourceAsStream(internal), file.toPath())) {
						logger.info("Successfully manually updated {}!", name);
						return "File updated!";
					} else {
						logger.error("Failed to overwrite {}!", name);
						return "Failed to overwrite file.";
					}
				}
			}
			boolean ignored = tempFile.delete();
		} catch (IOException ex) {
			logger.error("Couldn't locate nor create {}", file.getAbsolutePath(), ex);
			return ex.getMessage();
		}
		return "File was not updated.";
	}

	/**
	 * @param name - json file to be searched
	 * @return Returns nullable File.
	 */
	@NotNull
	public File getFile(String name) {
		File file = files.get(name);

		if (file == null)
			logger.error("Couldn't find file {}.json", name);

		return file;
	}
	
	/**
	 * @param name - json file to be searched
	 * @param path - string's json path
	 * @return Returns not-null string. If search returns null string, returns provided path. 
	 */
	@NotNull
	public String getString(String name, String path) {
		String result = getNullableString(name, path);
		if (result == null) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
			return "PATH ERROR: invalid";
		}
		return result;
	}
	
	/**
	 * @param name - json file to be searched
	 * @param path - string's json path
	 * @return Returns null-able string. 
	 */
	@Nullable
	public String getNullableString(String name, String path) {
		File file = files.get(name);

		String text;
		try {
			if (file == null)
				throw new FileNotFoundException();

			text = JsonPath.using(CONF).parse(file).read("$." + path);

			if (text != null && text.isBlank()) text = null;
		
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
			text = "FILE ERROR: file not found";
		} catch (IOException ex) {
			logger.error("Couldn't process file {}.json\n{}", name, ex.getMessage());
			text = "FILE ERROR: IO exception";
		}

		return text;
	}

	@NotNull
	public List<String> getStringList(String name, String path){
		File file = files.get(name);
		
		if (file == null) {
			logger.error("Couldn't find file {}.json", name);
			return Collections.emptyList();
		}

		try {
			List<String> array = JsonPath.using(CONF).parse(file).read("$." + path);	
			
			if (array == null || array.isEmpty())
				throw new KeyIsNull(path);
				
			return array;
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
		} catch (KeyIsNull ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
		} catch (IOException ex) {
			logger.warn("Couldn't process file {}.json", name, ex);
		}
		return Collections.emptyList();
	}

	@Nullable
	public Boolean getBoolean(String name, String path){
		File file = files.get(name);

		if (file == null) return null;

		try {
			return JsonPath.using(CONF).parse(file).read("$." + path);
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
		} catch (IOException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
		}
		return null;
	}

	@Nullable
	public Integer getInteger(String name, String path){
		File file = files.get(name);

		if (file == null) return null;

		try {
			return JsonPath.using(CONF).parse(file).read("$." + path);
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
		} catch (IOException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
		}
		return null;
	}

	@NotNull
	public JSONObject getJsonObject(String name){
		File file = files.get(name);

		if (file == null) {
			logger.error("Couldn't find file {}.json", name);
			return null;
		}

		try {
			JSONObject object = JsonPath.using(CONF.jsonProvider(new JsonOrgJsonProvider())).parse(file).json();

			if (object == null || object.isEmpty())
				return null;

			return object;
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
		} catch (IOException ex) {
			logger.warn("Couldn't process file {}.json", name, ex);
		}
		return null;
	}

	public boolean export(InputStream inputStream, Path destination) {
		boolean success = true;
		try {
			Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | NullPointerException ex){
			logger.info("Exception at copying", ex);
			success = false;
		}

		return success;
	}

	private static class KeyIsNull extends Exception {
		public KeyIsNull(String str) {
			super(str);
		}
	}
}
