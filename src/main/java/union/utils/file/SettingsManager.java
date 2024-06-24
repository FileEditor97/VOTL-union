package union.utils.file;

import ch.qos.logback.classic.Logger;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SettingsManager {

	private final Logger logger = (Logger) LoggerFactory.getLogger(SettingsManager.class);
	private final FileManager fileManager;

	private final Configuration CONF = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

	private boolean dbVerifyEnabled, dbPlayerEnabled = true;
	private final Set<Long> botWhitelist = new HashSet<>();

	public SettingsManager(FileManager fileManager) {
		this.fileManager = fileManager;
		File file = fileManager.getFile("settings");
		try {
			DocumentContext context = JsonPath.using(CONF).parse(file);

			dbVerifyEnabled = Optional.ofNullable((boolean) context.read("$.unionVerify")).orElse(true);
			dbPlayerEnabled = Optional.ofNullable((boolean) context.read("$.unionPlayer")).orElse(true);

			List<Long> ids = context.read("$.botWhitelist");
			if (ids != null)
				botWhitelist.addAll(ids);
		} catch (IOException ex) {
			logger.error("Couldn't process settings.json\n{}", ex.getMessage());
		}
	}

	public void setDbVerifyEnabled(boolean enabled) {
		dbVerifyEnabled = enabled;
		writeChange("$.unionVerify", dbVerifyEnabled);
	}

	public void setDbPlayerEnabled(boolean enabled) {
		dbPlayerEnabled = enabled;
		writeChange("$.unionPlayer", dbPlayerEnabled);
	}

	public void addBotWhitelisted(long value) {
		botWhitelist.add(value);
		System.out.println(botWhitelist);
		writeChange("$.botWhitelist", botWhitelist);
	}

	public void removeBotWhitelisted(long value) {
		botWhitelist.remove(value);
		writeChange("$.botWhitelist", botWhitelist);
	}

	public boolean isDbVerifyDisabled() {
		return !dbVerifyEnabled;
	}

	public boolean isDbPlayerDisabled() {
		return !dbPlayerEnabled;
	}

	public boolean isBotWhitelisted(long id) {
		return botWhitelist.contains(id);
	}

	private void writeChange(String name, Object value) {
		File file = fileManager.getFile("settings");
		try {
			DocumentContext context = JsonPath.using(CONF).parse(file);
			context.set(name, value);
			FileWriter writer = new FileWriter(file);
			writer.write(context.jsonString());
			writer.close();
		} catch (IOException ex) {
			logger.error("Couldn't process settings.json\n{}", ex.getMessage());
		}
	}

}
