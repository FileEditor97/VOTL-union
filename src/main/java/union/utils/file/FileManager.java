package union.utils.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import union.App;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.interactions.DiscordLocale;

import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import ch.qos.logback.classic.Logger;

class KeyIsNull extends Exception {
	public KeyIsNull(String str) {
		super(str);
	}
}

public class FileManager {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(FileManager.class);

	private final Configuration CONF = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
	
	private Map<String, File> files;
	private List<DiscordLocale> locales;

	public FileManager() {
	}

	public FileManager addFile(String name, String internal, String external){
		createUpdateLoad(name, internal, external);
		
		return this;
	}
	
	// Convenience method do add new languages more easy.
	public FileManager addLang(@Nonnull String file) throws Exception {
		if (locales == null)
		locales = new ArrayList<>();
		
		DiscordLocale locale = DiscordLocale.from(file);
		if (locale.equals(DiscordLocale.UNKNOWN)) {
			throw new Exception("Unknown language was provided");
		}
		locales.add(locale);

		return addFile(file, "/lang/" + file + ".json", Constants.DATA_PATH + "lang" + Constants.SEPAR + file + ".json");
	}
	
	public Map<String, File> getFiles() {
		return files;
	}
	
	public List<DiscordLocale> getLanguages() {
		return locales;
	}
	
	public void createUpdateLoad(String name, String internal, String external) {
		if (files == null)
			files = new HashMap<>();

		File file = new File(external);
		
		String[] split = external.contains("/") ? external.split(File.separator) : external.split(Pattern.quote(File.separator));

		try {
			if (!file.exists()) {
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
			if (external.contains("lang")) {
				File tempFile = File.createTempFile("locale-", ".json");
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
				tempFile.delete();
			}
			files.put(name, file);
			logger.info("Successfully loaded {}!", name);
		} catch (IOException ex) {
			logger.error("Couldn't locate nor create {}", file.getAbsolutePath(), ex);
		}
	}
	
	/**
	 * @param name - json file to be saerched
	 * @param path - string's json path
	 * @return Returns not-null string. If search returns null string, returns provided path. 
	 */
	@Nonnull
	public String getString(String name, String path) {
		String result = getNullableString(name, path);
		if (result == null)
			return path;
		return result;
	}
	
	/**
	 * @param name - json file to be saerched
	 * @param path - string's json path
	 * @return Returns null-able string. 
	 */
	@Nullable
	public String getNullableString(String name, String path) {
		File file = files.get(name);

		String text = null;
		try {
			if (file == null)
				throw new FileNotFoundException();

			text = JsonPath.using(CONF).parse(file).read("$." + path);

			if (text == null || text.isBlank())
				throw new KeyIsNull(path);
		
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
			text = "TEXT ERROR: file not found";
		} catch (KeyIsNull ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
			text = null;
		} catch (IOException ex) {
			logger.error("Couldn't process file {}.json", name, ex);
			text = "ERROR at processing file";
		}

		return text;
	}
	
	public boolean getBoolean(String name, String path){
		File file = files.get(name);
		
		if(file == null)
			return false;
		
		try {
			Object res = JsonPath.using(CONF).parse(file).read("$." + path);
			
			if (res == null || res.equals(null))
				return false;
				
			return true;
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
		} catch (IOException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
		}
		return false;
	}

	@Nonnull
	public List<String> getStringList(String name, String path){
		File file = files.get(name);
		
		if(file == null)
			return new ArrayList<>();

		List<String> array = new ArrayList<String>();
		try {
			array = JsonPath.using(CONF).parse(file).read("$." + path);	
			
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
		return new ArrayList<>();
	}

	@SuppressWarnings("all")
	@Nonnull
	public Map<String, String> getMap(String name, String path){
		File file = files.get(name);
		if(file == null)
			return Map.of();

		Map<String, String> map = new HashMap<String, String>();
		try {
			map = JsonPath.using(CONF).parse(file).read("$." + path, Map.class);	
			
			if (map == null || map.isEmpty())
				throw new KeyIsNull(path);
				
			return map;
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
		} catch (KeyIsNull ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
		} catch (IOException ex) {
			logger.warn("Couldn't process file {}.json", name, ex);
		}
		return Map.of();
	}

	public boolean export(InputStream inputStream, Path destination){
		boolean success = true;
		try {
			Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | NullPointerException ex){
			logger.info("Exception at copying", ex);
			success = false;
		}

		return success;
	}
}
