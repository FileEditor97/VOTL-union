package union.utils.file;

import ch.qos.logback.classic.Logger;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JsonOrgJsonProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import union.utils.CastUtil;
import union.utils.ColorUtil;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class SettingsManager {

	private final Logger log = (Logger) LoggerFactory.getLogger(SettingsManager.class);
	private final FileManager fileManager;

	private final Configuration CONF = Configuration.defaultConfiguration()
		.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

	private boolean dbVerifyEnabled, dbPlayerEnabled = true;
	private final Set<Long> botWhitelist = new HashSet<>();
	private final Map<String, GameServerInfo> databases = new HashMap<>();
	private final Map<Long, List<String>> servers = new HashMap<>();
	private String panicWebhook = null;

	public SettingsManager(FileManager fileManager) {
		this.fileManager = fileManager;
		File file = fileManager.getFile("settings");
		try {
			DocumentContext context = JsonPath.using(CONF).parse(file);

			dbVerifyEnabled = Optional.ofNullable((boolean) context.read("$.unionVerify")).orElse(true);
			dbPlayerEnabled = Optional.ofNullable((boolean) context.read("$.unionPlayer")).orElse(true);

			List<Long> ids = context.read("$.botWhitelist");
			if (ids != null) botWhitelist.addAll(ids);

			Map<String, Map<String, Object>> dbEntries = context.read("$.databases");
			if (dbEntries != null) {
				for (Map.Entry<String, Map<String, Object>> entry : dbEntries.entrySet()) {
					databases.put(entry.getKey(), new GameServerInfo(entry.getValue()));
				}
			}

			Map<String, List<String>> serverEntries = context.read("$.servers");
			if (serverEntries != null) {
				for (Map.Entry<String, List<String>> entry : serverEntries.entrySet()) {
					servers.put(Long.parseLong(entry.getKey()), new ArrayList<>(entry.getValue()));
				}
			}

			String panicWebhook = context.read("$.panicWebhook");
			if (panicWebhook != null && panicWebhook.isBlank()) this.panicWebhook = panicWebhook.trim();
		} catch (IOException ex) {
			log.error("Couldn't read settings.json\n{}", ex.getMessage());
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
		writeChange("$.botWhitelist", botWhitelist);
	}

	public void removeBotWhitelisted(long value) {
		botWhitelist.remove(value);
		writeChange("$.botWhitelist", botWhitelist);
	}

	public void addDatabase(String name, GameServerInfo value) {
		databases.put(name, value);
		writeChange("$.databases", databases);
	}

	public void removeDatabase(String name) {
		databases.remove(name);
		writeChange("$.databases", databases);
	}

	public void addServer(long guildId, List<String> dbs) {
		servers.put(guildId, dbs);
		writeChange("$.servers", servers);
	}

	public void removeServer(long guildId) {
		servers.remove(guildId);
		writeChange("$.servers", servers);
	}

	public void setPanicWebhook(String webhook) {
		panicWebhook = webhook;
		writeChange("$.panicWebhook", panicWebhook==null?"":panicWebhook);
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

	public Set<Long> getBotWhitelist() {
		return Collections.unmodifiableSet(botWhitelist);
	}

	public Map<String, GameServerInfo> getGameServers() {
		return Collections.unmodifiableMap(databases);
	}

	public Map<String, GameServerInfo> getGameServers(long guildId) {
		List<String> dbs = servers.get(guildId);
		if (dbs == null || dbs.isEmpty()) return Map.of();
		LinkedHashMap<String, GameServerInfo> map = new LinkedHashMap<>();
		for (String db : dbs) {
			GameServerInfo info = databases.get(db);
			if (info != null) map.put(db, info);
		}
		return Collections.unmodifiableMap(map);
	}

	public Map<Long, List<String>> getServers() {
		return Collections.unmodifiableMap(servers);
	}

	public boolean isServer(long guildId) {
		return servers.containsKey(guildId);
	}

	public String getPanicWebhook() {
		return panicWebhook;
	}


	private void writeChange(String name, Object value) {
		File file = fileManager.getFile("settings");
		try {
			DocumentContext context = JsonPath.using(CONF.jsonProvider(new JsonOrgJsonProvider())).parse(file);
			context.set(name, value);

			String json = context.jsonString();
			String prettyJson;

			if (json.trim().startsWith("{")) {
				prettyJson = new JSONObject(json).toString(2);
			} else if (json.trim().startsWith("[")) {
				prettyJson = new JSONArray(json).toString(2);
			} else {
				prettyJson = json; // Handle non-standard JSON
			}

			// Write the formatted JSON to the file
			try (FileWriter writer = new FileWriter(file)) {
				writer.write(prettyJson);
			}
		} catch (IOException ex) {
			log.error("Couldn't write settings.json\n{}", ex.getMessage());
		}
	}

	public static class GameServerInfo {
		private final String title;
		private final Integer color;

		public GameServerInfo(Map<String, Object> map) {
			this.title = CastUtil.requireNonNull(map.get("title"));
			this.color = CastUtil.requireNonNull(map.get("color"));
		}

		public GameServerInfo(String title, int color) {
			this.title = title;
			this.color = color;
		}

		public String getTitle() {
			return title;
		}

		public Integer getColor() {
			return color;
		}

		public Color getColor(float alpha) {
			return ColorUtil.decode(color, alpha);
		}
	}

}
