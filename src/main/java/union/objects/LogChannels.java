package union.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum LogChannels {
	MODERATION("modLogId", "bot.guild.log.types.moderation"),
	GROUPS("groupLogId", "bot.guild.log.types.group"),
	VERIFICATION("verificationLogId", "bot.guild.log.types.verify"),
	TICKETS("ticketLogId", "bot.guild.log.types.tickets"),
	ROLES("roleLogId", "bot.guild.log.types.roles"),
	SERVER("serverLogId", "bot.guild.log.types.server");

	private final String dbName;
	private final String path;

	private static final List<String> ALL = new ArrayList<>();
	private static final Map<String, LogChannels> BY_NAME = new HashMap<String, LogChannels>();

	static {
		for (LogChannels lc : LogChannels.values()) {
			ALL.add(lc.getDBName());
			BY_NAME.put(lc.getDBName(), lc);
		}
	}

	LogChannels(String dbName, String path) {
		this.dbName = dbName;
		this.path = path;
	}

	public String getDBName() {
		return this.dbName;
	}

	public String getPath() {
		return this.path;
	}

	public static List<String> getAllNames() {
		return ALL;
	}

	public static LogChannels of(String dbName) {
		LogChannels result = BY_NAME.get(dbName);
		if (result == null) {
			throw new IllegalArgumentException("Invalid DB name: " + dbName);
		}
		return result;
	}

}
