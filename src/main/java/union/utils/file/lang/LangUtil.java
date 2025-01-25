package union.utils.file.lang;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.Logger;
import com.jayway.jsonpath.JsonPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import union.utils.file.FileManager;

import net.dv8tion.jda.api.interactions.DiscordLocale;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public final class LangUtil {

	private final Logger logger = (Logger) LoggerFactory.getLogger(LangUtil.class);

	private final FileManager fileManager;
	private final Map<String, Object> languages = new HashMap<>();
	
	public LangUtil(FileManager fileManager) {
		for (DiscordLocale locale : fileManager.getLanguages()) {
			try {
				File file = fileManager.getFile(locale.getLocale());
				languages.put(locale.getLocale(), JsonPath.parse(file).json());
			} catch (IOException e) {
				logger.warn(e.getMessage(), e);
			}
		}
		this.fileManager = fileManager;
	}
	
	@NotNull
	public String getString(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> getString(locale.getLocale(), path);
			default -> getString("en-GB", path);
        };
	}

	@Nullable
	public String getNullableString(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> getNullableString(locale.getLocale(), path);
			default -> getNullableString("en-GB", path);
        };
	}

	/**
	 * @param lang - language to be used
	 * @param path - string's json path
	 * @return Returns not-null string. If search returns null string, returns provided path.
	 */
	@NotNull
	public String getString(String lang, String path) {
		String result = getNullableString(lang, path);
		if (result == null) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, lang);
			return "path_error_invalid";
		}
		return result;
	}

	/**
	 * @param lang - language to be used
	 * @param path - string's json path
	 * @return Returns null-able string.
	 */
	@Nullable
	private String getNullableString(String lang, String path) {
		final String text;

		if (languages.containsKey(lang)) {
			text = JsonPath.using(FileManager.CONF)
				.parse(languages.get(lang))
				.read("$." + path);

			if (text == null || text.isBlank()) return null;
			return text;
		} else {
			return fileManager.getNullableString(lang, path);
		}
	}

}
